package dk.emilbolet.quectolaunch.data

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dk.emilbolet.quectolaunch.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Everything that talks to the system about installed apps. */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    // Decoded icons, so scrolling the drawer doesn't redraw them from scratch
    private val iconCache = LruCache<String, ImageBitmap>(400)

    @Volatile
    private var iconPack: IconPack? = null

    /** Goes up whenever the icons change (new pack, app updated); icons on screen reload on it. */
    var iconVersion by mutableIntStateOf(0)
        private set

    // One size for every place an icon is shown, so the cache can be shared
    private val iconSizePx = (64 * context.resources.displayMetrics.density).toInt()

    /** All launchable apps in every profile (personal and work). Call off the main thread. */
    fun loadApps(): List<AppInfo> {
        val me = Process.myUserHandle()
        return userManager.userProfiles
            .flatMap { user ->
                launcherApps.getActivityList(null, user).map { info ->
                    AppInfo(
                        info = info,
                        label = info.label.toString().trim(),
                        isWorkProfile = user != me,
                    )
                }
            }
            .filter { it.packageName != context.packageName }
    }

    /** Emits whenever apps are installed, removed, updated, or a profile is paused/resumed. */
    fun changes(): Flow<AppChange> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) {
                trySend(AppChange(packageName, user))
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                trySend(AppChange(packageName, user, removed = true))
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                trySend(AppChange(packageName, user))
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) {
                trySend(AppChange())
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) {
                trySend(AppChange())
            }
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }

    /**
     * Whether the app behind an [AppInfo.key] is still installed. Used to drop home screen
     * items for apps that were uninstalled while the launcher wasn't running.
     */
    fun isInstalled(appKey: String): Boolean {
        val user = userManager.userProfiles.firstOrNull { AppInfo.keyHasUser(appKey, it) }
            ?: return false
        return try {
            launcherApps.getApplicationInfo(AppInfo.packageOfKey(appKey), 0, user) != null
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            true // can't tell (e.g. profile locked), so keep the item
        }
    }

    fun cachedIcon(app: AppInfo): ImageBitmap? = iconCache.get(app.key)

    /**
     * Loads an icon: from the icon pack if it has one for this app, otherwise the app's own.
     * Adds the work-profile badge if needed. Call off the main thread.
     */
    fun loadIcon(app: AppInfo): ImageBitmap {
        iconCache.get(app.key)?.let { return it }
        val version = iconVersion
        val themed = iconPack?.iconFor(app.component)
        val drawable = when {
            themed == null -> app.info.getBadgedIcon(0)
            app.isWorkProfile -> context.packageManager.getUserBadgedIcon(themed, app.user)
            else -> themed
        }
        val bitmap = drawable.toBitmap(iconSizePx, iconSizePx).asImageBitmap()
        // Don't keep it if the icons were reset while this one was loading
        if (version == iconVersion) iconCache.put(app.key, bitmap)
        return bitmap
    }

    /** Forgets all decoded icons; icons on screen load again. */
    fun clearIconCache() {
        iconCache.evictAll()
        iconVersion++
    }

    /** Apps that offer themselves as icon packs (ADW/Nova style). Call off the main thread. */
    fun iconPacks(): List<IconPackInfo> {
        val packageManager = context.packageManager
        return ICON_PACK_ACTIONS
            .flatMap { packageManager.queryIntentActivities(Intent(it), 0) }
            .distinctBy { it.activityInfo.packageName }
            .map { IconPackInfo(it.activityInfo.packageName, it.loadLabel(packageManager).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Switches to an icon pack, or back to the apps' own icons with null. Parses the pack,
     * so call off the main thread. An uninstalled or broken pack falls back to no pack.
     */
    fun setIconPack(packageName: String?) {
        iconPack = packageName?.let { IconPack.load(context, it) }
        clearIconCache()
    }

    /** Re-reads the current pack if [packageName] is it, e.g. after the pack was updated. */
    fun reloadIconPackIfChanged(packageName: String) {
        if (iconPack?.packageName == packageName) setIconPack(packageName)
    }

    fun launch(app: AppInfo) {
        try {
            launcherApps.startMainActivity(app.component, app.user, null, null)
        } catch (e: Exception) {
            // App was just uninstalled, disabled, or its work profile is paused
            Toast.makeText(context, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }

    fun openAppInfo(app: AppInfo) {
        try {
            launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * The app's long-press shortcuts ("New message", "Navigate home"...). Android only
     * gives these to the default launcher; otherwise the list is empty. Call off the main thread.
     */
    fun shortcuts(app: AppInfo): List<AppShortcut> {
        return try {
            if (!launcherApps.hasShortcutHostPermission()) return emptyList()
            val query = LauncherApps.ShortcutQuery()
                .setPackage(app.packageName)
                .setActivity(app.component)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                )
            launcherApps.getShortcuts(query, app.user).orEmpty()
                .filter { it.isEnabled }
                .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
                .take(MAX_SHORTCUTS)
                .mapNotNull { info ->
                    val label = (info.shortLabel ?: info.longLabel)?.toString()
                    if (label.isNullOrBlank()) null else AppShortcut(label, info)
                }
        } catch (e: Exception) {
            // Lost the default-launcher role in the meantime, or the profile is locked
            emptyList()
        }
    }

    fun startShortcut(shortcut: AppShortcut) {
        try {
            launcherApps.startShortcut(shortcut.info, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }

    fun uninstall(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun searchWeb(query: String) {
        val search = Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(search)
        } catch (e: ActivityNotFoundException) {
            // No search app installed; fall back to the browser
            val url = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
            context.startActivity(
                Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/** A change to the installed apps. [packageName] is null when several changed at once. */
data class AppChange(
    val packageName: String? = null,
    val user: UserHandle? = null,
    /** True when the package was uninstalled. */
    val removed: Boolean = false,
)

/** One entry in an app's long-press shortcut list. */
class AppShortcut(val label: String, val info: ShortcutInfo)

private const val MAX_SHORTCUTS = 4

private val ICON_PACK_ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME")
