package com.software.sello

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
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
import com.software.sello.feature.bulk.BulkCategory
import com.software.sello.feature.bulk.BulkCorrection
import com.software.sello.feature.bulk.BulkEntryScreen
import com.software.sello.feature.bulk.BulkEntryState
import com.software.sello.feature.bulk.BulkLineUi
import com.software.sello.feature.category.CarryCard
import com.software.sello.feature.category.CarryQuestion
import com.software.sello.feature.category.CategoryEditorScreen
import com.software.sello.feature.category.CategoryEditorState
import com.software.sello.feature.category.DropFigures
import com.software.sello.feature.category.DropReason
import com.software.sello.feature.category.EDITOR_CARRY_SWITCH_TAG
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
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.ShellState
import com.software.sello.navigation.ShellTab
import com.software.sello.navigation.appTabs
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
 * What signed carry-over looks like (ADR 0020, SELLO-041;
 * docs/design/system/features/recibo.html#screen-recibo), kept as committed images (ADR 0022):
 * Recibo and the category detail of SC13, the editor's card and the drop question of SC22, the list
 * before deleting SC25's expense and catch-up's lines of SC47. Every figure is copied from
 * docs/testing/signed-carryover-examples.md.
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

    /**
     * SC11 on 22 feb 2027: Comida (base 100.000) carries −150.000 from ene, so its capacity
     * is −50.000; with Hogar and Transporte the month is 20.000 over: 0 por día beside the
     * signed deficit, never hiding it (CP14, CP15).
     */
    private fun deficitWithZeroAllowance(look: Look) {
        val sc11 = carryMonthOf(
            "2027-02-22",
            listOf(
                categoryIn(1, "Comida", 100_000, icon = "restaurant"),
                categoryIn(2, "Hogar", 400_000),
                categoryIn(3, "Transporte", 50_000)
            ),
            listOf(
                storedExpense(110, 1, "2027-01-10", 250_000),
                storedExpense(111, 2, "2027-02-06", 380_000),
                storedExpense(112, 3, "2027-02-07", 40_000)
            ),
            mapOf(1 to ene)
        )
        rule.showSello(look) {
            ReciboScreen(render.recibo(ReadState.Ready(sc11)), {}, PaddingValues(12.dp))
        }
        rule.captureStill("src/test/screenshots/carry-deficit-${look.label}.png")
        // The carrying cell gives its signed carry: a deficit is never shown as a sobrante.
        rule.onNodeWithText("−150.000", useUnmergedTree = true).assertExists()
    }

    @Test
    fun deficitWithZeroAllowanceLight() = deficitWithZeroAllowance(Look())

    @Test
    fun deficitWithZeroAllowanceDark() = deficitWithZeroAllowance(Look(dark = true))

    /**
     * Colombian sizes on 22 feb 2027: Comida (base 2.000.000) brings +600.000 and Hogar
     * (base 3.500.000) −2.350.000 from ene; Transporte 500.000 does not carry. The month's
     * sum has to fit beside the cells' signed carries at the narrowest width.
     */
    private fun reciboInMillions(look: Look) {
        val millions = carryMonthOf(
            "2027-02-22",
            listOf(
                categoryIn(1, "Comida", 2_000_000, icon = "restaurant"),
                categoryIn(2, "Hogar", 3_500_000),
                categoryIn(3, "Transporte", 500_000)
            ),
            listOf(
                storedExpense(130, 1, "2027-01-10", 1_400_000),
                storedExpense(131, 2, "2027-01-12", 5_850_000),
                storedExpense(132, 1, "2027-02-05", 900_000),
                storedExpense(133, 2, "2027-02-06", 3_000_000),
                storedExpense(134, 3, "2027-02-07", 300_000)
            ),
            mapOf(1 to ene, 2 to ene)
        )
        rule.showSello(look) {
            ReciboScreen(render.recibo(ReadState.Ready(millions)), {}, PaddingValues(12.dp))
        }
        rule.captureStill("src/test/screenshots/carry-recibo-millions-${look.label}.png")
    }

    @Test
    fun reciboInMillionsLight() = reciboInMillions(Look())

    @Test
    fun reciboInMillionsDark() = reciboInMillions(Look(dark = true))

    private fun categoryDetail(look: Look) {
        val comida = render.categories(sc13).first { it.name == "Comida" }
        rule.showSello(look) {
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
        rule.captureStill("src/test/screenshots/carry-detail-${look.label}.png")
    }

    @Test
    fun categoryDetailLight() = categoryDetail(Look())

    @Test
    fun categoryDetailDark() = categoryDetail(Look(dark = true))

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
            base = plain("100.000"),
            carry = minus("40.000"),
            carryMagnitude = plain("40.000"),
            direction = CarryDirection.Deficit,
            capacity = plain("60.000")
        )
    )

    private fun editorCarrying(look: Look) {
        rule.showSello(look) { CategoryEditorScreen(carrying, {}) }
        rule.onNodeWithTag(EDITOR_CARRY_SWITCH_TAG).revealed().assertIsDisplayed()
        rule.captureStill("src/test/screenshots/carry-editor-${look.label}.png")
    }

    @Test
    fun editorCarryingLight() = editorCarrying(Look())

    @Test
    fun editorCarryingDark() = editorCarrying(Look(dark = true))

    private fun editorCarryOff(look: Look) {
        rule.showSello(look) {
            CategoryEditorScreen(carrying.copy(carry = CarryCard.Off(abr)), {})
        }
        rule.onNodeWithTag(EDITOR_CARRY_SWITCH_TAG).revealed().assertIsDisplayed()
        rule.captureStill("src/test/screenshots/carry-editor-off-${look.label}.png")
    }

    @Test
    fun editorCarryOffLight() = editorCarryOff(Look())

    @Test
    fun editorCarryOffDark() = editorCarryOff(Look(dark = true))

    private fun dropQuestion(look: Look) {
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
        rule.showSello(look) { CategoryEditorScreen(asked, {}) }
        rule.captureStill("src/test/screenshots/carry-drop-${look.label}.png")
    }

    /** The carry moved while the drop was asked: it was −30.000, now −40.000; nothing saved. */
    private fun dropChanged(look: Look) {
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
                    remainingAfter = plain("100.000"),
                    changedFrom = minus("30.000")
                )
            )
        )
        rule.showSello(look) { CategoryEditorScreen(asked, {}) }
        rule.captureStill("src/test/screenshots/carry-drop-changed-${look.label}.png")
    }

    @Test fun dropChangedLight() = dropChanged(Look())

    @Test fun dropChangedDark() = dropChanged(Look(dark = true))

    @Test
    fun dropQuestionLight() = dropQuestion(Look())

    @Test
    fun dropQuestionDark() = dropQuestion(Look(dark = true))

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
    private val sc25 = CarryCorrection(
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

    private val sc25List by lazy {
        val comida = categoryIn(1, "Comida", 100_000, icon = "restaurant").category
        CarryCorrectionText(money).list(sc25, mapOf(comida.id to comida))
    }

    private fun deleteCorrection(look: Look) {
        val list = sc25List
        rule.showSello(look) {
            // The slip opens over a screen; the desk stands in for it.
            Box(Modifier.fillMaxSize().background(SelloTheme.colors.desk))
            CorrectionQuestionSlip(CorrectionQuestion(CorrectionKind.Delete, list), {}, {})
        }
        rule.captureStill("src/test/screenshots/carry-delete-${look.label}.png")
    }

    @Test
    fun deleteCorrectionLight() = deleteCorrection(Look())

    @Test
    fun deleteCorrectionDark() = deleteCorrection(Look(dark = true))

    /**
     * SC25 the other way: putting ene's 20.000 back (undoing the deletion, or anotar it in a
     * receipt) moves feb, mar and abr by −20.000, back to the figures before the deletion.
     */
    private val sc25Back = CarryCorrection(
        months = sc25.months.map {
            CarryMonthChange(it.categoryId, it.month, it.after, it.before, cop(-20_000))
        },
        edited = emptyList(),
        settled = emptyList(),
        firstAffected = feb,
        overallCapacityChange = mapOf(
            feb to cop(-20_000),
            mar to cop(-20_000),
            abr to cop(-20_000)
        )
    )

    private val sc25BackList by lazy {
        val comida = categoryIn(1, "Comida", 100_000, icon = "restaurant").category
        CarryCorrectionText(money).list(sc25Back, mapOf(comida.id to comida))
    }

    /** Recibo on 10 abr 2027, Comida carrying from ene: the screen the question opens over. */
    private val abrRecibo by lazy {
        carryMonthOf(
            "2027-04-10",
            listOf(
                categoryIn(1, "Comida", 100_000, icon = "restaurant"),
                categoryIn(2, "Transporte", 50_000)
            ),
            listOf(
                storedExpense(140, 1, "2027-04-05", 30_000),
                storedExpense(141, 2, "2027-04-06", 10_000)
            ),
            mapOf(1 to ene)
        )
    }

    private val abrShell = ShellState(
        currentMonth = abr,
        selectedMonth = abr,
        monthPickerOpen = false,
        pickerYear = 2027,
        pendingEntry = null
    )

    /** The correction question over Recibo's own frame (ReciboDestinations.kt), as SelloAppRoot shows it. */
    private fun question(name: String, question: CorrectionQuestion, look: Look) {
        rule.showSello(look) {
            ShellScreen(
                abrShell,
                monthNames(),
                {},
                navigation = appTabs(ShellTab.Recibo),
                actions = {
                    IconButton(onClick = {}) {
                        Icon(SelloIcon.Settings.painter(), stringResource(R.string.settings_open))
                    }
                }
            ) { padding ->
                ReciboScreen(render.recibo(ReadState.Ready(abrRecibo)), {}, padding)
            }
            CorrectionQuestionSlip(question, {}, {})
        }
        rule.captureStill("src/test/screenshots/carry-$name-${look.label}.png")
    }

    private val undoQuestion by lazy { CorrectionQuestion(CorrectionKind.Undo, sc25BackList) }

    @Test fun undoQuestionLight() = question("undo", undoQuestion, Look())

    @Test fun undoQuestionDark() = question("undo", undoQuestion, Look(dark = true))

    /** The data moved while it was open: the list read again, said above it. */
    @Test
    fun changedQuestionLight() = question(
        "changed",
        CorrectionQuestion(CorrectionKind.Delete, sc25List, changed = true),
        Look()
    )

    @Test
    fun changedQuestionDark() = question(
        "changed",
        CorrectionQuestion(CorrectionKind.Delete, sc25List, changed = true),
        Look(dark = true)
    )

    @Test
    fun busyQuestionLight() =
        question("busy", CorrectionQuestion(CorrectionKind.Delete, sc25List, busy = true), Look())

    @Test
    fun busyQuestionDark() = question(
        "busy",
        CorrectionQuestion(CorrectionKind.Delete, sc25List, busy = true),
        Look(dark = true)
    )

    /** Anotar varios on 10 abr 2027 with one line of 20.000 in Comida dated 10 ene: the receipt asks first. */
    private fun bulkCorrection(look: Look, busy: Boolean) {
        val comida = BulkCategory("1", "Comida", "restaurant")
        val state = BulkEntryState(
            lines = listOf(BulkLineUi("1", comida, plain("20.000"), "Mercado")),
            highlighted = 0,
            date = LocalDate.of(2027, 1, 10),
            today = LocalDate.of(2027, 4, 10),
            loading = false,
            categories = listOf(comida),
            total = plain("20.000"),
            count = 1,
            busy = busy,
            correction = BulkCorrection(sc25BackList, changed = false)
        )
        rule.showSello(look) { BulkEntryScreen(state, dateLabels(), {}) }
        val name = if (busy) "bulk-busy" else "bulk"
        rule.captureStill("src/test/screenshots/carry-$name-${look.label}.png")
    }

    @Test fun bulkCorrectionLight() = bulkCorrection(Look(), busy = false)

    @Test fun bulkCorrectionDark() = bulkCorrection(Look(dark = true), busy = false)

    @Test fun bulkCorrectionBusyLight() = bulkCorrection(Look(), busy = true)

    @Test fun bulkCorrectionBusyDark() = bulkCorrection(Look(dark = true), busy = true)

    private fun hogar(month: YearMonth, k: Long, c: Long, s: Long, r: Long, verdict: CarryVerdict) =
        CarryMonth(month, ene, cop(1_000_000), cop(k), cop(c), cop(s), cop(r), verdict)

    /** SC47: catch-up records feb's 300.000 rent in Hogar's chain on 10 mar 2027. */
    private fun catchUpLines(look: Look) {
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
        rule.showSello(look) {
            CatchUpRecordedScreen(state, PaddingValues(12.dp), {}) { _, _ -> }
        }
        rule.captureStill("src/test/screenshots/carry-catch-up-${look.label}.png")
    }

    @Test
    fun catchUpLinesLight() = catchUpLines(Look())

    @Test
    fun catchUpLinesDark() = catchUpLines(Look(dark = true))

    private companion object {
        val InProgress = CarryVerdict.InProgress(Verdict.OnTrack)
        val Within = CarryVerdict.Closed(PastVerdict.WithinLimit)
        val Over = CarryVerdict.Closed(PastVerdict.Over)
    }
}
