package io.github.hiroto7.braketopause

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.preference.PreferenceManager
import io.github.hiroto7.braketopause.PlaybackControlService.MediaControlBinder

class PlaybackControlTileService : TileService() {
    private lateinit var intent: Intent
    private lateinit var sharedPreferences: SharedPreferences
    private var playbackControlService: PlaybackControlService? = null

    private val onMediaControlStartedListener = {
        qsTile.state = Tile.STATE_ACTIVE
        qsTile.updateTile()
    }

    private val onMediaControlStoppedListener = {
        qsTile.state = Tile.STATE_INACTIVE
        qsTile.updateTile()
    }

    override fun onCreate() {
        super.onCreate()
        intent = Intent(application, PlaybackControlService::class.java)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
    }

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MediaControlBinder
            playbackControlService = binder.service
            playbackControlService!!.addOnMediaControlStartedListener(onMediaControlStartedListener)
            playbackControlService!!.addOnMediaControlStoppedListener(onMediaControlStoppedListener)
            qsTile.state =
                if (playbackControlService!!.isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            qsTile.updateTile()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            playbackControlService!!.removeOnMediaControlStartedListener(
                onMediaControlStartedListener
            )
            playbackControlService!!.removeOnMediaControlStoppedListener(
                onMediaControlStoppedListener
            )
            playbackControlService = null
        }
    }

    override fun onStartListening() {
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    override fun onStopListening() = unbindService(connection)

    override fun onClick() {
        when (qsTile.state) {
            Tile.STATE_INACTIVE -> {
                if (sharedPreferences.getBoolean(getString(R.string.location_key), true) &&
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    sharedPreferences.getBoolean(
                        getString(R.string.activity_recognition_key),
                        true
                    ) &&
                    checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ) {
                    val mainIntent = Intent(this, MainActivity::class.java)
                    mainIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivityAndCollapse(mainIntent)
                } else {
                    startForegroundService(intent)
                }
            }
            Tile.STATE_ACTIVE -> {
                playbackControlService!!.stopMediaControl()
                stopService(intent)
            }
        }
        qsTile.updateTile()
    }
}