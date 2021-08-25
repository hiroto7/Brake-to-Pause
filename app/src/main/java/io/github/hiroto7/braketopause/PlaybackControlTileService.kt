package io.github.hiroto7.braketopause

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.hiroto7.braketopause.PlaybackControlService.MediaControlBinder

class PlaybackControlTileService : TileService() {
    private lateinit var intent: Intent
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
                startForegroundService(intent)
            }
            Tile.STATE_ACTIVE -> {
                playbackControlService!!.stopMediaControl()
                stopService(intent)
            }
        }
        qsTile.updateTile()
    }
}