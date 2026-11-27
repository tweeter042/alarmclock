package io.github.tweeter042.alarmclock.core.data.datastore

import io.github.tweeter042.alarmclock.core.model.entity.StopwatchState
import io.github.tweeter042.alarmclock.core.model.repository.SettingsRepository
import io.github.tweeter042.alarmclock.core.model.repository.StopwatchRepository
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import kotlinx.coroutines.flow.Flow

/** [AppSettings] as one JSON document (`settings.json`, device-protected storage). */
internal class DataStoreSettingsRepository(
    private val document: JsonDocumentStore<AppSettings>,
) : SettingsRepository {
    override val settings: Flow<AppSettings> get() = document.data

    override suspend fun current(): AppSettings = document.current()

    /** @throws java.io.IOException if the settings file cannot be written. */
    override suspend fun update(transform: (AppSettings) -> AppSettings) = document.update(transform)
}

/** [StopwatchState] as one JSON document (`stopwatch.json`, device-protected storage). */
internal class DataStoreStopwatchRepository(
    private val document: JsonDocumentStore<StopwatchState>,
) : StopwatchRepository {
    override val state: Flow<StopwatchState> get() = document.data

    override suspend fun current(): StopwatchState = document.current()

    /** @throws java.io.IOException if the stopwatch file cannot be written. */
    override suspend fun update(transform: (StopwatchState) -> StopwatchState) = document.update(transform)
}
