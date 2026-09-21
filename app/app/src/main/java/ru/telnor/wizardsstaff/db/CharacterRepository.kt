package ru.telnor.wizardsstaff.db

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.telnor.wizardsstaff.rules.HERO_POINTS_MAX
import ru.telnor.wizardsstaff.rules.Sheet
import ru.telnor.wizardsstaff.rules.WOUNDED_MAX

/**
 * Лист персонажа в том виде, в каком его показывает экран: паспорт, входные данные
 * для правил и части листа по порядку.
 *
 * `sheet` — это те же данные, но для движка: `Pf2.kt` по ним считает КБ, испытания,
 * навыки и атаки. Ни одного из этих чисел в базе нет.
 */
data class CharacterSheet(
    val record: CharacterRecord,
    val weapons: List<CharacterWeaponRecord>,
    val feats: List<CharacterFeatRecord>,
    val items: List<CharacterItemRecord>,
    val spells: List<CharacterSpellRecord>,
) {
    val id: Long get() = record.id
    val name: String get() = record.name
    val sheet: Sheet get() = record.toSheet()

    /** Черты одной колонки вкладки «Черты и снаряжение». */
    fun feats(group: FeatGroup): List<CharacterFeatRecord> = feats.filter { it.featGroup == group }
}

/** Между базой и экраном персонажей. */
class CharacterRepository(context: Context) {

    private val db = StaffDatabase.open(context)
    private val dao = db.characters()

    /** Все листы. Обновляется сам при любой правке: за этим в C2 и брали Room. */
    fun all(): Flow<List<CharacterSheet>> = dao.all().map { list -> list.map { it.toSheet() } }

    /** Один лист. Null, если персонажа удалили. */
    fun byId(id: Long): Flow<CharacterSheet?> = dao.byId(id).map { it?.toSheet() }

    // Правки прямо из листа. Пределы правил живут в `rules/Pf2.kt`, а держит их база:
    // считать «не больше трёх» на стороне экрана — значит повторить это в каждом месте,
    // откуда придёт нажатие.

    suspend fun addHeroPoints(id: Long, delta: Int) = dao.addHeroPoints(id, delta, HERO_POINTS_MAX)

    suspend fun addHp(id: Long, delta: Int) = dao.addHp(id, delta)

    suspend fun addTempHp(id: Long, delta: Int) = dao.addTempHp(id, delta)

    suspend fun addWounded(id: Long, delta: Int) = dao.addWounded(id, delta, WOUNDED_MAX)

    suspend fun addShieldHp(id: Long, delta: Int) = dao.addShieldHp(id, delta)

    suspend fun setShieldRaised(id: Long, raised: Boolean) = dao.setShieldRaised(id, raised)

    suspend fun setXp(id: Long, xp: Int) = dao.setXp(id, xp)

    suspend fun setDying(id: Long, dying: Boolean) = dao.setDying(id, dying)

    suspend fun heal(id: Long, keepTempHp: Boolean) = dao.heal(id, keepTempHp)

    /**
     * Засев при первом запуске: если персонажей нет ни одного, в базу кладётся Сильврин.
     * Возвращает true, если засев состоялся.
     *
     * Проверка «пусто ли» нарочно по числу персонажей, а не по флагу «засевали уже»:
     * человек, стеревший всех персонажей, получит Сильврина обратно, а не пустой экран
     * без единой кнопки — редактора листов пока нет, и создать персонажа руками нельзя.
     */
    suspend fun seedIfEmpty(): Boolean = db.withTransaction {
        if (dao.count() > 0) return@withTransaction false
        write(silvrinSeed())
        true
    }

    /**
     * Пишет персонажа вместе с частями. Номер персонажу выдаёт база, и только после
     * этого части узнают, к кому они относятся.
     *
     * Всё одной транзакцией: оборвись запись посередине — в базе остался бы персонаж
     * без оружия и черт, и «пусто ли» ответило бы «не пусто», так что починиться само
     * это уже не смогло бы.
     */
    suspend fun write(seed: CharacterSeed): Long = db.withTransaction {
        val id = dao.add(seed.character)
        dao.addWeapons(seed.weapons.map { it.copy(characterId = id) })
        dao.addFeats(seed.feats.map { it.copy(characterId = id) })
        dao.addItems(seed.items.map { it.copy(characterId = id) })
        dao.addSpells(seed.spells.map { it.copy(characterId = id) })
        id
    }
}

/**
 * Раскладывает части листа по порядку. Room отдаёт их в том порядке, в каком их вернула
 * база, а он ничем не закреплён: `@Relation` не умеет ORDER BY.
 */
private fun FullCharacter.toSheet(): CharacterSheet = CharacterSheet(
    record = character,
    weapons = weapons.sortedBy { it.position },
    feats = feats.sortedBy { it.position },
    items = items.sortedBy { it.position },
    spells = spells.sortedBy { it.position },
)
