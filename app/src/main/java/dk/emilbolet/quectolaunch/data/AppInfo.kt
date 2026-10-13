package dk.emilbolet.quectolaunch.data

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.os.UserHandle

/** One launchable app, as reported by LauncherApps. */
class AppInfo(
    val info: LauncherActivityInfo,
    val label: String,
    val isWorkProfile: Boolean,
) {
    val component: ComponentName get() = info.componentName
    val user: UserHandle get() = info.user
    val packageName: String get() = component.packageName

    val isSystemApp: Boolean
        get() = (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

    /** Stable ID across reloads; the same app can exist in both the personal and work profile. */
    val key: String = "${info.componentName.flattenToString()}#${info.user.hashCode()}"

    companion object {
        fun packageOfKey(key: String): String = key.substringBefore('/')

        fun keyHasUser(key: String, user: UserHandle): Boolean =
            key.substringAfterLast('#') == user.hashCode().toString()
    }
}

/** A letter heading in the app drawer and the apps under it. */
data class AppSection(val letter: String, val apps: List<AppInfo>)
