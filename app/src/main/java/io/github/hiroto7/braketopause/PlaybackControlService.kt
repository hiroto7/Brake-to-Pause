package io.github.hiroto7.braketopause

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.location.*
import java.util.*
import java.util.stream.Collectors

class PlaybackControlService : Service(), OnAudioFocusChangeListener {
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setOnAudioFocusChangeListener(this)
        .build()
    private val binder: IBinder = MediaControlBinder()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    var isEnabled = false
        private set
    private var usesLocation = false
    private var usesActivityRecognition = false
    private var hasAudioFocus = false
    private var timerInProgress = false
    private lateinit var mainPendingIntent: PendingIntent
    private lateinit var controllingPlaybackNotification: Notification
    private lateinit var playbackPausedNotification: Notification
    private val locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            val location = locationResult.lastLocation
            if (!location.hasSpeed()) {
                return
            }
            val speedThresholdKph =
                sharedPreferences.getInt(getString(R.string.speed_threshold_key), 8)
            val lastSpeedMps = location.speed
            val lastSpeedKph = 3.6f * lastSpeedMps
            if (lastSpeedKph < speedThresholdKph) {
                pausePlayback()
            } else {
                resumePlayback()
            }
        }
    }
    private val transitionsReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!ActivityTransitionResult.hasResult(intent)) {
                return
            }

            val selectedActivities = LinkedList<Int>().apply {
                if (sharedPreferences.getBoolean(getString(R.string.in_vehicle_key), true)) {
                    add(DetectedActivity.IN_VEHICLE)
                }
                if (sharedPreferences.getBoolean(getString(R.string.on_bicycle_key), true)) {
                    add(DetectedActivity.ON_BICYCLE)
                }
                if (sharedPreferences.getBoolean(getString(R.string.running_key), true)) {
                    add(DetectedActivity.RUNNING)
                }
                if (sharedPreferences.getBoolean(getString(R.string.walking_key), true)) {
                    add(DetectedActivity.WALKING)
                }
            }

            val result = ActivityTransitionResult.extractResult(intent)!!
            for (event in result.transitionEvents) {
                if (selectedActivities.contains(event.activityType) && event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                    if (usesLocation) {
                        requestLocationUpdates()
                    } else {
                        resumePlayback()
                    }
                } else {
                    if (usesLocation) {
                        removeLocationUpdates()
                    }
                    pausePlayback()
                }
            }
        }
    }
    private lateinit var transitionPendingIntent: PendingIntent
    private val stopReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            stopMediaControl()
            stopSelf()
        }
    }
    private val stopMediaControlWithNotificationCallback = Runnable {
        stopMediaControl()
        val notification = NotificationCompat.Builder(this, AUTOMATIC_STOP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_stop_24)
            .setContentTitle(getString(R.string.playback_control_automatically_ended))
            .setContentText(getString(R.string.time_has_passed_with_media_paused))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(mainPendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startTimer() {
        handler.postDelayed(stopMediaControlWithNotificationCallback, DELAY_MILLIS.toLong())
        timerInProgress = true
    }

    private fun stopTimer() {
        handler.removeCallbacks(stopMediaControlWithNotificationCallback)
        timerInProgress = false
    }

    private fun pausePlayback() {
        if (!hasAudioFocus) {
            audioManager.requestAudioFocus(focusRequest)
            hasAudioFocus = true
            notificationManager.notify(NOTIFICATION_ID, playbackPausedNotification)
        }
        if (!timerInProgress) {
            startTimer()
        }
    }

    private fun resumePlayback() {
        if (hasAudioFocus) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            hasAudioFocus = false
            notificationManager.notify(NOTIFICATION_ID, controllingPlaybackNotification)
        }
        if (timerInProgress) {
            stopTimer()
        }
    }

    private fun requestLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException()
        }
        val locationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(1000)
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun removeLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    private val onMediaControlStartedListeners: MutableSet<Runnable> = HashSet()
    private fun requestActivityTransitionUpdates() {
        registerReceiver(transitionsReceiver, IntentFilter(ACTION_TRANSITION))
        val activityTypes = listOf(
            DetectedActivity.STILL,
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.RUNNING,
            DetectedActivity.WALKING
        )
        val transitions = activityTypes.stream()
            .map { activityType: Int ->
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build()
            }
            .collect(Collectors.toList())
        val request = ActivityTransitionRequest(transitions)
        val task = activityRecognitionClient.requestActivityTransitionUpdates(
            request,
            transitionPendingIntent
        )
    }

    private fun removeActivityTransitionUpdates() {
        unregisterReceiver(transitionsReceiver)
        activityRecognitionClient.removeActivityTransitionUpdates(transitionPendingIntent)
    }

    private val onMediaControlStoppedListeners: MutableSet<Runnable> = HashSet()
    private fun createNotificationChannel() {
        val channels = listOf(
            NotificationChannel(
                PLAYBACK_CONTROL_CHANNEL_ID,
                getString(R.string.playback_control),
                NotificationManager.IMPORTANCE_LOW
            ),
            NotificationChannel(
                AUTOMATIC_STOP_CHANNEL_ID,
                getString(R.string.automatic_exit),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        notificationManager.createNotificationChannels(channels)
    }

    fun stopMediaControl() {
        unregisterReceiver(stopReceiver)
        stopForeground(true)
        if (timerInProgress) {
            stopTimer()
        }
        if (usesLocation) {
            removeLocationUpdates()
        }
        if (usesActivityRecognition) {
            removeActivityTransitionUpdates()
        }
        isEnabled = false
        onMediaControlStoppedListeners.forEach(Runnable::run)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (hasAudioFocus) {
            val lastingFocusRequest =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).build()
            audioManager.requestAudioFocus(lastingFocusRequest)
        }
        if (isEnabled) {
            stopMediaControl()
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onAudioFocusChange(focusChange: Int) {
        if (focusChange != AudioManager.AUDIOFOCUS_LOSS) {
            return
        }
        hasAudioFocus = false
        if (!isEnabled) {
            return
        }
        notificationManager.notify(NOTIFICATION_ID, controllingPlaybackNotification)
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        notificationManager = getSystemService(NotificationManager::class.java)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        activityRecognitionClient = ActivityRecognition.getClient(this)
        val transitionIntent = Intent(ACTION_TRANSITION)
        transitionPendingIntent = PendingIntent.getBroadcast(
            this, 0, transitionIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        )
        val mainIntent = Intent(this, MainActivity::class.java)
        mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(ACTION_STOP_MEDIA_CONTROL)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationBuilder = NotificationCompat.Builder(this, PLAYBACK_CONTROL_CHANNEL_ID)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(mainPendingIntent)
            .setColorized(true)
            .setColor(getColor(R.color.orange_500))
            .addAction(
                R.drawable.ic_baseline_stop_24,
                getString(R.string.finish_playback_control),
                stopPendingIntent
            )
        controllingPlaybackNotification = notificationBuilder
            .setContentTitle(getString(R.string.controlling_playback_state))
            .setSmallIcon(R.drawable.ic_baseline_pause_circle_outline_24)
            .build()
        playbackPausedNotification = notificationBuilder
            .setContentTitle(getText(R.string.paused_media))
            .setSmallIcon(R.drawable.ic_baseline_pause_24)
            .build()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            if (hasAudioFocus) playbackPausedNotification else controllingPlaybackNotification
        )
        startTimer()
        registerReceiver(stopReceiver, IntentFilter(ACTION_STOP_MEDIA_CONTROL))
        usesLocation = sharedPreferences.getBoolean(getString(R.string.location_key), true)
        usesActivityRecognition =
            sharedPreferences.getBoolean(getString(R.string.activity_recognition_key), true)
        if (usesLocation) {
            requestLocationUpdates()
        }
        if (usesActivityRecognition) {
            requestActivityTransitionUpdates()
        }
        isEnabled = true
        onMediaControlStartedListeners.forEach(Runnable::run)
        return START_STICKY
    }

    fun addOnMediaControlStartedListener(onMediaControlStartedListener: Runnable) {
        onMediaControlStartedListeners.add(onMediaControlStartedListener)
    }

    fun removeOnMediaControlStartedListener(onMediaControlStartedListener: Runnable) {
        onMediaControlStartedListeners.remove(onMediaControlStartedListener)
    }

    fun addOnMediaControlStoppedListener(onMediaControlStoppedListener: Runnable) {
        onMediaControlStoppedListeners.add(onMediaControlStoppedListener)
    }

    fun removeOnMediaControlStoppedListener(onMediaControlStoppedListener: Runnable) {
        onMediaControlStoppedListeners.remove(onMediaControlStoppedListener)
    }

    inner class MediaControlBinder : Binder() {
        val service: PlaybackControlService
            get() = this@PlaybackControlService
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val DELAY_MILLIS = 60 * 30 * 1000
        private val TAG = PlaybackControlService::class.java.simpleName
        private val ACTION_TRANSITION =
            PlaybackControlService::class.java.canonicalName!! + ".ACTION_TRANSITION"
        private val ACTION_STOP_MEDIA_CONTROL =
            PlaybackControlService::class.java.canonicalName!! + ".ACTION_STOP_MEDIA_CONTROL"
        private const val PLAYBACK_CONTROL_CHANNEL_ID = "playback_control"
        private const val AUTOMATIC_STOP_CHANNEL_ID = "automatic_stop"
    }
}