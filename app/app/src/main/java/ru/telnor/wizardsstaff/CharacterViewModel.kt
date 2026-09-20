package ru.telnor.wizardsstaff

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.telnor.wizardsstaff.db.CharacterRepository
import ru.telnor.wizardsstaff.db.CharacterSheet

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

    init {
        // Засев на чистой базе. Редактора листов пока нет, и без него раздел «Персонажи»
        // открылся бы пустым навсегда.
        viewModelScope.launch { repo.seedIfEmpty() }
    }

    fun select(id: Long) {
        chosenId.value = id
    }
}
