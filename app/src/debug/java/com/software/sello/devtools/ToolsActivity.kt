package com.software.sello.devtools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FormField
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.theme.SelloTheme
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.launch

/**
 * The debug build's tools: which data is in use, the sandbox's financial date, the
 * histories it can load and its reset. It has its own launcher and is absent from
 * release builds.
 */
class ToolsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val tools = SandboxRegistry.tools
        setContent {
            SelloTheme {
                if (tools != null) ToolsScreen(tools)
            }
        }
    }
}

@Composable
fun ToolsScreen(tools: SandboxTools) {
    val scope = rememberCoroutineScope()
    var moving by remember { mutableStateOf(false) }
    fun move(action: suspend SandboxProfiles.() -> Unit) {
        if (moving) return
        moving = true
        scope.launch { tools.profiles.action() }
    }
    SelloScaffold(title = stringResource(R.string.tools_title)) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ProfileSlip(tools, moving, onEnter = {
                move { enter() }
            }, onLeave = { move { leave() } })
            tools.time?.let { TimeSlip(it) }
            ScenarioSlip(tools.scenarios, enabled = !moving, onLoad = { move { load(it) } })
            if (tools.profiles.inSandbox) {
                ResetSlip(enabled = !moving, onReset = {
                    move { reset() }
                })
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = SelloTheme.colors.inkSoft,
        modifier = modifier
    )
}

