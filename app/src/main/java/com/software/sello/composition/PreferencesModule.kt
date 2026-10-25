package com.software.sello.composition

import com.software.sello.data.StorageNames
import com.software.sello.data.preferences.DataStoreUserPreferences
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.BackupRecords
import com.software.sello.domain.port.CatchUpNoticePreferences
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.CorrectionAnnouncementPreferences
import com.software.sello.domain.port.PreferenceReset
import com.software.sello.domain.port.SuggestionPreferences
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The composition-only bindings for the nonfinancial preference adapter, stored in
 * [fileName]: the profile's own preferences file.
 */
fun preferencesModule(fileName: String = StorageNames.Normal.preferences): Module = module {
    single { DataStoreUserPreferences(get(), get<CoroutineScope>(), fileName) }
    single<AppearancePreferences> { get<DataStoreUserPreferences>() }
    single<CategoryOrderingPreferences> { get<DataStoreUserPreferences>() }
    single<CorrectionAnnouncementPreferences> { get<DataStoreUserPreferences>() }
    single<SuggestionPreferences> { get<DataStoreUserPreferences>() }
    single<CatchUpNoticePreferences> { get<DataStoreUserPreferences>() }
    single<BackupRecords> { get<DataStoreUserPreferences>() }
    single<PreferenceReset> { get<DataStoreUserPreferences>() }
}
