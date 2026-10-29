package com.software.sello.devtools

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.software.sello.MainActivity
import com.software.sello.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Puts the sandbox badge over every app screen while the sandbox is in use: a small
 * label in the status bar's strip, so it covers no control, that says the data is the
 * sandbox's and on which financial date. Tapping it opens the tools.
 */
object SandboxBadges {
    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityPostCreated(activity: Activity, saved: Bundle?) {
                    if (activity is MainActivity) attach(activity)
                }

                override fun onActivityCreated(activity: Activity, saved: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }

    private fun attach(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val badge = ComposeView(activity).apply {
            setContent {
                SandboxBadge(onOpen = {
                    activity.startActivity(Intent(activity, ToolsActivity::class.java))
                })
            }
        }
        content.addView(
            badge,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }
}

@Composable
internal fun SandboxBadge(
    onOpen: () -> Unit,
    time: SandboxTime? = SandboxRegistry.tools?.time,
    scenarios: ScenarioRunner? = SandboxRegistry.tools?.scenarios
) {
    if (time == null) return
    val state by time.state.collectAsStateWithLifecycle()
    val zone = state.day.zone.id
    // A history not all loaded yet must not pass for a complete one.
    val history = scenarios?.state?.collectAsStateWithLifecycle()?.value
    val unfinished = history != null && history.scenario != null && !history.finished
    val spoken = stringResource(R.string.sandbox_badge_spoken, spokenDate(state.day.date), zone) +
        if (unfinished) " " + stringResource(R.string.sandbox_badge_unfinished_spoken) else ""
    // The status bar's strip, so it covers no control; never less than the label needs.
    val strip = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        Modifier.fillMaxWidth().height(max(strip, MinStrip)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.sandbox_badge, shortDate(state.day.date), zone) +
                if (unfinished) " " + stringResource(R.string.sandbox_badge_unfinished) else "",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .testTag(SANDBOX_BADGE_TAG)
                .background(SandboxInk, RoundedCornerShape(50))
                .clickable(onClick = onOpen)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    role = Role.Button
                    onClick {
                        onOpen()
                        true
                    }
                }
                .padding(horizontal = 10.dp, vertical = 2.dp)
        )
    }
}

private val Spanish = Locale.forLanguageTag("es")

internal fun shortDate(date: LocalDate): String =
    DateTimeFormatter.ofPattern("d MMM yyyy", Spanish).format(date).replace(".", "")

internal fun spokenDate(date: LocalDate): String =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Spanish).format(date)

/** A warning orange no Sello ink uses, so the badge is never mistaken for the app. */
internal val SandboxInk = Color(0xFFB34700)

private val MinStrip = 20.dp

const val SANDBOX_BADGE_TAG = "sandbox:badge"
