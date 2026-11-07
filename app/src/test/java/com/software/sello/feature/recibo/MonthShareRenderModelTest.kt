package com.software.sello.feature.recibo

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.monthOf
import com.software.sello.feature.expense.storedExpense
import com.software.sello.testing.testMoney
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The image's render model, from snapshots the real policy works out from rows. Every
 * expected figure was worked out by hand from those rows.
 */
class MonthShareRenderModelTest {
    private val render = MonthShareRenderModel(testMoney())

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val food = categoryIn(1, "Alimentación", 900_000, icon = "restaurant")
    private val transport = categoryIn(2, "Transporte", 200_000, icon = "directions_bus")
    private val leisure = categoryIn(3, "Entretenimiento", 200_000, icon = "theaters")
    private val coffee = categoryIn(4, "Café", 80_000, icon = "local_cafe")
    private val home = categoryIn(5, "Hogar", 1_100_000)
    private val art = categoryIn(6, "Arte", null, icon = "redeem")
    private val everything = listOf(food, transport, leisure, coffee, home, art)

    private var next = 100

    private fun spent(category: Int, date: String, amount: Long, note: String? = null) =
        storedExpense(next++, category, date, amount, note)

    /** The mock's October, with notes on the expenses that must never reach the image. */
    private fun october() = listOf(
        spent(1, "2026-10-03", 600_000, note = "Mercado con Ana"),
        spent(1, "2026-10-20", 212_400),
        spent(2, "2026-10-05", 155_000),
        spent(3, "2026-10-11", 221_500, note = "Concierto"),
        spent(4, "2026-10-12", 48_000),
        spent(5, "2026-10-01", 1_100_000, note = "Arriendo"),
        spent(6, "2026-10-15", 80_000)
    )

    @Test
    fun theMonthInProgressShowsRecibosHeadlineTheTotalsAndEachCategory() {
        val image = render.image(monthOf("2026-10-28", everything, october()))!!

        assertEquals(YearMonth.of(2026, 10), image.month)
        assertEquals(LocalDate.of(2026, 10, 28), image.asOf)
        assertFalse(image.closed)
        // Limits 900.000 + 200.000 + 200.000 + 80.000 + 1.100.000 = 2.480.000. Spent
        // 812.400 + 155.000 + 221.500 + 48.000 + 1.100.000 + 80.000 = 2.416.900, Arte's
        // included. Left 63.100: below the limits, Al día.
        assertEquals(ReciboAnswerLabel.Remaining, image.label)
        assertEquals(plain("63.100"), image.amount)
        assertEquals(ReciboStamp.OnTrack, image.stamp)
        assertEquals(plain("2.416.900"), image.spent)
        assertEquals(plain("2.480.000"), image.limit)
        // Recibo's order: most used first, ties by name, the unlimited one last.
        assertEquals(
            listOf("Alimentación", "Café", "Entretenimiento", "Hogar", "Transporte", "Arte"),
            image.categories.map { it.name }
        )
        val byName = image.categories.associateBy { it.name }
        assertEquals(
            MonthShareCategory(
                name = "Alimentación",
                iconKey = "restaurant",
                spent = plain("812.400"),
                limit = plain("900.000"),
                over = false,
                fraction = byName.getValue("Alimentación").fraction,
                archived = false
            ),
            byName.getValue("Alimentación")
        )
        // 221.500 of 200.000: over, its bar full.
        val over = byName.getValue("Entretenimiento")
        assertEquals(plain("221.500"), over.spent)
        assertEquals(plain("200.000"), over.limit)
        assertTrue(over.over)
        assertEquals(1f, over.fraction)
        // Exactly its limit is not over.
        assertFalse(byName.getValue("Hogar").over)
        // No limit: what it spent and no bar.
        val open = byName.getValue("Arte")
        assertEquals(plain("80.000"), open.spent)
        assertNull(open.limit)
        assertNull(open.fraction)
    }

    @Test
    fun anOverrunSaysHowFarOverWithExcedido() {
        // 900.000 limit, 950.000 spent: 50.000 over.
        val image = render.image(
            monthOf("2026-10-28", listOf(food), listOf(spent(1, "2026-10-04", 950_000)))
        )!!

        assertEquals(ReciboAnswerLabel.Remaining, image.label)
        assertEquals(
            MoneyTextValue(MoneySign.Minus, "", "50.000", "menos 50.000 pesos"),
            image.amount
        )
        assertEquals(ReciboStamp.Over, image.stamp)
        assertEquals(plain("950.000"), image.spent)
        assertEquals(plain("900.000"), image.limit)
    }

