package net.badacid.homeassist.echoshow.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.badacid.homeassist.echoshow.core.HomeConnection
import net.badacid.homeassist.echoshow.core.SavedSignIn
import net.badacid.homeassist.echoshow.platform.KeystoreTextCipher
import net.badacid.homeassist.echoshow.platform.SavedTextStore
import net.badacid.homeassist.echoshow.platform.homeAssistantClient
import net.badacid.homeassist.echoshow.platform.openHomeConnection
import okhttp3.OkHttpClient
import java.io.File

typealias ConnectionOpener = suspend (SavedSignIn, onTokens: (SavedSignIn) -> Unit) -> HomeConnection

/**
 * Holds one AppModel for the life of the process, so turning the screen or recreating the activity keeps
 * the connection and the page.
 */
class HomeAssistApp : Application() {
    /** For tests: set before the model is first used. */
    internal var storageForTests: SavedTextStorage? = null
    internal var openerForTests: ConnectionOpener? = null
    internal var liveForTests: net.badacid.homeassist.echoshow.screens.camera.LiveVideoAccess? = null

    /** When live view last stopped for each camera, for the picture labels. */
    val pictureUpdates = net.badacid.homeassist.echoshow.devices.camera.PictureUpdates()

    val client: OkHttpClient by lazy { homeAssistantClient() }

    /** The model's thread: the main thread, as AppModel requires. */
    private var mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Where connections do their work; their reports reach the model through StateFlows. */
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var built: AppModel? = null

    val model: AppModel
        get() = built ?: buildModel().also { built = it }

    private fun buildModel(): AppModel {
        val storage = storageForTests ?: SavedTextStore(File(filesDir, "saved-state.bin"), KeystoreTextCipher()).let { store ->
            object : SavedTextStorage {
                override fun read() = store.read()
                override fun write(text: String) = store.write(text)
            }
        }
        val opener: ConnectionOpener = openerForTests ?: { signIn, onTokens ->
            // Fresh tokens can arrive on a network thread; the model takes them on the main thread.
            openHomeConnection(signIn, { fresh -> mainScope.launch { onTokens(fresh) } }, client, workScope)
        }
        return AppModel(storage, opener, mainScope, System::currentTimeMillis)
    }

    /** For tests: a new model next time, as if the process had started again. */
    internal fun resetForTests() {
        mainScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        built = null
        storageForTests = null
        openerForTests = null
        liveForTests = null
    }
}
