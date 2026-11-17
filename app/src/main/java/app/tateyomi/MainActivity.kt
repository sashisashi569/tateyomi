package app.tateyomi

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.tateyomi.ui.MainViewModel
import app.tateyomi.ui.TateyomiAppRoot

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val model = viewModel
        setContent {
            TateyomiAppRoot(viewModel = model)
        }
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uris = shareUris(intent)
        if (uris.isEmpty()) return
        // The activity can be recreated with the original intent. Clear the
        // shared payload after handing it to the view model.
        intent?.action = null
        intent?.removeExtra(Intent.EXTRA_STREAM)
        intent?.clipData = null
        viewModel.importShared(uris)
    }

    private fun shareUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) {
            return emptyList()
        }
        val result = LinkedHashSet<Uri>()
        if (intent.action == Intent.ACTION_SEND) {
            intent.streamUri()?.let { result += it }
        } else {
            result += intent.streamUris()
            if (result.isEmpty()) {
                intent.streamUri()?.let { result += it }
            }
        }
        intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let { result += it }
            }
        }
        return result.toList()
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    @Suppress("DEPRECATION")
    private fun Intent.streamUris(): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
}
