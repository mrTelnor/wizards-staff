package ru.telnor.wizardsstaff

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.telnor.wizardsstaff.db.CharacterRecord
import ru.telnor.wizardsstaff.db.CharacterRepository
import ru.telnor.wizardsstaff.db.CatalogFeat
import ru.telnor.wizardsstaff.db.CharacterSheet
import ru.telnor.wizardsstaff.db.FeatCatalog
import ru.telnor.wizardsstaff.db.sameAs
import ru.telnor.wizardsstaff.rules.ArmorCategory
import ru.telnor.wizardsstaff.rules.Rank
import ru.telnor.wizardsstaff.rules.Save
import ru.telnor.wizardsstaff.rules.Skill

/**
 * Листы персонажей: что показывать в разделе «Персонажи».
 *
 * Отдельно от `StaffViewModel` намеренно: здесь нет ни одного обращения к посоху,
 * а там — ни одного к листам. Связь между ними появится в шаге 4 задачи D2, когда
 * выбор действия начнёт взводить посох, и пойдёт она через каркас, а не между моделями.
 */
class CharacterViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = CharacterRepository(application)

    /**
     * Справочник черт из ресурсов. Читается один раз и не меняется, поэтому лежит
     * обычным полем, а не потоком.
     */
    val featCatalog: List<CatalogFeat> = FeatCatalog.load(application)

    /** Кого выбрали чипом в верхней панели. Null — показываем первого. */
    private val chosenId = MutableStateFlow<Long?>(null)

    val characters: StateFlow<List<CharacterSheet>> = repo.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Открытый лист. Если выбранного персонажа больше нет (удалили), показывается первый:
     * пустой экран при непустой базе выглядел бы поломкой.
     */
    val selected: StateFlow<CharacterSheet?> =
        combine(characters, chosenId) { list, id ->
            list.firstOrNull { it.id == id } ?: list.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Сохранения открытого персонажа, свежие сверху. `flatMapLatest` переподписывается
     * на другой запрос, когда чипом выбрали другого персонажа.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val saves: StateFlow<List<CharacterSheet>> = selected
        .flatMapLatest { sheet -> if (sheet == null) flowOf(emptyList()) else repo.savesOf(sheet.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Совпадает ли открытый лист с каким-нибудь своим сохранением. Считается по уже
     * загруженным данным, отдельного запроса к базе не нужно.
     */
    val currentSaved: StateFlow<Boolean> = combine(selected, saves) { sheet, list ->
        sheet != null && list.any { it.sameAs(sheet) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Засев на чистой базе. Редактора листов пока нет, и без него раздел «Персонажи»
        // открылся бы пустым навсегда.
        viewModelScope.launch { repo.seedIfEmpty() }
    }

    fun select(id: Long) {
        chosenId.value = id
    }

    /** Снимает копию открытого листа под этим именем. */
    fun saveCopy(name: String) {
        val sheet = selected.value ?: return
        viewModelScope.launch { repo.saveCopy(sheet, name, System.currentTimeMillis()) }
    }

    /*
     * Правки листа. Номер персонажа берётся у открытого: нажать кнопку можно только
     * на том листе, который сейчас на экране. Если листа нет, нажимать тоже нечего.
     *
     * Считать новое значение здесь нельзя: два быстрых нажатия прочитали бы одно и то же
     * число. Прибавку считает база, см. CharacterDao.
     */
    private fun edit(block: suspend (Long) -> Unit) {
        val id = selected.value?.id ?: return
        viewModelScope.launch { block(id) }
    }

    fun addHeroPoints(delta: Int) = edit { repo.addHeroPoints(it, delta) }

    fun addHp(delta: Int) = edit { repo.addHp(it, delta) }

    fun addTempHp(delta: Int) = edit { repo.addTempHp(it, delta) }

    fun addWounded(delta: Int) = edit { repo.addWounded(it, delta) }

    fun addShieldHp(delta: Int) = edit { repo.addShieldHp(it, delta) }

    fun setShieldRaised(raised: Boolean) = edit { repo.setShieldRaised(it, raised) }

    fun setXp(xp: Int) = edit { repo.setXp(it, xp) }

    fun setDying(dying: Boolean) = edit { repo.setDying(it, dying) }

    /*
     * Правка владений с вкладки «Навыки». Карта меняется здесь, в базу уходит готовая:
     * упаковывать её в SQL нечем, а гонки двух быстрых нажатий тут быть не может —
     * степень владения меняют раз в уровень, а не по три раза за ход.
     */

    private fun withRecord(block: suspend (CharacterRecord) -> Unit) {
        val record = selected.value?.record ?: return
        viewModelScope.launch { block(record) }
    }

    fun setSkillRank(skill: Skill, rank: Rank) =
        withRecord { repo.setSkills(it.id, it.skills + (skill to rank)) }

    fun setSkillItem(skill: Skill, value: Int) =
        withRecord { repo.setSkillItems(it.id, it.skillItems + (skill to value)) }

    fun setArmorRank(category: ArmorCategory, rank: Rank) =
        withRecord { repo.setArmorRanks(it.id, it.armorRanks + (category to rank)) }

    fun setSaveRank(save: Save, rank: Rank) =
        withRecord { repo.setSaves(it.id, it.saves + (save to rank)) }

    fun setSaveItem(save: Save, value: Int) =
        withRecord { repo.setSaveItems(it.id, it.saveItems + (save to value)) }

    fun setPerceptionRank(rank: Rank) =
        withRecord { repo.setPerception(it.id, rank, it.perceptionItem) }

    fun setPerceptionItem(value: Int) =
        withRecord { repo.setPerception(it.id, it.perception, value) }

    fun setClassDc(rank: Rank) = withRecord { repo.setClassDc(it.id, rank) }

    fun setSpellAttack(rank: Rank) = withRecord { repo.setSpellAttack(it.id, rank) }

    fun setSpellDc(rank: Rank) = withRecord { repo.setSpellDc(it.id, rank) }

    /** Степень владения одной строки блока «умения в оружии». Название не меняется. */
    fun setWeaponRank(index: Int, rank: Rank) = withRecord { record ->
        val updated = record.weaponTraining.toMutableList()
        if (index !in updated.indices) return@withRecord
        updated[index] = updated[index].copy(rank = rank)
        repo.setWeaponTraining(record.id, updated)
    }

    /** Уточнение к «Знанию»: первое или второе. Пустая строка стирает уточнение. */
    fun setLore(which: Int, text: String) = withRecord { record ->
        val value = text.ifBlank { null }
        when (which) {
            1 -> repo.setLore(record.id, value, record.lore2)
            2 -> repo.setLore(record.id, record.lore1, value)
        }
    }

    /** «Полностью здоров»: ПЗ до максимума, ранения в ноль, «при смерти» снято. */
    fun heal(keepTempHp: Boolean) = edit { repo.heal(it, keepTempHp) }
}
