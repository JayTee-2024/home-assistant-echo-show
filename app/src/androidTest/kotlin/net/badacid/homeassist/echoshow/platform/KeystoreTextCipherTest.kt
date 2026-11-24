package net.badacid.homeassist.echoshow.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on the Echo itself (scripts/echo.sh instrument): the keystore exists only on a real device. */
@RunWith(AndroidJUnit4::class)
class KeystoreTextCipherTest {
    private val cipher = KeystoreTextCipher(alias = "home-assist-test-key")

    @Test
    fun roundTrip() {
        val text = """{"version":1,"signIn":{"accessToken":"secret"}}"""
        assertEquals(text, cipher.decrypt(cipher.encrypt(text)))
        assertFalse(String(cipher.encrypt(text), Charsets.ISO_8859_1).contains("secret"))
    }

    @Test
    fun twoEncryptionsOfTheSameTextDiffer() {
        assertNotEquals(cipher.encrypt("same").toList(), cipher.encrypt("same").toList())
    }

    @Test(expected = Exception::class)
    fun aTamperedByteFailsToDecrypt() {
        val bytes = cipher.encrypt("hello")
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        cipher.decrypt(bytes)
    }
}
