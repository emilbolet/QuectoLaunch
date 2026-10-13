package dk.emilbolet.quectolaunch

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Lets the home screen gestures lock the phone and open the notification shade.
 *
 * Android only allows these two actions from an accessibility service. This one does
 * nothing else: it asks for no events and cannot read what is on screen (see
 * res/xml/gesture_service.xml). The user turns it on in system settings, after the
 * explanation shown in launcher settings.
 */
class GestureService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        /** The running service, or null while the user hasn't turned it on. Compose state. */
        var instance by mutableStateOf<GestureService?>(null)
            private set

        /** Locking this way keeps fingerprint and face unlock working; needs Android 9. */
        val canLock: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        fun lockScreen(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
        }

        fun openNotifications(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true
    }
}
