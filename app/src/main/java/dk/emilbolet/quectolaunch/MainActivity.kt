package dk.emilbolet.quectolaunch

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.emilbolet.quectolaunch.data.PendingWidget
import dk.emilbolet.quectolaunch.ui.LauncherApp
import dk.emilbolet.quectolaunch.ui.theme.LauncherTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars, with light icons over the wallpaper
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            // Nothing but wallpaper until the saved theme is known, so it never flashes the wrong one
            val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return@setContent
            LauncherTheme(settings) {
                LauncherApp(viewModel, settings, onAddWidget = ::addWidget)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.widgets.startListening()
    }

    /**
     * Adds a widget in up to three steps: ask Android to let us show it (the first time
     * this brings up a system dialog), open the widget's own setup screen if it has one,
     * then put it on the home screen.
     */
    private fun addWidget(pending: PendingWidget) {
        val widgets = viewModel.widgets
        viewModel.pendingWidget = pending
        if (widgets.bind(pending.appWidgetId, pending.info)) {
            configurePendingWidget()
        } else {
            startForResult(REQUEST_BIND_WIDGET) {
                @Suppress("DEPRECATION")
                startActivityForResult(
                    widgets.bindIntent(pending.appWidgetId, pending.info),
                    REQUEST_BIND_WIDGET,
                )
            }
        }
    }

    private fun configurePendingWidget() {
        val pending = viewModel.pendingWidget ?: return
        if (viewModel.widgets.needsConfigure(pending.info)) {
            startForResult(REQUEST_CONFIGURE_WIDGET) {
                viewModel.widgets.startConfigure(this, pending.appWidgetId, REQUEST_CONFIGURE_WIDGET)
            }
        } else {
            viewModel.placePendingWidget()
        }
    }

    /** Runs [start]; if the other app can't be opened, the widget is given up. */
    private fun startForResult(requestCode: Int, start: () -> Unit) {
        try {
            start()
        } catch (e: Exception) {
            onWidgetResult(requestCode, RESULT_CANCELED)
        }
    }

    // The widget host can only start a setup screen the old way, so results arrive here
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        onWidgetResult(requestCode, resultCode)
    }

    private fun onWidgetResult(requestCode: Int, resultCode: Int) {
        when {
            requestCode != REQUEST_BIND_WIDGET && requestCode != REQUEST_CONFIGURE_WIDGET -> return
            resultCode != RESULT_OK -> viewModel.cancelPendingWidget()
            requestCode == REQUEST_BIND_WIDGET -> configurePendingWidget()
            else -> viewModel.placePendingWidget()
        }
    }

    // Pressing Home while we're already in front arrives here
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.goHome(toFirstPage = true)
        }
    }

    // When the user comes back from an app, start on the home screen again (same page)
    override fun onStop() {
        super.onStop()
        viewModel.widgets.stopListening()
        viewModel.goHome()
    }

    private companion object {
        const val REQUEST_BIND_WIDGET = 1
        const val REQUEST_CONFIGURE_WIDGET = 2
    }
}
