package ru.telnor.wizardsstaff

import android.content.Context

/**
 * Где планшет держит PIN от посохов.
 *
 * Ключ — адрес BLE: приложение знает его ещё до подключения, из результатов поиска,
 * поэтому нужный PIN подставляется сразу, без вопросов человеку. Посохов теоретически
 * может быть несколько, и у каждого свой PIN.
 *
 * Хранилище обычное, не шифрованное. Модель угроз у нас — шутник за игровым столом,
 * который берёт чужой посох и подкидывает себе броски; от него защищает сам PIN.
 * От того, у кого в руках разблокированный планшет с правами root, не спасёт ничего,
 * что мы можем сделать за разумное время, а `EncryptedSharedPreferences` притащил бы
 * зависимость ради ощущения безопасности, а не ради неё самой. Файл всё равно лежит
 * в личной папке приложения и другим программам не виден.
 */
class PinStore(context: Context) {

    private val prefs = context.getSharedPreferences("staff-pins", Context.MODE_PRIVATE)

    /** PIN этого посоха или null, если планшет его ещё не знает. */
    fun get(address: String): String? = prefs.getString(key(address), null)

    /** Запоминает PIN. Вызывать только после того, как посох подтвердил вход. */
    fun save(address: String, pin: String) {
        prefs.edit().putString(key(address), pin).apply()
    }

    /** Забывает PIN: посох его не принял, значит хранить незачем. */
    fun forget(address: String) {
        prefs.edit().remove(key(address)).apply()
    }

    // Адрес в нижнем регистре: Android отдаёт его в верхнем при сканировании
    // и в нижнем в некоторых ответах, а ключ должен совпадать в обоих случаях.
    private fun key(address: String) = "pin-" + address.lowercase()
}
