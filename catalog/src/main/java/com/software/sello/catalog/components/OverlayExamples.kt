package com.software.sello.catalog.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.AnnotatedString
import com.software.sello.catalog.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.CelebrationOverlay
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.DangerSlip
import com.software.sello.designsystem.component.MilestoneState
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloNotice
import com.software.sello.designsystem.component.StampTicket
import com.software.sello.designsystem.icon.SelloIcon
import kotlinx.coroutines.delay

@Composable
fun DangerAndTicketExample() {
    val strings = LocalResources.current
    var confirm by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf("") }
    ExampleStack {
        SelloNotice(
            strings.getString(R.string.catalog_demo_gasto_anotado_48_700_en_comida),
            confirmation = true
        )
        SelloNotice(
            strings.getString(
                R.string.catalog_demo_celular_nuevo_meta_lograda
            ),
            actionLabel = "Deshacer",
            onAction = {
                result =
                    strings.getString(R.string.catalog_demo_100_pendiente_de_marcar)
            }
        )
        SelloNotice(
            strings.getString(
                R.string.catalog_demo_gasto_borrado
            ),
            actionLabel = "Deshacer",
            onAction = {
            },
            busy = true
        )
        StampTicket(
            GoalPlaces.take(2),
            strings.getString(R.string.catalog_demo_ganaste_los_sellos_del_25_y_del),
            strings.getString(R.string.catalog_demo_viaje_a_la_costa_faltan_1_290)
        )
        StampTicket(
            listOf(GoalPlaces[1]),
            strings.getString(R.string.catalog_demo_de_vuelta_en_el_50),
            strings.getString(R.string.catalog_demo_viaje_faltan_1_490)
        )
        StampTicket(
            listOf(
                GoalPlaces[3].copy(
                    state = MilestoneState.Ready,
                    description = strings.getString(
                        R.string.catalog_demo_100_por_ciento_pendiente_de_marcar_como
                    )
                )
            ),
            strings.getString(R.string.catalog_demo_llegaste_al_100),
            strings.getString(R.string.catalog_demo_celular_marcala)
        )
        DangerSlip(
            strings.getString(R.string.catalog_demo_borrar_esta_meta),
            AnnotatedString(
                strings.getString(R.string.catalog_demo_se_borran_la_meta_y_sus_6)
            ),
            strings.getString(R.string.catalog_demo_borrar_meta),
            {
                confirm =
                    true
            }
        )
        ExampleCaption(result)
    }
    if (confirm) {
        ConfirmSlip(
            strings.getString(R.string.catalog_demo_borrar_viaje_a_la_costa),
            AnnotatedString(
                strings.getString(R.string.catalog_demo_se_borran_la_meta_y_sus_6)
            ),
            "Borrar",
            {
                result =
                    strings.getString(
                        R.string.catalog_demo_confirmacion_sintetica_el_catalogo_no_borra_registros
                    )
                confirm = false
            },
            {
                confirm =
                    false
            }
        )
    }
}

@Composable
fun CelebrationExample() {
    val strings = LocalResources.current
    var open by rememberSaveable { mutableStateOf(false) }
    var long by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf(false) }
    var outcome by rememberSaveable { mutableStateOf("") }
    val accessibility = LocalAccessibilityManager.current
    val duration =
        accessibility?.calculateRecommendedTimeoutMillis(
            10_000,
            containsIcons = true,
            containsText = true,
            containsControls = true
        )
            ?: 10_000
    LaunchedEffect(notice) {
        if (notice) {
            delay(duration)
            notice = false
        }
    }
    ExampleStack {
        ExampleCaption(
            strings.getString(
                R.string.catalog_demo_celebracion_sintetica_de_una_finalizacion_confirmada_sin
            )
        )
        SelloButton(strings.getString(R.string.catalog_demo_marcar_como_lograda), {
            long = false
            open = true
        })
        SelloButton(strings.getString(R.string.catalog_demo_probar_nombre_y_monto_largos), {
            long = true
            open = true
        }, kind = ButtonKind.Outlined)
        if (notice) {
            SelloNotice(
                strings.getString(
                    R.string.catalog_demo_celular_nuevo_meta_lograda
                ),
                actionLabel = "Deshacer",
                onAction = {
                    notice =
                        false
                    outcome = strings.getString(R.string.catalog_demo_100_pendiente_de_marcar)
                }
            )
        }
        ExampleCaption(outcome)
    }
    if (open) {
        CelebrationOverlay(
            strings.getString(
                R.string.catalog_demo_lo_lograste
            ),
            strings.getString(
                R.string.catalog_demo_meta_lograda
            ),
            if (long) {
                strings.getString(
                    R.string.catalog_demo_un_viaje_con_toda_la_familia_para
                )
            } else {
                strings.getString(R.string.catalog_demo_celular_nuevo)
            },
            SelloIcon.PhoneIphone,
            copFixture(
                if (long) 999_999_999_999 else 1_200_000
            ),
            strings.getString(R.string.catalog_demo_16_oct_2026_6_aportes),
            GoalPlaces.map {
                it.copy(state = MilestoneState.Earned, description = "${it.label}, logrado")
            },
            "Listo", strings.getString(R.string.catalog_demo_ver_mi_meta), {
                open = false
                notice = true
            }, {
                open = false
                outcome =
                    strings.getString(R.string.catalog_demo_meta_lograda_historial_de_solo_lectura)
            }
        )
    }
}
