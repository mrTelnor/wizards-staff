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
import ru.telnor.wizardsstaff.db.CharacterRepository
import ru.telnor.wizardsstaff.db.CharacterSheet
import ru.telnor.wizardsstaff.db.sameAs

/**
 * Листы персонажей: что показывать в разделе «Персонажи».
 *
 * Отдельно от `StaffViewModel` намеренно: здесь нет ни одного обращения к посоху,
 * а там — ни одного к листам. Связь между ними появится в шаге 4 задачи D2, когда
 * выбор действия начнёт взводить посох, и пойдёт она через каркас, а не между моделями.
 */
class CharacterViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = CharacterRepository(application)

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

    /** «Полностью здоров»: ПЗ до максимума, ранения в ноль, «при смерти» снято. */
    fun heal(keepTempHp: Boolean) = edit { repo.heal(it, keepTempHp) }
}
