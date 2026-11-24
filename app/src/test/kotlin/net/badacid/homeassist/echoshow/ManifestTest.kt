package net.badacid.homeassist.echoshow

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Settings in the manifest that the app depends on and a unit test can see. */
class ManifestTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun `allows plain http, because Home Assistant on a home network is usually http`() {
        // Android 9 and later refuse cleartext HTTP unless the app allows it; without this, every sign-in to
        // http://<address>:8123 would fail as "Could not reach Home Assistant".
        assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        val config = File("src/main/res/xml/network_security_config.xml").readText()
        assertTrue(config.contains("cleartextTrafficPermitted=\"true\""))
    }

    @Test
    fun `keeps backups off`() {
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }
}
