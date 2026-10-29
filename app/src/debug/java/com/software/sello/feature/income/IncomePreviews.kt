package com.software.sello.feature.income

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import java.time.LocalDate

@Composable
private fun IncomePreview(ink: SelloInk, dark: Boolean, busy: Boolean = false) {
    val date = LocalDate.of(2026, 10, 13)
    SelloTheme(ink = ink, darkTheme = dark, reducedMotion = true) {
        IncomeFormScreen(
            IncomeFormState(
                loading = false,
                date = date,
                today = date,
                amount = MoneyTextValue(MoneySign.None, "", "150.000", "150.000 pesos"),
                sourceKeys = listOf("salary", "freelance", "passive_income", "transfer", "other"),
                sourceKey = "other",
                otherName = "Bono",
                missing = null,
                busy = busy
            ),
            {},
            onExpense = {}
        )
    }
}

@Preview(name = "Cobalto claro", locale = "es", showBackground = true)
@Preview(name = "Fuente 1.3", locale = "es", fontScale = 1.3f)
@Composable
private fun CobaltoLightIncome() = IncomePreview(SelloInk.Cobalto, false)

@Preview(name = "Cobalto oscuro", locale = "es")
@Composable
private fun CobaltoDarkIncome() = IncomePreview(SelloInk.Cobalto, true)

@Preview(name = "Violeta claro", locale = "es")
@Composable
private fun VioletaLightIncome() = IncomePreview(SelloInk.Violeta, false)

@Preview(name = "Violeta oscuro ocupado", locale = "es", widthDp = 840, heightDp = 420)
@Composable
private fun VioletaDarkIncome() = IncomePreview(SelloInk.Violeta, true, busy = true)
