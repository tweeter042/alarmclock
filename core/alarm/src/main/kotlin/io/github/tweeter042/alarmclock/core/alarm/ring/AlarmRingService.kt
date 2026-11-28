package io.github.tweeter042.alarmclock.core.alarm.ring

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.tweeter042.alarmclock.core.alarm.DefaultAlarmEngine
import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.alarm.SystemEngineClock
import io.github.tweeter042.alarmclock.core.alarm.alarmEngineOrNull
import io.github.tweeter042.alarmclock.core.alarm.engine.RingCoordinator
import io.github.tweeter042.alarmclock.core.alarm.internal.WakeLocks
import io.github.tweeter042.alarmclock.core.alarm.notify.EngineNotifier
import io.github.tweeter042.alarmclock.core.alarm.notify.NotificationIds
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerIntents
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerPayload
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.engine.RingUiLauncher
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * The foreground service that plays alarms (directBootAware, default process).
 *
 * - Goes foreground IMMEDIATELY in onStartCommand (type systemExempted, falling back to
 *   mediaPlayback) with the ringing notification: category ALARM, channel engine_alarms, full-screen
 *   intent → the ring screen. For a new ring the ring id is generated here, before the ring exists.
 * - Hands the trigger to the engine, then mirrors the engine's ring state: while a ring is sounding
 *   it runs a [RingSession] for it (audio, vibration, wake lock, gestures); when nothing sounds (and
 *   no trigger or briefing is pending) it restores the alarm volume and stops.
 * - START_STICKY: if the process dies mid-ring the system restarts it and the engine restores the ring.
 */
class AlarmRingService : Service() {

    private val log = EngineLog.ring
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: DefaultAlarmEngine? = null

