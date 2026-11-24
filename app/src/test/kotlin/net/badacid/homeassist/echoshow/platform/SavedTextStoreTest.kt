package net.badacid.homeassist.echoshow.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class SavedTextStoreTest {
    @TempDir
    lateinit var folder: File

    /** Reverses the bytes and adds a marker, so a file it did not write fails to "decrypt". */
    private class FakeCipher : TextCipher {
        override fun encrypt(text: String): ByteArray = byteArrayOf(42) + text.toByteArray().reversedArray()
        override fun decrypt(bytes: ByteArray): String {
            if (bytes.isEmpty() || bytes[0] != 42.toByte()) throw java.security.GeneralSecurityException("not ours")
            return String(bytes.copyOfRange(1, bytes.size).reversedArray())
        }
    }

    private fun store(cipher: TextCipher = FakeCipher()) = SavedTextStore(File(folder, "saved-state.bin"), cipher)

    @Test
    fun `reads back what was written`() {
        store().write("""{"version":1}""")
        assertEquals("""{"version":1}""", store().read())
    }

    @Test
    fun `keeps the text encrypted on disk`() {
        store().write("secret-token")
        assertFalse(String(File(folder, "saved-state.bin").readBytes()).contains("secret-token"))
    }

    @Test
    fun `gives null for no file`() {
        assertNull(store().read())
    }

    @Test
    fun `gives null when the file cannot be decrypted`() {
        File(folder, "saved-state.bin").writeBytes(byteArrayOf(1, 2, 3))
        assertNull(store().read())
        File(folder, "saved-state.bin").writeBytes(ByteArray(0))
        assertNull(store().read())
    }

    @Test
    fun `gives null when the cipher fails in any other way`() {
        store().write("text")
        val broken = object : TextCipher {
            override fun encrypt(text: String) = ByteArray(0)
            override fun decrypt(bytes: ByteArray): String = throw IllegalStateException("keystore unavailable")
        }
        assertNull(store(broken).read())
    }

    @Test
    fun `a write after a bad file replaces it`() {
        File(folder, "saved-state.bin").writeBytes(byteArrayOf(9, 9))
        store().write("fresh")
        assertEquals("fresh", store().read())
    }

    @Test
    fun `a failed write leaves the old file`() {
        store().write("old")
        val failing = object : TextCipher {
            override fun encrypt(text: String): ByteArray = throw java.security.GeneralSecurityException("no key")
            override fun decrypt(bytes: ByteArray) = FakeCipher().decrypt(bytes)
        }
        assertThrows<IOException> { store(failing).write("new") }
        assertEquals("old", store().read())
        assertTrue(folder.listFiles()!!.none { it.name.endsWith(".tmp") }, "no temporary file is left behind")
    }
}
