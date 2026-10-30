package com.software.sello

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.CarryMonth
import com.software.sello.domain.model.CarryVerdict
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.Verdict
import com.software.sello.domain.policy.CarryCorrection
import com.software.sello.domain.policy.CarryMonthChange
import com.software.sello.domain.port.ReadState
import com.software.sello.feature.category.CarryCard
import com.software.sello.feature.category.CarryQuestion
import com.software.sello.feature.category.CategoryEditorScreen
import com.software.sello.feature.category.CategoryEditorState
import com.software.sello.feature.category.DropFigures
import com.software.sello.feature.category.DropReason
import com.software.sello.feature.category.EDITOR_CARRY_TAG
import com.software.sello.feature.expense.categoryId
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.recibo.CategoryDetailScreen
import com.software.sello.feature.recibo.CategoryDetailState
import com.software.sello.feature.recibo.ExpenseListState
import com.software.sello.feature.recibo.ReciboRenderModel
import com.software.sello.feature.recibo.ReciboScreen
import com.software.sello.feature.recurrence.CatchUpCarry
import com.software.sello.feature.recurrence.CatchUpNoticeState
import com.software.sello.feature.recurrence.CatchUpRecordedScreen
import com.software.sello.feature.recurrence.RecordedItem
import com.software.sello.navigation.CorrectionQuestionSlip
import com.software.sello.presentation.carry.CarryCorrectionText
import com.software.sello.presentation.carry.CarryDirection
import com.software.sello.presentation.carry.carryMonthOf
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.corrections.CorrectionKind
import com.software.sello.presentation.corrections.CorrectionQuestion
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.testing.testMoney
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What signed carry-over looks like (ADR 0020, SELLO-041; docs/design/v2/arrastre.html), kept
 * as committed images (ADR 0022): Recibo and the category detail of SC13, the editor's card and
 * the drop question of SC22, the list before deleting SC25's expense and catch-up's lines of
 * SC47. Every figure is copied from docs/testing/signed-carryover-examples.md.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CarryScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val money = testMoney()
    private val ene = YearMonth.of(2027, 1)
    private val feb = YearMonth.of(2027, 2)
    private val mar = YearMonth.of(2027, 3)
    private val abr = YearMonth.of(2027, 4)

    private fun cop(pesos: Long) = Money.cop(pesos)

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun minus(digits: String) =
        MoneyTextValue(MoneySign.Minus, "", digits, "menos $digits pesos")

    /** SC13: Comida on from ene, Transporte 50.000 off, Ocio unlimited; 22 feb 2027. */
    private val sc13 = carryMonthOf(
        "2027-02-22",
        listOf(
            categoryIn(1, "Comida", 100_000, icon = "restaurant"),
            categoryIn(2, "Transporte", 50_000),
            categoryIn(3, "Ocio", null)
        ),
        listOf(
            storedExpense(100, 1, "2027-01-10", 20_000),
            storedExpense(101, 1, "2027-02-05", 30_000),
            storedExpense(102, 2, "2027-02-06", 60_000),
            storedExpense(103, 3, "2027-02-07", 10_000)
        ),
        mapOf(1 to ene)
    )

    private val render = ReciboRenderModel(money)

    private fun recibo(look: Look) {
        rule.showSello(look) {
            ReciboScreen(render.recibo(ReadState.Ready(sc13)), {}, PaddingValues(12.dp))
        }
        rule.captureStill("src/test/screenshots/carry-recibo-${look.label}.png")
    }

    @Test
    fun reciboCobaltoLight() = recibo(Look())

    @Test
    fun reciboCobaltoDark() = recibo(Look(dark = true))

    @Test
    fun reciboVioletaLight() = recibo(Look(ink = SelloInk.Violeta))

    @Test
    fun categoryDetail() {
        val comida = render.categories(sc13).first { it.name == "Comida" }
        rule.showSello(Look()) {
            CategoryDetailScreen(
                state = CategoryDetailState.Ready(
                    category = comida,
                    month = feb,
                    today = LocalDate.of(2027, 2, 22),
                    stale = false,
                    canRecord = true,
                    expenses = ExpenseListState.Empty
                ),
                months = monthNames(),
                dates = dateLabels(),
                showName = true,
                onAction = {},
                onRecordHere = {},
                contentPadding = PaddingValues(12.dp)
            )
        }
        rule.captureStill("src/test/screenshots/carry-detail-cobalto-light-font1.0.png")
    }

    /** Comida in abr of SC22: base 100.000, carry −40.000, capacity 60.000. */
    private val carrying = CategoryEditorState(
        loading = false,
        iconKeys = CategoryIcons.keys,
        iconKey = "restaurant",
        editing = true,
        name = "Comida",
        limitText = "100000",
        limitAmount = plain("100.000"),
        targetMonth = abr,
        carry = CarryCard.On(
            since = ene,
            carry = minus("40.000"),
            carryMagnitude = plain("40.000"),
            direction = CarryDirection.Deficit,
            capacity = plain("60.000")
        )
    )

    @Test
    fun editorCarrying() {
        rule.showSello(Look()) { CategoryEditorScreen(carrying, {}) }
        rule.onNodeWithTag(EDITOR_CARRY_TAG).performScrollTo()
        rule.captureStill("src/test/screenshots/carry-editor-cobalto-light-font1.0.png")
    }

    @Test
    fun dropQuestion() {
        val asked = carrying.copy(
            carryQuestion = CarryQuestion.Drop(
                DropReason.Disable,
                DropFigures(
                    month = abr,
                    carry = minus("40.000"),
                    base = plain("100.000"),
                    capacityBefore = plain("60.000"),
                    capacityAfter = plain("100.000"),
                    remainingBefore = plain("60.000"),
                    remainingAfter = plain("100.000")
                )
            )
        )
        rule.showSello(Look()) { CategoryEditorScreen(asked, {}) }
        rule.captureStill("src/test/screenshots/carry-drop-cobalto-light-font1.0.png")
    }

    private fun closed(month: YearMonth, k: Long, c: Long, s: Long, r: Long, over: Boolean) =
        CarryMonth(
            month,
            ene,
            cop(100_000),
            cop(k),
            cop(c),
            cop(s),
            cop(r),
            CarryVerdict.Closed(if (over) PastVerdict.Over else PastVerdict.WithinLimit)
        )

    /** SC25: deleting ene's 20.000 moves feb, mar and abr by +20.000; mar stays Excedido. */
    @Test
    fun deleteCorrection() {
        val sc25 = CarryCorrection(
            months = listOf(
                CarryMonthChange(
                    categoryId(1),
                    feb,
                    closed(feb, 80_000, 180_000, 70_000, 110_000, false),
                    closed(feb, 100_000, 200_000, 70_000, 130_000, false),
                    cop(20_000)
                ),
                CarryMonthChange(
                    categoryId(1),
                    mar,
                    closed(mar, 110_000, 210_000, 250_000, -40_000, true),
                    closed(mar, 130_000, 230_000, 250_000, -20_000, true),
                    cop(20_000)
                ),
                CarryMonthChange(
                    categoryId(1),
                    abr,
                    CarryMonth(
                        abr,
                        ene,
                        cop(100_000),
                        cop(-40_000),
                        cop(60_000),
                        cop(0),
                        cop(60_000),
                        CarryVerdict.InProgress(Verdict.OnTrack)
                    ),
                    CarryMonth(
                        abr,
                        ene,
                        cop(100_000),
                        cop(-20_000),
                        cop(80_000),
                        cop(0),
                        cop(80_000),
                        CarryVerdict.InProgress(Verdict.OnTrack)
                    ),
                    cop(20_000)
                )
            ),
            edited = emptyList(),
            settled = emptyList(),
            firstAffected = feb,
            overallCapacityChange = mapOf(
                feb to cop(20_000),
                mar to cop(20_000),
                abr to cop(20_000)
            )
        )
        val comida = categoryIn(1, "Comida", 100_000, icon = "restaurant").category
        val list = CarryCorrectionText(money).list(sc25, mapOf(comida.id to comida))
        rule.showSello(Look()) {
            // The slip opens over a screen; the desk stands in for it.
            Box(Modifier.fillMaxSize().background(SelloTheme.colors.desk))
            CorrectionQuestionSlip(CorrectionQuestion(CorrectionKind.Delete, list), {}, {})
        }
        rule.captureStill("src/test/screenshots/carry-delete-cobalto-light-font1.0.png")
    }

    private fun hogar(month: YearMonth, k: Long, c: Long, s: Long, r: Long, verdict: CarryVerdict) =
        CarryMonth(month, ene, cop(1_000_000), cop(k), cop(c), cop(s), cop(r), verdict)

    /** SC47: catch-up records feb's 300.000 rent in Hogar's chain on 10 mar 2027. */
    @Test
    fun catchUpLines() {
        val sc47 = CarryCorrection(
            months = listOf(
                CarryMonthChange(
                    categoryId(1),
                    mar,
                    hogar(mar, 200_000, 1_200_000, 500_000, 700_000, InProgress),
                    hogar(mar, -100_000, 900_000, 500_000, 400_000, InProgress),
                    cop(-300_000)
                )
            ),
            edited = listOf(
                CarryMonthChange(
                    categoryId(1),
                    feb,
                    hogar(feb, 0, 1_000_000, 800_000, 200_000, Within),
                    hogar(feb, 0, 1_000_000, 1_100_000, -100_000, Over),
                    cop(0)
                )
            ),
            settled = emptyList(),
            firstAffected = feb,
            overallCapacityChange = mapOf(mar to cop(-300_000))
        )
        val hogar = categoryIn(1, "Hogar", 1_000_000, icon = "home").category
        val list = CarryCorrectionText(money).list(listOf(sc47), mapOf(hogar.id to hogar))
        val state = CatchUpNoticeState.Ready(
            items = listOf(
                RecordedItem(
                    id = "rent",
                    income = false,
                    note = "Arriendo",
                    categoryName = "Hogar",
                    iconKey = "home",
                    sourceKey = null,
                    sourceName = null,
                    date = LocalDate.of(2027, 2, 5),
                    amount = plain("300.000")
                )
            ),
            today = LocalDate.of(2027, 3, 10),
            carry = CatchUpCarry.Changed(list)
        )
        rule.showSello(Look()) {
            CatchUpRecordedScreen(state, PaddingValues(12.dp), {}) { _, _ -> }
        }
        rule.captureStill("src/test/screenshots/carry-catch-up-cobalto-light-font1.0.png")
    }

    private companion object {
        val InProgress = CarryVerdict.InProgress(Verdict.OnTrack)
        val Within = CarryVerdict.Closed(PastVerdict.WithinLimit)
        val Over = CarryVerdict.Closed(PastVerdict.Over)
    }
}