    private var session: RingSession? = null
    private var foregroundId: Int? = null
    private var notificationKey: Any? = null
    private val generations = HashMap<String, Int>()
    private var watchSilentRingId: String? = null
    private var pendingTriggers = 0
    private var lastStartId = 0
    private var speechJob: Job? = null
    private var ringsRestored = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        val engine = applicationContext.alarmEngineOrNull()
        this.engine = engine
        if (engine == null) {
            log.e("The Application does not implement AlarmEngineHost; cannot ring")
            return
        }
        engine.start()
        scope.launch {
            engine.awaitRingsRestored()
            ringsRestored = true
            reevaluate()
        }
        scope.launch { engine.ringBook.collect { onBook() } }
        scope.launch { engine.briefingRequest.collect { request -> if (request != null) startBriefing(request) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val engine = engine
        val payload = if (intent?.action == ACTION_TRIGGER) TriggerIntents.parse(intent) else null
        val preRingId = when {
            payload == null -> null
            payload.createsRing -> RingCoordinator.newRingId()
            else -> payload.ringId
        }
        goForeground(payload, preRingId)
        if (engine == null) {
            WakeLocks.releaseStartup()
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (payload != null) {
            pendingTriggers++
            scope.launch {
                try {
                    withContext(Dispatchers.Default) { engine.onRingTrigger(payload, preRingId) }
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    log.e("Ring trigger failed", t)
                } finally {
                    pendingTriggers--
                    WakeLocks.releaseStartup()
                    onBook()
                }
            }
        } else {
            WakeLocks.releaseStartup()
            onBook()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        session?.stop()
        session = null
        speechJob?.cancel()
        scope.cancel()
        engine?.let { engine ->
            if (engine.ringBook.value.sounding == null) engine.volume.restore()
        }
        WakeLocks.releaseStartup()
        super.onDestroy()
    }

    // --- foreground -----------------------------------------------------------------------------

    /** startForegroundService() requires startForeground() within seconds, whatever else fails. */
    private fun goForeground(payload: TriggerPayload?, preRingId: String?) {
        val engine = engine
        if (engine == null) {
            startForegroundSafely(NotificationIds.ring("unavailable"), fallbackNotification(payload, preRingId))
            return
        }
        try {
            val sounding = engine.ringBook.value.sounding
            val briefing = engine.briefingRequest.value
            when {
                sounding != null -> showRing(sounding, force = true)
                briefing != null && payload == null -> {
                    notificationKey = null
                    startForegroundSafely(NotificationIds.BRIEFING, engine.notifier.briefing(engine.settingsSnapshot()))
                }
                else -> {
                    notificationKey = null
                    val notification = engine.notifier.starting(payload, preRingId, engine.settingsSnapshot())
                    startForegroundSafely(NotificationIds.ring(preRingId ?: "starting"), notification)
                }
            }
        } catch (t: Throwable) {
            log.e("Ring notification could not be built; using the fallback notification", t)
            startForegroundSafely(NotificationIds.ring(preRingId ?: "starting"), fallbackNotification(payload, preRingId))
        }
    }

    /** Only used if the app is misconfigured (no AlarmEngineHost): every screen intent opens the launcher activity. */
    private fun fallbackNotification(payload: TriggerPayload?, preRingId: String?): Notification {
        val launcher = object : RingUiLauncher {
            override fun ringScreenIntent(context: Context, ringId: String) = launchIntent()
            override fun sunriseIntent(context: Context, alarmId: Long, occurrenceAt: Long) = launchIntent()
            override fun mainIntent(context: Context) = launchIntent()
            override fun alarmEditorIntent(context: Context, alarmId: Long) = launchIntent()
        }
        return EngineNotifier(this, launcher, SystemEngineClock).starting(payload, preRingId, AppSettings())
    }

    private fun launchIntent(): Intent =
        packageManager.getLaunchIntentForPackage(packageName) ?: Intent(Intent.ACTION_MAIN).setPackage(packageName)

    private fun startForegroundSafely(id: Int, notification: Notification) {
        val previous = foregroundId
        try {
            ServiceCompat.startForeground(this, id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } catch (t: Throwable) {
            log.w("systemExempted foreground refused; using mediaPlayback", t)
            try {
                ServiceCompat.startForeground(this, id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } catch (t2: Throwable) {
                log.e("Could not go foreground; posting the alarm notification instead", t2)
                engine?.notifier?.postRing(id, notification)
            }
        }
        foregroundId = id
        if (previous != null && previous != id) engine?.notifier?.cancelRing(previous)
    }

    /** Posts / updates the foreground notification of [ring] (a new id re-fires the full-screen intent). */
    private fun showRing(ring: ActiveRing, force: Boolean = false) {
        val engine = engine ?: return
        val generation = generations[ring.ringId] ?: 0
        val watchSilent = watchSilentRingId == ring.ringId
        val key = listOf(ring.ringId, ring.state, ring.currentMissionIndex, ring.awakePromptShowing, ring.snoozeCount, watchSilent, generation)
        if (!force && key == notificationKey) return
        notificationKey = key
        val notification = try {
            engine.notifier.ring(ring, engine.settingsSnapshot(), watchSilent)
        } catch (t: Throwable) {
            log.e("Ring notification could not be built; using the fallback notification", t)
            fallbackNotification(null, ring.ringId)
        }
        startForegroundSafely(NotificationIds.ring(ring.ringId, generation), notification)
    }

    // --- ring state -----------------------------------------------------------------------------

    private fun onBook() {
        val engine = engine ?: return
        val ring = engine.ringBook.value.sounding
        val mode = ring?.soundMode
        if (ring == null || mode == null) {
            stopSession()
            engine.briefingRequest.value?.let(::startBriefing)
            reevaluate()
            return
        }
        if (speechJob?.isActive == true) {
            // A ring is sounding again: the after-dismiss briefing stops.
            speechJob?.cancel()
            engine.env.deps.speaker?.stop()
        }
        val current = session
        if (current == null || current.ring.ringId != ring.ringId || current.mode != mode || current.ring.startedAt != ring.startedAt) {
            current?.stop()
            watchSilentRingId = null
            session = RingSession(this, ring, mode, engine, engine.settingsSnapshot(), sessionHost).also { it.start() }
            log.i("Sounding a ${ring.kind} ring ($mode)")
        } else {
            current.update(ring)
        }
        showRing(ring)
    }

    private fun stopSession() {
        session?.let {
            it.stop()
            log.i("Ring session ended")
        }
        session = null
    }

    private val sessionHost = object : RingSession.Host {
        override fun onWatchSilent(ringId: String, silent: Boolean) {
            watchSilentRingId = if (silent) ringId else null
            engine?.ringBook?.value?.sounding?.takeIf { it.ringId == ringId }?.let { showRing(it) }
        }

        override fun reannounce(ringId: String) {
            generations[ringId] = (generations[ringId] ?: 0) + 1
            engine?.ringBook?.value?.sounding?.takeIf { it.ringId == ringId }?.let { showRing(it) }
        }
    }

    /** Stops the service once nothing sounds and no trigger or briefing is pending. */
    private fun reevaluate() {
        val engine = engine ?: return
        if (!ringsRestored || session != null || pendingTriggers > 0 || speechJob?.isActive == true) return
        if (engine.ringBook.value.sounding != null || engine.briefingRequest.value != null) return
        engine.volume.restore()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foregroundId?.let { engine.notifier.cancelRing(it) }
        foregroundId = null
        notificationKey = null
        generations.clear()
        stopSelfResult(lastStartId)
    }

    // --- after-dismiss briefing -----------------------------------------------------------------

    private fun startBriefing(ring: ActiveRing) {
        val engine = engine ?: return
        engine.consumeBriefing(ring.ringId)
        if (speechJob?.isActive == true || session != null) return
        val speaker = engine.env.deps.speaker ?: return
        val composer = engine.env.deps.briefing ?: return
        val settings = engine.settingsSnapshot()
        notificationKey = null
        startForegroundSafely(NotificationIds.BRIEFING, engine.notifier.briefing(settings))
        speechJob = scope.launch {
            try {
                withTimeoutOrNull(AFTER_DISMISS_BRIEFING_MILLIS) {
                    val language = settings.region.language.tag
                    val text = withContext(Dispatchers.IO) { composer.compose(ring.alarm, engine.env.clock.now(), language).spokenText }
                    if (text.isNotBlank()) speaker.speak(text, language, ring.alarm.briefing.speechRatePercent, useAlarmStream = true)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                log.w("After-dismiss briefing failed", t)
            } finally {
                speechJob = null
                scope.launch { reevaluate() }
            }
        }
    }

    internal companion object {
        /** True while the service exists (same process). */
        @Volatile
        var isRunning: Boolean = false
            private set

        private const val ACTION_TRIGGER = "io.github.tweeter042.alarmclock.engine.service.TRIGGER"
        private const val ACTION_ENSURE = "io.github.tweeter042.alarmclock.engine.service.ENSURE"
        private const val ACTION_BRIEFING = "io.github.tweeter042.alarmclock.engine.service.BRIEFING"
        private const val AFTER_DISMISS_BRIEFING_MILLIS = 90_000L

        /** From AlarmTriggerReceiver: a ring trigger fired. Returns false if Android refused the start. */
        fun startForTrigger(context: Context, payload: TriggerPayload): Boolean =
            startService(context, Intent(context, AlarmRingService::class.java).setAction(ACTION_TRIGGER).also { TriggerIntents.put(it, payload) })

        /** The engine has a ring to sound and the service is not running. */
        fun start(context: Context, reason: String): Boolean {
            EngineLog.ring.i("Starting the ring service ($reason)")
            return startService(context, Intent(context, AlarmRingService::class.java).setAction(ACTION_ENSURE))
        }

        fun startForBriefing(context: Context): Boolean =
            isRunning || startService(context, Intent(context, AlarmRingService::class.java).setAction(ACTION_BRIEFING))

        private fun startService(context: Context, intent: Intent): Boolean = try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (t: Throwable) {
            // ForegroundServiceStartNotAllowedException (background start) or IllegalStateException.
            EngineLog.ring.e("The ring service could not be started", t)
            false
        }
    }
}
