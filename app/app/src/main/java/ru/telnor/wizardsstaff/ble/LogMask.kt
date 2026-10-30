package ru.telnor.wizardsstaff.ble

/*
 * Как PIN прячется в журнале обмена.
 *
 * Свои команды входа и смены PIN приложение пишет в журнал готовой строкой со звёздочками
 * (StaffCommand.authForLog, changePinForLog), здесь они не нужны. Эта функция — для всего
 * остального, прежде всего для строк, набранных руками на экране логов.
 *
 * Строгим разбором JSON тут не обойтись: посох читает команды ArduinoJson, а тот прощает
 * многое — одинарные кавычки, ключи без кавычек, \uXXXX в ключах и значениях. Всё это
 * пускает на посох по настоящему PIN, и журнал должен прятать его так же. Поэтому строка
 * разбирается так же снисходительно: на строки в кавычках любого вида, голые слова и
 * знаки {}[]:, — и прячется с запасом: лучше лишние звёздочки, чем открытый PIN.
 *
 * Без Android и без org.json: так её проверяет обычный JVM-тест.
 */

/** Чем заменяется PIN в журнале. Всегда четыре звёздочки, чтобы не выдавать длину. */
const val MASKED_SECRET = "****"

// Ключи, значение которых может быть PIN: pin у auth, old и new у pin, auth на всякий случай.
private val SECRET_KEYS = setOf("pin", "old", "new", "auth")

// Значения этих ключей не прячем: имя команды и вид события не секрет, а без них
// журнал перестанет понимать, что за строка перед ним.
private val OPEN_KEYS = setOf("cmd", "ev")

// Знаки, которые в JSON отделяют одно от другого.
private const val PUNCTUATION = "{}[]:,"

/** Кусок строки: строка в кавычках, голое слово или число, знак. */
private class Token(
    val start: Int,
    val end: Int,
    /** Кавычка, если это строка: '"' или '\''. Для слов и знаков — null. */
    val quote: Char?,
    val isPunctuation: Boolean,
    /** Текст без кавычек, с раскрытыми \uXXXX, в нижнем регистре. */
    val text: String,
)

// Отдельное слово pin, old, new или auth: не часть другого слова. Для запасного ремня.
private val SECRET_WORD = Regex("""(?<![\p{L}\p{N}_])(pin|old|new|auth)(?![\p{L}\p{N}_])""")

/**
 * Прячет PIN в строке перед записью в журнал. Посоху уходит настоящая строка, а в журнал
 * на экране, в файл экспорта и в logcat — эта.
 *
 * Срабатывает, если в строке есть ключ pin, old, new или auth либо ключ cmd со словом auth
 * или pin в значении. Тогда звёздочками заменяются ВСЕ значения, кроме cmd и ev.
 * Остальные строки, в том числе ответы посоха, возвращаются как есть.
 *
 * Строка проверяется дважды. Сперва она режется на команды так же, как это делает посох
 * (ble.h: конец команды — перевод строки или закрывшаяся фигурная скобка, кавычки посох
 * при этом не смотрит), и каждая команда прячется отдельно. Иначе строка вида
 * `'}{"cmd":"auth","pin":"…"}` выглядела бы для журнала одной строкой в кавычках, а посох
 * увидел бы в ней две команды и впустил по второй. Потом то же для строки целиком.
 */
fun maskSecrets(line: String): String = maskWhole(maskByStaffCommands(line))

/**
 * То же для строк, которые уходят посоху, плюс запасной ремень: если точная маскировка
 * ничего не нашла, а в строке есть отдельное слово pin, old, new или auth (после раскрытия
 * \uXXXX, в любом регистре), все цифры заменяются на «*». Строку это портит, зато PIN,
 * спрятанный так, как разбор не предусмотрел, в журнал не попадёт.
 *
 * Для ответов посоха ремень не годится: в его сообщениях слово PIN встречается в обычном
 * тексте («неверный PIN, промах 1 подряд»), и цифры в них пропали бы зря.
 */
fun maskOutgoing(line: String): String {
    val masked = maskSecrets(line)
    if (masked != line) return masked
    if (!SECRET_WORD.containsMatchIn(unescape(line))) return line
    return line.map { if (it.isDigit()) '*' else it }.joinToString("")
}

