package ru.telnor.wizardsstaff.rules

/*
 * Броня из основной книги, редакция Legacy. Значения взяты с pf2.ru, по одной странице
 * на предмет: pf2.ru/armor/<английское имя>?legacy=true.
 *
 * Зачем встроенный список. Редактор листа должен давать выбрать броню, а не вводить
 * шесть чисел руками: опечатка в пределе ловкости или требовании силы молча испортит
 * КБ, скорость и половину навыков, и заметить это можно будет только за столом.
 *
 * Это НЕ справочник PF2e. Здесь только те данные, без которых не считается вывод.
 * Черты, заклинания и прочее содержимое книг в приложение не тащим.
 *
 * Требование силы записано значением, а не модификатором — так в Legacy. В Remaster
 * его переписали на модификатор, и если смешать редакции, персонаж с силой 14 никогда
 * не дотянется до требования 14 и навечно останется со штрафами.
 */

/** Броня из книги: то же, что Armor, плюс справочные поля для показа в редакторе. */
data class CatalogArmor(
    val armor: Armor,
    /** Группа брони: тканевая, кожаная, кольчужная, композитная, латная, деревянная. */
    val group: String,
    /** Свойства: гибкая, шумная, закрытая. Пустая строка, если их нет. */
    val traits: String = "",
    /** Вес книжной записью: «Л» это лёгкий, остальное числом. Нагрузку пока не считаем. */
    val bulk: String = "—",
)

/**
 * Одежда исследователя. Брони не даёт вовсе, но предел ловкости у неё есть, и владение
 * идёт по «без брони» — поэтому она лежит здесь, а не считается отсутствием брони.
 */
val EXPLORERS_CLOTHING = CatalogArmor(
    armor = Armor("Одежда исследователя", ArmorCategory.UNARMORED, 0, 5, 0, 0, 0),
    group = "Тканевая", traits = "удобная", bulk = "Л",
)

val PADDED_ARMOR = CatalogArmor(
    armor = Armor("Стёганый доспех", ArmorCategory.LIGHT, 1, 3, 0, 0, 10),
    group = "Тканевая", bulk = "Л",
)

val LEATHER_ARMOR = CatalogArmor(
    armor = Armor("Кожаный доспех", ArmorCategory.LIGHT, 1, 4, -1, 0, 10),
    group = "Кожаная", bulk = "1",
)

val STUDDED_LEATHER = CatalogArmor(
    armor = Armor("Клёпаный кожаный доспех", ArmorCategory.LIGHT, 2, 3, -1, 0, 12),
    group = "Кожаная", bulk = "1",
)

val CHAIN_SHIRT = CatalogArmor(
    armor = Armor("Кольчужная рубаха", ArmorCategory.LIGHT, 2, 3, -1, 0, 12),
    group = "Кольчужная", traits = "гибкая, шумная", bulk = "1",
)

val HIDE_ARMOR = CatalogArmor(
    armor = Armor("Сыромятный доспех", ArmorCategory.MEDIUM, 3, 2, -2, -5, 14),
    group = "Кожаная", bulk = "2",
)

val SCALE_MAIL = CatalogArmor(
    armor = Armor("Чешуйчатый доспех", ArmorCategory.MEDIUM, 3, 2, -2, -5, 14),
    group = "Композитная", bulk = "2",
)

/** Доспех Сильврина: требование силы 14, и у него ровно столько. */
val WOODEN_BREASTPLATE_ITEM = CatalogArmor(
    armor = Armor("Деревянный нагрудник", ArmorCategory.MEDIUM, 3, 2, -2, -5, 14),
    group = "Деревянная", bulk = "2",
)

val CHAIN_MAIL = CatalogArmor(
    armor = Armor("Кольчуга", ArmorCategory.MEDIUM, 4, 1, -2, -5, 16),
    group = "Кольчужная", traits = "гибкая, шумная", bulk = "2",
)

val BREASTPLATE = CatalogArmor(
    armor = Armor("Нагрудник", ArmorCategory.MEDIUM, 4, 1, -2, -5, 16),
    group = "Латная", bulk = "2",
)

val SPLINT_MAIL = CatalogArmor(
    armor = Armor("Пластинчатый доспех", ArmorCategory.HEAVY, 5, 1, -3, -10, 16),
    group = "Композитная", bulk = "3",
)

val HALF_PLATE = CatalogArmor(
    armor = Armor("Кольчужно-латный доспех", ArmorCategory.HEAVY, 5, 1, -3, -10, 16),
    group = "Латная", bulk = "3",
)

val FULL_PLATE = CatalogArmor(
    armor = Armor("Полный латный доспех", ArmorCategory.HEAVY, 6, 0, -3, -10, 18),
    group = "Латная", traits = "закрытая", bulk = "4",
)

/** Весь список для выбора в редакторе, по возрастанию защиты внутри категорий. */
val ARMOR_CATALOG: List<CatalogArmor> = listOf(
    EXPLORERS_CLOTHING,
    PADDED_ARMOR,
    LEATHER_ARMOR,
    STUDDED_LEATHER,
    CHAIN_SHIRT,
    HIDE_ARMOR,
    SCALE_MAIL,
    WOODEN_BREASTPLATE_ITEM,
    CHAIN_MAIL,
    BREASTPLATE,
    SPLINT_MAIL,
    HALF_PLATE,
    FULL_PLATE,
)

/** Броня по имени: так лист хранит выбор, а не копию шести чисел. */
fun armorByName(name: String): CatalogArmor? = ARMOR_CATALOG.firstOrNull { it.armor.name == name }