@Composable
private fun ProfileSlip(
    tools: SandboxTools,
    moving: Boolean,
    onEnter: () -> Unit,
    onLeave: () -> Unit
) {
    val inSandbox = tools.profiles.inSandbox
    Slip(Modifier.fillMaxWidth()) {
        Heading(stringResource(R.string.tools_profile_heading))
        Text(
            stringResource(
                if (inSandbox) R.string.tools_profile_sandbox else R.string.tools_profile_normal
            ),
            modifier = Modifier.testTag(TOOLS_PROFILE_TAG)
        )
        if (tools.time == null && inSandbox) Note(stringResource(R.string.tools_profile_unopened))
        if (moving) {
            Note(
                stringResource(R.string.tools_moving),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        SelloButton(
            text = stringResource(if (inSandbox) R.string.tools_leave else R.string.tools_enter),
            onClick = if (inSandbox) onLeave else onEnter,
            loading = moving,
            modifier = Modifier.fillMaxWidth().testTag(TOOLS_SWITCH_TAG)
        )
    }
}

@Composable
private fun TimeSlip(time: SandboxTime) {
    val state by time.state.collectAsState()
    var typed by rememberSaveable { mutableStateOf("") }
    var problem by rememberSaveable { mutableStateOf<Int?>(null) }
    Slip(Modifier.fillMaxWidth()) {
        Heading(stringResource(R.string.tools_date_heading))
        LeaderLine(
            stringResource(R.string.tools_date),
            spokenDate(state.day.date),
            Modifier.testTag(TOOLS_DATE_TAG)
        )
        LeaderLine(stringResource(R.string.tools_zone), state.day.zone.id)
        Note(stringResource(R.string.tools_real_time_note))
        if (state.changed) Note(stringResource(R.string.tools_forward_only))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
            SelloButton(
                stringResource(R.string.tools_plus_day),
                { time.advanceDays(1) },
                Modifier.testTag(TOOLS_PLUS_DAY_TAG),
                kind = ButtonKind.Outlined
            )
            SelloButton(
                stringResource(R.string.tools_plus_month),
                { time.advanceMonths(1) },
                Modifier.testTag(TOOLS_PLUS_MONTH_TAG),
                kind = ButtonKind.Outlined
            )
        }
        FormField(
            label = stringResource(R.string.tools_jump_label),
            value = typed,
            onValueChange = {
                typed = it
                problem = null
            },
            message = problem?.let { FieldMessage(stringResource(it)) },
            modifier = Modifier.testTag(TOOLS_JUMP_FIELD_TAG)
        )
        SelloButton(
            stringResource(R.string.tools_jump),
            {
                problem = try {
                    when (time.jumpTo(LocalDate.parse(typed.trim()))) {
                        DateChange.Moved -> null
                        DateChange.RefusedBeforeSavedHistory -> R.string.tools_jump_refused
                    }
                } catch (_: DateTimeParseException) {
                    R.string.tools_jump_invalid
                }
            },
            Modifier.fillMaxWidth().testTag(TOOLS_JUMP_TAG),
            kind = ButtonKind.Outlined,
            enabled = typed.isNotBlank()
        )
        if (state.playing) {
            Note(
                pluralStringResource(R.plurals.tools_playing, state.stepsLeft, state.stepsLeft),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        SelloButton(
            stringResource(if (state.playing) R.string.tools_pause else R.string.tools_play),
            { if (state.playing) time.pause() else time.play() },
            Modifier.fillMaxWidth().testTag(TOOLS_PLAY_TAG)
        )
    }
}

@Composable
private fun ScenarioSlip(runner: ScenarioRunner?, enabled: Boolean, onLoad: (Scenario) -> Unit) {
    var loading by rememberSaveable { mutableStateOf<String?>(null) }
    Slip(Modifier.fillMaxWidth()) {
        Heading(stringResource(R.string.tools_scenarios_heading))
        runner?.let { ScenarioProgress(it) }
        Note(stringResource(R.string.tools_scenarios_note))
        Scenarios.all.forEach { scenario ->
            Text(scenario.title, style = MaterialTheme.typography.titleSmall)
            Note(
                scenario.summary + " " +
                    stringResource(R.string.tools_scenario_ends, spokenDate(scenario.today))
            )
            if (loading == scenario.key) {
                SelloButton(
                    stringResource(R.string.tools_scenario_load_confirm),
                    { onLoad(scenario) },
                    Modifier.fillMaxWidth().testTag(scenarioTag(scenario, "confirm")),
                    tone = ButtonTone.Loss,
                    enabled = enabled
                )
                SelloButton(
                    stringResource(R.string.tools_reset_cancel),
                    { loading = null },
                    Modifier.fillMaxWidth(),
                    kind = ButtonKind.Text
                )
            } else {
                SelloButton(
                    stringResource(R.string.tools_scenario_load),
                    { loading = scenario.key },
                    Modifier.fillMaxWidth().testTag(scenarioTag(scenario, "load")),
                    kind = ButtonKind.Outlined,
                    enabled = enabled
                )
            }
        }
    }
}

/** The loaded history: loading, loaded up to its day, or stopped with why and a retry. */
@Composable
private fun ScenarioProgress(runner: ScenarioRunner) {
    val state by runner.state.collectAsState()
    val scenario = state.scenario ?: return
    Text(scenario.title, style = MaterialTheme.typography.titleSmall)
    Text(
        when {
            state.finished ->
                stringResource(R.string.tools_scenario_loaded, spokenDate(scenario.today))

            state.problem != null ->
                stringResource(R.string.tools_scenario_stopped, state.next + 1, state.total)

            else -> stringResource(R.string.tools_scenario_progress, state.next + 1, state.total)
        },
        modifier = Modifier
            .testTag(TOOLS_SCENARIO_PROGRESS_TAG)
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
    state.problem?.let {
        Text(
            it,
            color = SelloTheme.colors.semantic.loss,
            modifier = Modifier.testTag(TOOLS_SCENARIO_PROBLEM_TAG)
        )
    }
    if (!state.finished && !state.loading) {
        SelloButton(
            stringResource(R.string.tools_scenario_retry),
            { runner.load() },
            Modifier.fillMaxWidth().testTag(TOOLS_SCENARIO_RETRY_TAG),
            kind = ButtonKind.Outlined
        )
    }
}

fun scenarioTag(scenario: Scenario, action: String) = "tools:scenario:${scenario.id}:$action"

@Composable
private fun ResetSlip(enabled: Boolean, onReset: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Slip(Modifier.fillMaxWidth()) {
        Heading(stringResource(R.string.tools_reset_heading))
        Note(stringResource(R.string.tools_reset_note))
        if (confirming) {
            SelloButton(
                stringResource(R.string.tools_reset_confirm),
                onReset,
                Modifier.fillMaxWidth().testTag(TOOLS_RESET_CONFIRM_TAG),
                tone = ButtonTone.Loss,
                enabled = enabled
            )
            SelloButton(
                stringResource(R.string.tools_reset_cancel),
                { confirming = false },
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Text
            )
        } else {
            SelloButton(
                stringResource(R.string.tools_reset),
                { confirming = true },
                Modifier.fillMaxWidth().testTag(TOOLS_RESET_TAG),
                kind = ButtonKind.Outlined,
                enabled = enabled
            )
        }
    }
}

const val TOOLS_PROFILE_TAG = "tools:profile"
const val TOOLS_SWITCH_TAG = "tools:switch"
const val TOOLS_DATE_TAG = "tools:date"
const val TOOLS_PLUS_DAY_TAG = "tools:plus-day"
const val TOOLS_PLUS_MONTH_TAG = "tools:plus-month"
const val TOOLS_JUMP_FIELD_TAG = "tools:jump-field"
const val TOOLS_JUMP_TAG = "tools:jump"
const val TOOLS_PLAY_TAG = "tools:play"
const val TOOLS_RESET_TAG = "tools:reset"
const val TOOLS_RESET_CONFIRM_TAG = "tools:reset-confirm"
const val TOOLS_SCENARIO_PROGRESS_TAG = "tools:scenario-progress"
const val TOOLS_SCENARIO_PROBLEM_TAG = "tools:scenario-problem"
const val TOOLS_SCENARIO_RETRY_TAG = "tools:scenario-retry"