/** Режет строку на команды по правилам посоха и прячет PIN в каждой. Разделители не трогает. */
private fun maskByStaffCommands(line: String): String {
    val out = StringBuilder()
    var start = 0
    var depth = 0
    for (i in line.indices) {
        when (line[i]) {
            '\n', '\r' -> {
                out.append(maskWhole(line.substring(start, i))).append(line[i])
                start = i + 1
                depth = 0
            }
            '{' -> depth++
            '}' -> if (--depth <= 0) {
                out.append(maskWhole(line.substring(start, i + 1)))
                start = i + 1
                depth = 0
            }
        }
    }
    out.append(maskWhole(line.substring(start)))
    return out.toString()
}

/** Прячет PIN в одном куске, разобранном как одна команда. */
private fun maskWhole(line: String): String {
    val tokens = tokenize(line)

    // Ключ — это слово или строка, за которыми идёт двоеточие, и стоят они в начале
    // или после { или запятой. Без второго условия в `"pin":"1234":1` значение 1234
    // сочлось бы ключом и осталось открытым.
    fun isKey(i: Int): Boolean {
        if (tokens[i].isPunctuation) return false
        val next = tokens.getOrNull(i + 1)
        if (next == null || !next.isPunctuation || next.text != ":") return false
        val prev = tokens.getOrNull(i - 1) ?: return true
        return prev.isPunctuation && (prev.text == "{" || prev.text == ",")
    }

    // Чьё это значение: ключ стоит двумя кусками раньше, через двоеточие.
    fun keyOf(i: Int): String? =
        if (i >= 2 && tokens[i - 1].isPunctuation && tokens[i - 1].text == ":" && isKey(i - 2)) {
            tokens[i - 2].text
        } else {
            null
        }

    val secret = tokens.indices.any { i ->
        (isKey(i) && tokens[i].text in SECRET_KEYS) ||
            (!tokens[i].isPunctuation && !isKey(i) && keyOf(i) == "cmd" &&
                ("auth" in tokens[i].text || "pin" in tokens[i].text))
    }
    if (!secret) return line

    val out = StringBuilder()
    var copied = 0
    for (i in tokens.indices) {
        val token = tokens[i]
        if (token.isPunctuation || isKey(i) || keyOf(i)?.let { it in OPEN_KEYS } == true) continue
        out.append(line, copied, token.start)
        // Одинарные кавычки оставляем одинарными, строки в двойных и голые слова — в двойных.
        val quote = if (token.quote == '\'') '\'' else '"'
        out.append(quote).append(MASKED_SECRET).append(quote)
        copied = token.end
    }
    out.append(line, copied, line.length)
    return out.toString()
}

/** Режет строку на куски. Незакрытая кавычка тянет строку до конца: так тоже не утечёт. */
private fun tokenize(line: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c.isWhitespace() -> i++

            c in PUNCTUATION -> {
                tokens += Token(i, i + 1, null, true, c.toString())
                i++
            }

            c == '"' || c == '\'' -> {
                var j = i + 1
                while (j < line.length && line[j] != c) j += if (line[j] == '\\') 2 else 1
                val contentEnd = minOf(j, line.length)
                val end = minOf(j + 1, line.length)
                tokens += Token(i, end, c, false, unescape(line.substring(i + 1, contentEnd)))
                i = end
            }

            else -> {
                var j = i
                while (j < line.length && !line[j].isWhitespace() &&
                    line[j] !in PUNCTUATION && line[j] != '"' && line[j] != '\''
                ) {
                    j += if (line[j] == '\\') 2 else 1
                }
                val end = minOf(j, line.length)
                tokens += Token(i, end, null, false, unescape(line.substring(i, end)))
                i = end
            }
        }
    }
    return tokens
}

/** Раскрывает \uXXXX и прочие \x так, как их поймёт посох, и приводит к нижнему регистру. */
private fun unescape(raw: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c == '\\' && i + 1 < raw.length) {
            val hex = raw.substring(i + 2, minOf(i + 6, raw.length))
            if (raw[i + 1] == 'u' && hex.length == 4 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                out.append(hex.toInt(16).toChar())
                i += 6
            } else {
                out.append(raw[i + 1])
                i += 2
            }
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString().lowercase()
}
