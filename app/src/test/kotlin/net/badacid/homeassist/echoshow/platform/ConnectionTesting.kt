package net.badacid.homeassist.echoshow.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.badacid.homeassist.echoshow.core.HomeConnection
import net.badacid.homeassist.echoshow.core.SavedSignIn
import net.badacid.homeassist.echoshow.testsupport.FakeHomeAssistant
import okhttp3.OkHttpClient
import java.util.concurrent.CopyOnWriteArrayList

/** Shared set-up for the connection tests: a stand-in, a scope that is cancelled afterwards, and helpers that wait with a time limit. */
open class ConnectionTesting {
    protected val fake = FakeHomeAssistant()
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    protected val client = OkHttpClient()
    protected val savedTokens = CopyOnWriteArrayList<SavedSignIn>()
    private val opened = CopyOnWriteArrayList<HomeConnection>()

    protected suspend fun open(
        signIn: SavedSignIn = fake.signInTokens(),
        clock: () -> Long = System::currentTimeMillis,
        firstLoadTimeoutMs: Long = 5_000,
        registryRefreshDelayMs: Long = 200,
        reconnectDelay: (Int) -> Long = { attempt -> attempt * 50L },
        commandTimeoutMs: Long = 10_000,
        onTokens: (SavedSignIn) -> Unit = { savedTokens += it },
    ): HomeConnection = openHomeConnection(
        signIn,
        onTokens = onTokens,
        client = client,
        scope = scope,
        clock = clock,
        firstLoadTimeoutMs = firstLoadTimeoutMs,
        registryRefreshDelayMs = registryRefreshDelayMs,
        reconnectDelay = reconnectDelay,
        commandTimeoutMs = commandTimeoutMs,
    ).also { opened += it }

    /** Waits up to five seconds for `flow` to hold a value matching `test`. */
    protected fun <T> StateFlow<T>.waitFor(test: (T) -> Boolean): T = runBlocking { withTimeout(5_000) { first(test) } }

    protected fun waitUntil(what: String, test: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!test()) {
            check(System.currentTimeMillis() < until) { "Timed out waiting for $what" }
            Thread.sleep(20)
        }
    }

    @org.junit.jupiter.api.AfterEach
    fun stopEverything() {
        opened.forEach { it.close() }
        scope.cancel()
        fake.close()
    }
}
