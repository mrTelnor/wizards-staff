package ru.telnor.wizardsstaff.db

import android.content.Context
import org.json.JSONArray

/*
 * Справочник черт: описания из `assets/feats.json`, собранные скриптом
 * `tools/fetch_feats.py` со страниц pf2.ru.
 *
 * Лежит рядом с базой, хотя базой не является: это тоже «откуда берутся данные»,
 * только данные не персонажа, а книги. В `rules/` ему нельзя — там всё без единой
 * зависимости от Android, а здесь нужен доступ к ресурсам приложения.
 *
 * Справочник неполон и будет неполон всегда: черт в Legacy больше тысячи, а собраны
 * пока общие, классовые жреца и народные котолюдов. Поэтому ни одна часть листа
 * от него не зависит — он только добавляет описание там, где оно нашлось.
 */

/** Черта из книги: то, что показывается в окне по нажатию. */
data class CatalogFeat(
    val english: String,
    val russian: String,
    /** Уровень черты. Null, если на странице его не было. */
    val level: Int?,
    val traits: List<String>,
    /** Источник, предварительные требования, триггер — всё, что стоит до текста. */
    val header: String,
    val text: String,
    val url: String,
)

/**
 * Находит черту по русскому названию. Регистр не в счёт: в листе автор пишет
 * «Лечащие руки», на сайте — «Лечащие Руки».
 *
 * Названия могут и разойтись совсем, и тогда описание просто не покажется. Это
 * задумано: приложение не должно ни падать, ни подставлять чужое описание.
 * Надёжнее было бы держать у черты персонажа английское имя — когда справочник
 * дорастёт до тысячи, до этого дойдёт.
 */
fun List<CatalogFeat>.findByRussian(name: String): CatalogFeat? {
    val wanted = name.trim().lowercase()
    return firstOrNull { it.russian.trim().lowercase() == wanted }
}

/** Читает справочник из ресурсов приложения. Файла нет — справочник пуст. */
object FeatCatalog {

    private const val ASSET = "feats.json"

    @Volatile
    private var cache: List<CatalogFeat>? = null

    /**
     * Справочник целиком. Читается один раз: файл небольшой, а открывать его
     * при каждом нажатии на черту незачем.
     */
    fun load(context: Context): List<CatalogFeat> =
        cache ?: synchronized(this) { cache ?: read(context).also { cache = it } }

    private fun read(context: Context): List<CatalogFeat> = runCatching {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val traits = item.optJSONArray("traits")
            CatalogFeat(
                english = item.optString("en"),
                russian = item.optString("ru"),
                level = if (item.isNull("level")) null else item.optInt("level"),
                traits = (0 until (traits?.length() ?: 0)).map { traits!!.getString(it) },
                header = item.optString("header"),
                text = item.optString("text"),
                url = item.optString("url"),
            )
        }
        // Справочник — украшение, а не основа: сломанный файл не должен мешать
        // открыть лист персонажа.
    }.getOrElse { emptyList() }
}
