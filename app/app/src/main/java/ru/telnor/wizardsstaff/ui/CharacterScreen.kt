package ru.telnor.wizardsstaff.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.telnor.wizardsstaff.db.CharacterSheet
import ru.telnor.wizardsstaff.ui.theme.PosohDimens

/*
 * Раздел «Персонажи»: вкладки и содержимое листа.
 *
 * Списка персонажей сбоку нет — переключатель это чип в верхней панели каркаса.
 * Решение из SCREENS.md, раздел 3: на одного-двух персонажей колонка-список съедала бы
 * треть экрана впустую.
 */

/** Вкладки листа. Порядок как в макете. */
enum class CharacterTab(val title: String) {
    Overview("Обзор"),
    Skills("Навыки"),
    Spells("Магия"),
    Feats("Черты и снаряжение"),
}

@Composable
fun CharacterScreen(character: CharacterSheet?, modifier: Modifier = Modifier) {
    // rememberSaveable: поворот планшета пересоздаёт активность, и обычный remember
    // вернул бы человека на «Обзор» с той вкладки, которую он читал.
    var tab by rememberSaveable { mutableStateOf(CharacterTab.Overview) }

    Column(modifier.fillMaxSize()) {
        TabBar(selected = tab, onSelect = { tab = it })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        when {
            character == null -> EmptyCharacters()
            tab == CharacterTab.Overview -> CharacterOverview(character, Modifier.fillMaxSize())
            else -> TabComing(tab)
        }
    }
}

/**
 * Полоса вкладок. Сделана руками, а не `TabRow`: тот растягивает вкладки на всю ширину
 * поровну, а в макете они стоят слева и каждая по своему тексту.
 */
@Composable
private fun TabBar(selected: CharacterTab, onSelect: (CharacterTab) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .padding(horizontal = PosohDimens.screenPadding),
    ) {
        CharacterTab.entries.forEach { item ->
            val active = item == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .height(48.dp)
                    .clickable { onSelect(item) },
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 0.dp),
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            color = if (active) scheme.primary else scheme.surface,
                            shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                        ),
                )
            }
        }
    }
}

/** Вкладка, которой ещё нет. Честно говорит, что будет, а не показывает пустоту. */
@Composable
private fun TabComing(tab: CharacterTab) {
    val text = when (tab) {
        CharacterTab.Skills -> "Обученные и нетренированные навыки со степенями владения " +
            "и бонусами, по которым их бросают."

        CharacterTab.Spells -> "Атака заклинанием, СЛ, традиция, заговоры и ячейки по кругам."
        CharacterTab.Feats -> "Черты народа, класса и навыков, снаряжение, кошель и языки."
        CharacterTab.Overview -> ""
    }
    Message(title = "Вкладка «${tab.title}» ещё не сделана", text = text)
}

/** Когда персонажей нет вовсе. При работающем засеве человек этого не увидит. */
@Composable
private fun EmptyCharacters() {
    Message(
        title = "Листов персонажей нет",
        text = "Приложение кладёт в базу Сильврина при первом запуске. Если экран пуст, " +
            "значит засев не сработал — загляни в логи посоха.",
    )
}

@Composable
private fun Message(title: String, text: String) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().padding(PosohDimens.screenPadding), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(560.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(PosohDimens.spaceM))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
