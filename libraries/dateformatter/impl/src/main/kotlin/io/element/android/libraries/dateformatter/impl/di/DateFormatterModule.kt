/*
 * Copyright 2023, 2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.dateformatter.impl.di

import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import io.element.android.libraries.dateformatter.impl.TimezoneProvider
import io.element.android.libraries.di.AppScope
import kotlinx.datetime.TimeZone
import java.util.Locale
import kotlin.time.Clock

@Module
@ContributesTo(AppScope::class)
object DateFormatterModule {
    @Provides
    fun providesClock(): Clock = Clock.System

    @Provides
    fun providesLocale(): Locale = Locale.getDefault()

    @Provides
    fun providesTimezone(): TimezoneProvider = TimezoneProvider { TimeZone.currentSystemDefault() }
}
