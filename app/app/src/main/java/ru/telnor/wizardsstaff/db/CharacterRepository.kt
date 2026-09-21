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
    // Части листа. Каждая новая должна попасть в `saveCopy` и `sameAs` — см. выше.
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

/*
 * ВАЖНО ПРИ ДОБАВЛЕНИИ ДАННЫХ В ЛИСТ.
 *
 * Сохранение персонажа — копия строки со всеми её частями (`CharacterSaves.kt`,
 * `CharacterRepository.saveCopy`). Из этого следуют два правила:
 *
 * 1. Новый СТОЛБЕЦ у `characters` или у части попадёт в сохранение сам: копируется
 *    вся строка целиком. Ничего дописывать не нужно.
 * 2. Новая ТАБЛИЦА-часть листа (пятый список рядом с оружием, чертами, предметами
 *    и заклинаниями) сама никуда не попадёт. Её надо вписать в три места:
 *    `CharacterSheet`, `CharacterRepository.saveCopy` и `CharacterSheet.sameAs`.
 *    Иначе сохранение потеряет её молча, а сверка «состояние сохранено» будет врать.
 *
 * То же касается новых вкладок листа: если вкладка хранит свои данные, они обязаны
 * ехать в сохранение. Тест `CharacterSaveTest` считает части и падает, когда их
 * становится больше, — но только если не забыть поправить и его.
 */

/** Между базой и экраном персонажей. */
class CharacterRepository(context: Context) {

    private val db = StaffDatabase.open(context)
    private val dao = db.characters()

    /** Все листы. Обновляется сам при любой правке: за этим в C2 и брали Room. */
    fun all(): Flow<List<CharacterSheet>> = dao.all().map { list -> list.map { it.toSheet() } }

    /** Один лист. Null, если персонажа удалили. */
    fun byId(id: Long): Flow<CharacterSheet?> = dao.byId(id).map { it?.toSheet() }

    /** Сохранения персонажа, свежие сверху. */
    fun savesOf(id: Long): Flow<List<CharacterSheet>> =
        dao.savesOf(id).map { list -> list.map { it.toSheet() } }

    /**
     * Снимает копию листа под данным именем. Части копируются вместе с ним и получают
     * номер новой строки — иначе они остались бы привязаны к живому персонажу и уехали
     * бы вместе с его правками.
     *
     * **Добавил новую часть листа — впиши её сюда.** Здесь перечислены все таблицы,
     * из которых состоит персонаж, и забытая просто не сохранится.
     */
    suspend fun saveCopy(sheet: CharacterSheet, name: String, at: Long): Long =
        db.withTransaction {
            val id = dao.add(
                sheet.record.copy(id = 0, saveName = name, savedAt = at, saveOf = sheet.id),
            )
            dao.addWeapons(sheet.weapons.map { it.copy(id = 0, characterId = id) })
            dao.addFeats(sheet.feats.map { it.copy(id = 0, characterId = id) })
            dao.addItems(sheet.items.map { it.copy(id = 0, characterId = id) })
            dao.addSpells(sheet.spells.map { it.copy(id = 0, characterId = id) })
            id
        }

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
 * Совпадают ли два листа по содержимому. Номера строк и подпись сохранения
 * не в счёт: у копии они свои по определению.
 *
 * **Добавил новую часть листа — впиши её и сюда.** Забытая часть не сломает ничего
 * заметного, но сверка начнёт врать: изменение в ней не будет считаться изменением,
 * и человека не предупредят, что состояние не сохранено.
 *
 * Сравнивается всё — и паспорт, и ПЗ с опытом, и поднятый щит, и части. По этому
 * сравнению лист считается сохранённым, и человека не спрашивают лишний раз.
 */
fun CharacterSheet.sameAs(other: CharacterSheet): Boolean =
    record.forCompare() == other.record.forCompare() &&
        weapons.map { it.forCompare() } == other.weapons.map { it.forCompare() } &&
        feats.map { it.forCompare() } == other.feats.map { it.forCompare() } &&
        items.map { it.forCompare() } == other.items.map { it.forCompare() } &&
        spells.map { it.forCompare() } == other.spells.map { it.forCompare() }

private fun CharacterRecord.forCompare() =
    copy(id = 0, saveName = null, savedAt = null, saveOf = null)

private fun CharacterWeaponRecord.forCompare() = copy(id = 0, characterId = 0)

private fun CharacterFeatRecord.forCompare() = copy(id = 0, characterId = 0)

private fun CharacterItemRecord.forCompare() = copy(id = 0, characterId = 0)

private fun CharacterSpellRecord.forCompare() = copy(id = 0, characterId = 0)

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
