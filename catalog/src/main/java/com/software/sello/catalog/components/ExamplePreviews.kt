package com.software.sello.catalog.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.software.sello.designsystem.component.CelebrationOverlay
import com.software.sello.designsystem.component.MilestoneState
import com.software.sello.designsystem.component.NoteComposer
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme

class ExamplePreviewTheme(val ink: SelloInk, val dark: Boolean)
class ExamplePreviewThemes : PreviewParameterProvider<ExamplePreviewTheme> {
    override val values = SelloInk.entries.asSequence().flatMap { ink ->
        sequenceOf(ExamplePreviewTheme(ink, false), ExamplePreviewTheme(ink, true))
    }
}

@Preview(name = "Compact", widthDp = 360, heightDp = 780)
@Preview(name = "Narrow, large text", widthDp = 320, heightDp = 780, fontScale = 2f)
@Preview(name = "Expanded", widthDp = 840, heightDp = 780)
annotation class ExampleThemePreviews

@Composable
private fun Frame(theme: ExamplePreviewTheme, content: @Composable () -> Unit) {
    SelloTheme(ink = theme.ink, darkTheme = theme.dark, reducedMotion = true) {
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@ExampleThemePreviews
@Composable
private fun EntryExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { EntryExample() }

@ExampleThemePreviews
@Composable
private fun AmountExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { AmountExample() }

@ExampleThemePreviews
@Composable
private fun KeypadExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { KeypadExample() }

@ExampleThemePreviews
@Composable
private fun ChipRowsExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { ChipRowsExample() }

@ExampleThemePreviews
@Composable
private fun NoteComposerExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { NoteComposerExample() }

@ExampleThemePreviews
@Composable
private fun EntryDockExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { EntryDockExample() }

@ExampleThemePreviews
@Composable
private fun NavigationBarExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { NavigationBarExample() }

@ExampleThemePreviews
@Composable
private fun MonthBarExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { MonthBarExample() }

@ExampleThemePreviews
@Composable
private fun PastMonthRibbonExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { PastMonthRibbonExample() }

@ExampleThemePreviews
@Composable
private fun FootnotesExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { FootnotesExample() }

@ExampleThemePreviews
@Composable
private fun AnswerSlipExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { AnswerSlipExample() }

@ExampleThemePreviews
@Composable
private fun ComparisonExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { ComparisonExample() }

@ExampleThemePreviews
@Composable
private fun RankingExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { RankingExample() }

@ExampleThemePreviews
@Composable
private fun SuggestionExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { SuggestionExample() }

@ExampleThemePreviews
@Composable
private fun PlanSlipExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { PlanSlipExample() }

@ExampleThemePreviews
@Composable
private fun ScheduleRowExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { ScheduleRowExample() }

@ExampleThemePreviews
@Composable
private fun LimitRowExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { LimitRowExample() }

@ExampleThemePreviews
@Composable
private fun RepeatSheetOpenPreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { RepeatSheetExample(startOpen = true) }

@ExampleThemePreviews
@Composable
private fun ChoiceSlipOpenPreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { ExampleChoice() }

@ExampleThemePreviews
@Composable
private fun NoticeExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { NoticeExample() }

@ExampleThemePreviews
@Composable
private fun SplitChartsExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { SplitChartsExample() }

@ExampleThemePreviews
@Composable
private fun GoalsBandExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { GoalsBandExample() }

@ExampleThemePreviews
@Composable
private fun MilestonesExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { MilestonesExample() }

@ExampleThemePreviews
@Composable
private fun GoalPillExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { GoalPillExample() }

@ExampleThemePreviews
@Composable
private fun GoalFormExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { GoalFormExample() }

@ExampleThemePreviews
@Composable
private fun GoalCardsExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { GoalCardsExample() }

@ExampleThemePreviews
@Composable
private fun MovementsExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { MovementsExample() }

@ExampleThemePreviews
@Composable
private fun InvestmentStatesExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { InvestmentStatesExample() }

@ExampleThemePreviews
@Composable
private fun InvestmentsExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { InvestmentsExample() }

@ExampleThemePreviews
@Composable
private fun IconStripExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { IconStripExample() }

@ExampleThemePreviews
@Composable
private fun DangerAndTicketExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { DangerAndTicketExample() }

@ExampleThemePreviews
@Composable
private fun CelebrationExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { CelebrationExample() }

@ExampleThemePreviews
@Composable
private fun NoteComposerOpenPreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) {
    NoteComposer(
        AnnotatedString("Gasto en Comida · 48.700"),
        "Almuerzo",
        {},
        "8/60",
        "Opcional",
        null,
        "Listo",
        "Cancelar",
        {},
        {},
        contextIcon = SelloIcon.Restaurant
    )
}

@ExampleThemePreviews
@Composable
private fun CelebrationOpenPreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) {
    CelebrationOverlay(
        "¡Lo lograste!", "META LOGRADA", "Viaje a la costa", SelloIcon.Flight,
        copFixture(
            3_000_000
        ),
        "16 oct 2026 · 6 aportes",
        GoalPlaces.map {
            it.copy(state = MilestoneState.Earned, description = "${it.label}, logrado")
        },
        "Listo", "Ver mi meta", {}, {}
    )
}

@ExampleThemePreviews
@Composable
private fun SettingRowExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { SettingRowExample() }

@ExampleThemePreviews
@Composable
private fun InkSwatchesExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { InkSwatchesExample() }

@ExampleThemePreviews
@Composable
private fun OptionSlipExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { OptionSlipExample() }

@ExampleThemePreviews
@Composable
private fun ConfirmPointsExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { ConfirmPointsExample() }

@ExampleThemePreviews
@Composable
private fun ChoicePointsExamplePreview(
    @PreviewParameter(ExamplePreviewThemes::class) theme: ExamplePreviewTheme
) = Frame(theme) { ChoicePointsExample() }
