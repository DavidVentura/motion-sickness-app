// SPDX-FileCopyrightText: 2026 David Ventura
// SPDX-License-Identifier: GPL-3.0-only

package dev.davidv.motionsickness.motion

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MotionTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        job?.cancel()
        job = scope.launch {
            // Collect the StateFlow to keep the tile synced even if toggled via notification
            MotionCuesService.isRunning.collect { isRunning ->
                qsTile?.apply {
                    state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    updateTile()
                }
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        job?.cancel()
        job = null
    }

    override fun onClick() {
        super.onClick()
        // Toggle the service based on current state
        if (MotionCuesService.isRunning.value) {
            MotionCuesService.stop(this)
        } else {
            MotionCuesService.start(this)
        }
    }
}
