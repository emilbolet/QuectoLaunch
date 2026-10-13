package dk.emilbolet.quectolaunch.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import dk.emilbolet.quectolaunch.GestureService
import dk.emilbolet.quectolaunch.R

/** What the home screen gestures do, and the way to the accessibility settings they need. */
class SystemControls(private val context: Context) {

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

    private fun open(action: String) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }
}
