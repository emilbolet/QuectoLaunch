package dk.emilbolet.quectolaunch.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import dk.emilbolet.quectolaunch.GestureService
import dk.emilbolet.quectolaunch.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The quick toggles in the expanded dock, and what the home screen gestures do.
 *
 * Only the flashlight can be switched directly. Since Android 10 apps can't turn Wi-Fi or
 * Bluetooth on and off, so those open the system's own panel or settings screen instead.
 */
class SystemControls(private val context: Context) {

    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private var torchCameraId: String? = null

    private val _torchOn = MutableStateFlow<Boolean?>(null)
    /** Whether the flashlight is on; null if the phone has none (or it's in use by the camera). */
    val torchOn: StateFlow<Boolean?> = _torchOn.asStateFlow()

    // Reports every camera that has a flash as soon as it's registered, and again on each change
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (torchCameraId == null) torchCameraId = cameraId
            if (cameraId == torchCameraId) _torchOn.value = enabled
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == torchCameraId) _torchOn.value = null
        }
    }

    init {
        cameraManager?.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
    }

    fun close() {
        cameraManager?.unregisterTorchCallback(torchCallback)
    }

    fun toggleTorch() {
        val id = torchCameraId ?: return
        val on = _torchOn.value ?: return
        try {
            cameraManager?.setTorchMode(id, !on)
        } catch (e: Exception) {
            // The camera app grabbed the flash in the meantime
        }
    }

    fun openWifi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            open(Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS)
        } else {
            open(Settings.ACTION_WIFI_SETTINGS)
        }
    }

    fun openBluetooth() = open(Settings.ACTION_BLUETOOTH_SETTINGS)

    fun openSound() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            open(Settings.Panel.ACTION_VOLUME, Settings.ACTION_SOUND_SETTINGS)
        } else {
            open(Settings.ACTION_SOUND_SETTINGS)
        }
    }

    fun openSettings() = open(Settings.ACTION_SETTINGS)

    /** Whether the user has turned on the accessibility service the gestures need. Compose state. */
    val hasGestureAccess: Boolean
        get() = GestureService.instance != null

    val canLockScreen: Boolean
        get() = GestureService.canLock

    fun openAccessibilitySettings() = open(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun lockScreen() {
        if (GestureService.canLock && !GestureService.lockScreen()) explainMissingAccess()
    }

    fun openNotifications() {
        if (!GestureService.openNotifications()) explainMissingAccess()
    }

    private fun explainMissingAccess() {
        Toast.makeText(context, R.string.gestures_need_access, Toast.LENGTH_LONG).show()
    }

    /** Tries each action in turn; some phones leave out the quick panels. */
    private fun open(vararg actions: String) {
        for (action in actions) {
            try {
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                continue
            }
        }
        Toast.makeText(context, R.string.cant_open, Toast.LENGTH_SHORT).show()
    }
}
