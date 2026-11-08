package com.software.sello.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.software.sello.R
import com.software.sello.feature.guide.GuidePage
import com.software.sello.feature.guide.GuidePageScreen
import com.software.sello.feature.guide.GuideScreen
import com.software.sello.presentation.month.monthNames

/**
 * Opens the Guía de Sello at [page] over the screen the person is on, as a help sheet's
 * "Ver más en la Guía de Sello" does: Back returns to that screen.
 */
fun NavHostController.openGuide(page: GuidePage) {
    navigate(GuidePageRoute(page.id)) { launchSingleTop = true }
}

/** The Guía de Sello: its list and its pages, titled with no month. */
internal fun NavGraphBuilder.guideDestinations(
    navigation: NavHostController,
    shellState: State<ShellState>,
    onShell: (ShellAction) -> Unit
) {
    composable<GuideRoute> {
        GuideFrame(navigation, shellState, onShell, stringResource(R.string.guide_title)) {
            GuideScreen(it, onOpen = navigation::openGuide)
        }
    }
    composable<GuidePageRoute> { entry ->
        val page = GuidePage.byId(entry.toRoute<GuidePageRoute>().page)
        // An id this version does not know becomes the list itself, so that a page opened
        // from it returns to it instead of replacing it.
        if (page == null) {
            LaunchedEffect(Unit) {
                navigation.navigate(GuideRoute) {
                    popUpTo<GuidePageRoute> { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        val title = stringResource(page?.title ?: R.string.guide_title)
        GuideFrame(navigation, shellState, onShell, title) { padding ->
            if (page == null) {
                GuideScreen(padding, onOpen = navigation::openGuide)
            } else {
                GuidePageScreen(page, padding)
            }
        }
    }
}

@Composable
private fun GuideFrame(
    navigation: NavHostController,
    shellState: State<ShellState>,
    onShell: (ShellAction) -> Unit,
    title: String,
    content: @Composable (PaddingValues) -> Unit
) {
    val state by shellState
    ShellScreen(
        state,
        monthNames(),
        onShell,
        navigation = null,
        onBack = { navigation.popBackStack() },
        // Not tied to a month: its title alone, as Tu año's.
        titleOnly = title,
        pastMonthRibbon = false,
        entryDock = false,
        content = content
    )
    BackHandler(enabled = !state.monthPickerOpen) { navigation.popBackStack() }
}
