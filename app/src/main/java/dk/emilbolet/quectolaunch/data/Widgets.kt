package dk.emilbolet.quectolaunch.data

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.util.SizeF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One widget an app offers, as shown in the widget picker. */
class WidgetChoice(val info: AppWidgetProviderInfo, val label: String)

/** All the widgets of one app. */
class WidgetGroup(val appLabel: String, val widgets: List<WidgetChoice>)

/** A widget on its way to the home screen, while Android asks for permission or setup. */
class PendingWidget(
    val appWidgetId: Int,
    val info: AppWidgetProviderInfo,
    val page: Int,
    val spanX: Int,
    val spanY: Int,
)

/**
 * Everything that talks to the system about widgets: which exist, getting an ID for one,
 * binding and configuring it, and creating the view that shows it.
 */
class WidgetHost(private val context: Context) {

    private val manager = AppWidgetManager.getInstance(context)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val host = object : AppWidgetHost(context, HOST_ID) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?,
        ): AppWidgetHostView = LauncherWidgetHostView(context)
    }

    /** Widgets only update while this is on; call from the activity's onStart/onStop. */
    fun startListening() {
        try {
            host.startListening()
        } catch (e: Exception) {
            // Known to fail now and then with too many widgets; they update on the next start
        }
    }

    fun stopListening() {
        try {
            host.stopListening()
        } catch (e: Exception) {
            // Nothing to do
        }
    }

    /** Every installed widget, grouped by app and sorted by name. Call off the main thread. */
    fun available(): List<WidgetGroup> {
        val packageManager = context.packageManager
        return userManager.userProfiles
            .flatMap { manager.getInstalledProvidersForProfile(it) }
            .groupBy { it.provider.packageName }
            .map { (packageName, infos) ->
                WidgetGroup(
                    appLabel = appLabel(packageName, infos.first()),
                    widgets = infos
                        .map { WidgetChoice(it, it.loadLabel(packageManager)) }
                        .sortedBy { it.label.lowercase() },
                )
            }
            .sortedBy { it.appLabel.lowercase() }
    }

    private fun appLabel(packageName: String, info: AppWidgetProviderInfo): String {
        val packageManager = context.packageManager
        return try {
            val app = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                info.activityInfo.applicationInfo
            } else {
                packageManager.getApplicationInfo(packageName, 0)
            }
            app.loadLabel(packageManager).toString()
        } catch (e: Exception) {
            // Not visible to us; the widget's own name is the next best thing
            info.loadLabel(packageManager)
        }
    }

    /** The widget's preview picture, or its icon if it has none. Call off the main thread. */
    fun preview(info: AppWidgetProviderInfo): ImageBitmap? {
        return try {
            val drawable = info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0) ?: return null
            val width = drawable.intrinsicWidth.coerceIn(1, MAX_PREVIEW_PX)
            val height = if (drawable.intrinsicWidth > 0) {
                (width.toFloat() * drawable.intrinsicHeight / drawable.intrinsicWidth)
                    .roundToInt().coerceIn(1, MAX_PREVIEW_PX)
            } else {
                width
            }
            drawable.toBitmap(width, height).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    /** How many grid cells the widget wants, limited to the grid. */
    fun spanFor(
        info: AppWidgetProviderInfo,
        cellWidthPx: Float,
        cellHeightPx: Float,
        columns: Int,
        rows: Int,
    ): Pair<Int, Int> {
        var spanX = 0
        var spanY = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            spanX = info.targetCellWidth
            spanY = info.targetCellHeight
        }
        if (spanX <= 0 && cellWidthPx > 0f) spanX = ceil(info.minWidth / cellWidthPx).toInt()
        if (spanY <= 0 && cellHeightPx > 0f) spanY = ceil(info.minHeight / cellHeightPx).toInt()
        return spanX.coerceIn(1, columns) to spanY.coerceIn(1, rows)
    }

    fun info(appWidgetId: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(appWidgetId)

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(appWidgetId: Int) = host.deleteAppWidgetId(appWidgetId)

    /** Connects an ID to a widget. False means the user has to allow it first, with [bindIntent]. */
    fun bind(appWidgetId: Int, info: AppWidgetProviderInfo): Boolean =
        manager.bindAppWidgetIdIfAllowed(appWidgetId, info.profile, info.provider, null)

    /** Android's own "allow this app to create widgets?" dialog. */
    fun bindIntent(appWidgetId: Int, info: AppWidgetProviderInfo): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)

    /** Whether the widget has a setup screen that must be shown before it can be used. */
    fun needsConfigure(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val optional = AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL
            if (info.widgetFeatures and optional != 0) return false
        }
        return true
    }

    /** Opens the widget's setup screen; the answer arrives in the activity's onActivityResult. */
    fun startConfigure(activity: Activity, appWidgetId: Int, requestCode: Int) {
        host.startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, requestCode, null)
    }

    /** A view showing the widget. Each call makes a new one; the latest gets the updates. */
    fun createView(context: Context, appWidgetId: Int, info: AppWidgetProviderInfo): LauncherWidgetHostView =
        host.createView(context, appWidgetId, info) as LauncherWidgetHostView

    private companion object {
        const val HOST_ID = 1
        const val MAX_PREVIEW_PX = 600
    }
}

/**
 * Shows one widget. On top of the standard view it notices a long press anywhere on the
 * widget (even on its buttons) and reports it, so the launcher can show its menu or start a drag.
 */
@SuppressLint("ViewConstructor")
class LauncherWidgetHostView(context: Context) : AppWidgetHostView(context) {

    var onLongPress: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var longPressed = false
    private var lastSize: SizeF? = null

    private val longPress = Runnable {
        longPressed = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
    }

    // After a long press, take over the touch so the widget's own buttons don't fire as well
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        track(event)
        return longPressed
    }

    // Always claim the touch, or a long press on a widget without buttons would fall
    // through to the home screen behind it
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        track(event)
        return true
    }

    private fun track(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressed = false
                downX = event.x
                downY = event.y
                removeCallbacks(longPress)
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE ->
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                    removeCallbacks(longPress)
                }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPress)
        super.onDetachedFromWindow()
    }

    /** Tells the widget how big it is drawn (in dp), so it can pick a matching layout. */
    fun reportSize(widthDp: Float, heightDp: Float) {
        val size = SizeF(widthDp, heightDp)
        if (size == lastSize) return
        lastSize = size
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updateAppWidgetSize(Bundle(), listOf(size))
        } else {
            val width = widthDp.roundToInt()
            val height = heightDp.roundToInt()
            @Suppress("DEPRECATION")
            updateAppWidgetSize(null, width, height, width, height)
        }
    }
}
