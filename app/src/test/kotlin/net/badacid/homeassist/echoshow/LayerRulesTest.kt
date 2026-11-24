package net.badacid.homeassist.echoshow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The app's layers: app → screens → devices → core, and platform → core only.
 * Screens never reach platform directly. These tests read the source files and check their imports.
 */
class LayerRulesTest {
    private val sources: Map<String, String> by lazy {
        val root = File("src/main/kotlin/net/badacid/homeassist/echoshow")
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
    }

    private fun violationsFor(layer: String) =
        layerViolations(sources).filter { it.startsWith("$layer/") }

    @Test
    fun `screens never import platform`() {
        assertEquals(emptyList<String>(), violationsFor("screens"))
    }

    @Test
    fun `platform imports only core`() {
        assertEquals(emptyList<String>(), violationsFor("platform"))
    }

    @Test
    fun `devices import only core`() {
        assertEquals(emptyList<String>(), violationsFor("devices"))
    }

    @Test
    fun `core is not a folder in app`() {
        assertTrue(sources.keys.none { it.startsWith("core/") }, "core lives only in the :core module")
    }

    @Test
    fun `a file that breaks a rule is reported`() {
        val found = layerViolations(
            mapOf("screens/Home.kt" to "import net.badacid.homeassist.echoshow.platform.Storage\n"),
        )
        assertEquals(1, found.size)
        assertTrue(found[0].contains("screens/Home.kt"))
    }
}