    @Test
    fun aPastMonthIsClosedWithItsCumplidoOrExcedidoStamp() {
        val september = YearMonth.of(2026, 9)
        val within = render.image(
            monthOf(
                "2026-10-28",
                listOf(food),
                listOf(spent(1, "2026-09-10", 900_000)),
                month = september
            )
        )!!
        // Exactly the limit, finished: Cumplido, nothing left over.
        assertTrue(within.closed)
        assertEquals(september, within.month)
        assertEquals(ReciboStamp.WithinLimit, within.stamp)
        assertEquals(plain("0"), within.amount)

        val over = render.image(
            monthOf(
                "2026-10-28",
                listOf(food),
                listOf(spent(1, "2026-09-10", 900_001)),
                month = september
            )
        )!!
        assertTrue(over.closed)
        assertEquals(ReciboStamp.OverLimit, over.stamp)
    }

    @Test
    fun anArchivedCategoryIsShownOnlyWhenItHasSpendingThatMonth() {
        val idle = categoryIn(7, "Viejo", 50_000, archived = true)
        val used = categoryIn(8, "Regalos", null, archived = true, icon = "redeem")
        val image = render.image(
            monthOf(
                "2026-10-28",
                listOf(food, idle, used),
                listOf(spent(1, "2026-10-04", 100_000), spent(8, "2026-10-06", 30_000))
            )
        )!!

        assertEquals(listOf("Alimentación", "Regalos"), image.categories.map { it.name })
        assertTrue(image.categories.single { it.name == "Regalos" }.archived)
        assertEquals(plain("30.000"), image.categories.single { it.name == "Regalos" }.spent)
    }

    @Test
    fun withoutAnyLimitTheImageSaysWhatWasSpentAndNoTotalLimit() {
        val image = render.image(
            monthOf("2026-10-28", listOf(art), listOf(spent(6, "2026-10-02", 45_000)))
        )!!

        assertEquals(ReciboAnswerLabel.Spent, image.label)
        assertEquals(ReciboStamp.NoLimit, image.stamp)
        assertEquals(plain("45.000"), image.amount)
        assertEquals(plain("45.000"), image.spent)
        assertNull(image.limit)
    }

    @Test
    fun aMonthWithNothingToShowHasNoImage() {
        assertNull(render.image(monthOf("2026-10-28", emptyList())))
        // Only an archived category with nothing spent: nothing to show either.
        assertNull(
            render.image(monthOf("2026-10-28", listOf(categoryIn(7, "Viejo", 50_000, true))))
        )
    }

    @Test
    fun theImageHasNoFieldForNotesIncomeGoalsInvestmentsOrSettings() {
        assertEquals(
            setOf(
                "month", "asOf", "closed", "label", "amount", "stamp", "spent", "limit",
                "categories"
            ),
            fieldsOf(MonthShareImage::class.java).keys
        )
        assertEquals(
            setOf("name", "iconKey", "spent", "limit", "over", "fraction", "archived"),
            fieldsOf(MonthShareCategory::class.java).keys
        )
        // Everything reachable from the image, through every Sello type it holds.
        val reachable = reachableFields(MonthShareImage::class.java)
        val forbidden =
            Regex("note|income|ingreso|goal|meta|invest|setting|preference|theme|order|ink")
        assertEquals(
            emptyList<String>(),
            reachable.filter { forbidden.containsMatchIn(it.lowercase()) }
        )
        // Only plain values: no Sello type the image doesn't name above can be inside it.
        assertEquals(
            setOf(
                MonthShareImage::class.java,
                MonthShareCategory::class.java,
                MoneyTextValue::class.java,
                MoneySign::class.java,
                ReciboAnswerLabel::class.java,
                ReciboStamp::class.java
            ),
            selloTypesFrom(MonthShareImage::class.java)
        )
    }

    @Test
    fun noNoteOfTheMonthReachesTheImage() {
        val image = render.image(monthOf("2026-10-28", everything, october()))!!

        listOf("Mercado con Ana", "Concierto", "Arriendo").forEach { note ->
            assertFalse(note, image.toString().contains(note))
        }
    }

    private fun fieldsOf(type: Class<*>) = type.declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
        .associate { it.name to it }

    /** The Sello classes a field's type names, its list elements' included. */
    private fun selloTypesIn(field: java.lang.reflect.Field): List<Class<*>> {
        val generic = field.genericType
        val named = if (generic is ParameterizedType) {
            generic.actualTypeArguments.filterIsInstance<Class<*>>() + field.type
        } else {
            listOf(field.type)
        }
        return named.filter { it.name.startsWith("com.software.sello") }
    }

    private fun selloTypesFrom(root: Class<*>): Set<Class<*>> {
        val seen = linkedSetOf<Class<*>>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val type = queue.removeFirst()
            if (!seen.add(type) || type.isEnum) continue
            fieldsOf(type).values.flatMap(::selloTypesIn).forEach(queue::addLast)
        }
        return seen
    }

    private fun reachableFields(root: Class<*>): List<String> =
        selloTypesFrom(root).filterNot { it.isEnum }.flatMap { type ->
            fieldsOf(type).keys.map { "${type.simpleName}.$it" }
        }
}
