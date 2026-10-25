package com.software.sello.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.CorrectionAnnouncementPreference
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** A real on-device preferences file survives a new adapter and never includes financial rows. */
@RunWith(AndroidJUnit4::class)
class DataStoreUserPreferencesPersistenceTest {
    @Test
    fun preferencesSurviveReopeningTheActualFile() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "sello-preferences-${UUID.randomUUID()}.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = DataStoreUserPreferences(
                PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file })
            )
            assertEquals(PreferenceWrite.Saved, first.setTheme(ThemePreference.Dark))
            assertEquals(PreferenceWrite.Saved, first.setInk(InkPreference.Violeta))
            assertEquals(PreferenceWrite.Saved, first.setCategoryOrder(CategoryOrder.AmountSpent))
            assertEquals(
                PreferenceWrite.Saved,
                first.setCategoryOrderDirection(CategoryOrderDirection.Ascending)
            )
            assertEquals(PreferenceWrite.Saved, first.dismissPastExpenseAnnouncement())
            assertEquals(PreferenceWrite.Saved, first.dismissPastLimitAnnouncement())
        } finally {
            // Wait until the first DataStore is gone: opening the same file while it is
            // still closing fails with "multiple DataStores active for the same file".
            firstScope.coroutineContext.job.cancelAndJoin()
        }

        val reopenedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = DataStoreUserPreferences(
                PreferenceDataStoreFactory.create(scope = reopenedScope, produceFile = { file })
            )
            assertEquals(
                PreferenceRead.Ready(
                    AppearancePreference(ThemePreference.Dark, InkPreference.Violeta)
                ),
                reopened.observeAppearance().first()
            )
            assertEquals(
                PreferenceRead.Ready(
                    CategoryOrderingPreference(
                        CategoryOrder.AmountSpent,
                        CategoryOrderDirection.Ascending
                    )
                ),
                reopened.observeCategoryOrdering().first()
            )
            assertEquals(
                PreferenceRead.Ready(CorrectionAnnouncementPreference(true, true)),
                reopened.observeCorrectionAnnouncements().first()
            )
        } finally {
            reopenedScope.coroutineContext.job.cancelAndJoin()
            file.delete()
        }
    }
}
