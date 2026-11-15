package app.tateyomi

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Seeds the emulator/device library with public-domain books for manual UI
 * inspection. Run with:
 *
 *   gradle connectedDebugAndroidTest --tests '*SeedLibraryTest*'
 */
@RunWith(AndroidJUnit4::class)
class SeedLibraryTest {

    @Test
    fun seedLibrary() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<TateyomiApp>()
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        app.repository.ready.first { it }
        if (app.repository.library.value.isEmpty()) {
            listOf("rasyomon.epub", "horadanoshaku.epub").forEach { name ->
                val target = File(app.cacheDir, name)
                testAssets.open(name).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                app.repository.importEpub(Uri.fromFile(target))
                target.delete()
            }
        }
        assertTrue(app.repository.library.value.isNotEmpty())
    }
}
