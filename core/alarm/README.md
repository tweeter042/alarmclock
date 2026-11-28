# core:alarm — the alarm engine

Everything between "an alarm is saved" and "the phone is ringing": AlarmManager scheduling, the
ring state machine, the foreground ring service (audio, vibration, gestures), notifications, the
reliability checker, the guard service and the watchdog. Reliability first: an alarm that does not
ring is a total failure, so every integration call is bounded by a timeout and a catch-all.

## Wiring (:app)

```kotlin
class AlarmClockApplication : Application(), AlarmEngineHost, AppGraphProvider {
    val data by lazy { DataGraph(this, BuildConfig.VERSION_NAME) }
    override val alarmEngine by lazy {
        DefaultAlarmEngine(this, data, EngineDependencies(hooks, ringUi, buildInfo, speaker, briefing, weather, holidays, calendar, wear, music))
    }
    override fun onCreate() {
        super.onCreate()
        alarmEngine.start()              // also runs in Direct Boot; returns immediately
    }
}
// AppGraph: engine = alarmEngine, reliability = DefaultReliabilityChecker(this, data, alarmEngine, wear)
```

- One engine per process; every component runs in the default process (no `android:process`).
- `ringUi.ringScreenIntent` should be an explicit intent to the ring activity (`showWhenLocked`,
  `turnScreenOn`); the engine adds `FLAG_ACTIVITY_NEW_TASK`.
- WorkManager: this module's manifest removes WorkManager's startup initializer (it crashes in
  Direct Boot). The watchdog initializes WorkManager on demand after unlock with a default
  configuration; implement `androidx.work.Configuration.Provider` in the Application if other
  modules use WorkManager too.
- Optional dependencies may be null; the engine then skips that feature.

## For the ring screen (feature:ringing)

- Show `engine.activeRing`. A full-screen intent can arrive a few milliseconds before the ring
  with that id is published: wait (≈5 s) for it; if another ring is current, show that one; if
  nothing appears, finish. After a dismiss, a queued ring may become current: keep showing it.
- States: RINGING, MISSION (call `onMissionInteraction` on every touch / sensor progress — it keeps
  the volume ducked), SNOOZED (`snoozedUntil`), AWAKE_CHECK with `awakePromptShowing` ("Are you
  awake?" → `confirmAwake`).
- Missions: `startMission` → `completeMission` per mission; the last one dismisses. With
  `SnoozeMode.MISSION_TO_SNOOZE`, `snooze()` returns false and remembers the request: run the
  missions and the last `completeMission` snoozes instead. `startMission` also works on a SNOOZED
  ring (dismissing a snoozed alarm that has missions). `abandonMission` when the screen is left.
- `dismiss()` always dismisses; enforce missions in the UI. Volume keys: call `snooze`/`dismiss`.

## Timers (feature:clock)

Keep the timer RUNNING with `endAt` in the repository and call `scheduleTimer(id, endAt)`; call
`cancelTimer(id)` on pause/reset/delete. While it rings the engine sets RINGING; "+1 minute"
(snooze) restarts it, dismiss resets it to IDLE.

## Other callers

- `triggerNow(LOCATION, locationAlarmId, template)` for geofences (one-shot location alarms are
  disabled when they ring); `triggerNow(ALARM, alarmId, alarm)` for smart wake — an occurrence up
  to 2 h ahead is taken over, so the regular trigger does not ring again.
- Watch actions: `snooze(ringId)`, `dismiss(ringId, DISMISSED_ON_WATCH)`.
- `AlarmEngineHooks.onPreAlarm` receives every pre-alarm kind (upcoming notice, sunrise, smart
  wake, weather check, prefetch); `onRingUpdated` every state change; `onRingFinished` exactly once.

## Design notes

- `schedule/TriggerPlanner` (pure): repositories + rings → every AlarmManager registration.
  `schedule/RequestCodes` documents the request-code scheme; `ScheduleLedger` records them all.
- `ring/RingReducer` (pure): the state machine, with queueing, mission-to-snooze, awake check,
  timer snooze and auto actions; `engine/RingCoordinator` persists it (RingStateStore + journal),
  runs the effects and arms the ring timers.
- Missed alarms, force stops and lost triggers are handled at start (`schedule/MissedAlarmPolicy`,
  boot count) and repaired by the integrity check (watchdog, foreground, boot, time changes).
