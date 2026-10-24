package com.example

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * Quick Settings TileService allowing the user to toggle the VoiceNotify Master Switch
 * ON or OFF directly from the Android notification shade without opening the app.
 */
@RequiresApi(Build.VERSION_CODES.N)
class QuickToggleTileService : TileService() {

    private lateinit var prefManager: PrefManager

    override fun onCreate() {
        super.onCreate()
        prefManager = PrefManager(applicationContext)
    }

    override fun onTileAdded() {
        super.onTileAdded()
        syncTileWithPreferences()
    }

    override fun onStartListening() {
        super.onStartListening()
        syncTileWithPreferences()
    }

    override fun onClick() {
        super.onClick()
        val newState = !prefManager.isMasterEnabled
        prefManager.isMasterEnabled = newState
        syncTileWithPreferences()
    }

    /**
     * Synchronizes the Quick Settings Tile state, label, subtitle, and icon with PrefManager.
     */
    private fun syncTileWithPreferences() {
        val tile = qsTile ?: return
        val isEnabled = prefManager.isMasterEnabled

        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_qs_voice_notify)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isEnabled) {
                getString(R.string.tile_subtitle_on)
            } else {
                getString(R.string.tile_subtitle_off)
            }
        }

        tile.updateTile()
    }
}
