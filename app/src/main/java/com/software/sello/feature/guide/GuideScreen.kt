package com.software.sello.feature.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.software.sello.designsystem.component.SectionTitle
import com.software.sello.designsystem.component.SettingRow
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.theme.SelloTheme

/*
 * The Guía de Sello (SELLO-206): static pages from string resources, the same at every
 * moment, so there is nothing to load, fail or save and no view model. The page shown is
 * the route's, and the scroll position is saved with the screen, so both survive rotation
 * and the process being stopped.
 */

/** The Guía's list: each section's title over a row per page; a tap opens it. */
@Composable
fun GuideScreen(
    contentPadding: PaddingValues,
    onOpen: (GuidePage) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .testTag(GUIDE_TAG)
    ) {
        GuideSection.entries.forEach { section ->
            val pages = GuidePage.entries.filter { it.section == section }
            if (pages.isNotEmpty()) {
                Slip(Modifier.fillMaxWidth()) {
                    SectionTitle(stringResource(section.title))
                    pages.forEachIndexed { index, page ->
                        SettingRow(
                            title = stringResource(page.title),
                            icon = page.icon,
                            caption = page.caption?.let { stringResource(it) },
                            onClick = { onOpen(page) },
                            divider = index > 0,
                            modifier = Modifier.testTag(guideRowTag(page))
                        )
                    }
                }
            }
        }
    }
}

/** One page of the Guía: its topics, each a bold term over plain text, on one slip. */
@Composable
fun GuidePageScreen(page: GuidePage, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val colors = SelloTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .testTag(guidePageTag(page))
    ) {
        Slip(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg)) {
                page.lede?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.inkSoft
                    )
                }
                page.topics.forEach { topic ->
                    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.xs)) {
                        Text(
                            stringResource(topic.term),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = SelloTheme.type.weight.emphasis,
                            color = colors.ink,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            stringResource(topic.body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.inkSoft
                        )
                    }
                }
            }
        }
    }
}

const val GUIDE_TAG = "guide"

fun guideRowTag(page: GuidePage) = "guide:row:${page.id}"

fun guidePageTag(page: GuidePage) = "guide:page:${page.id}"
