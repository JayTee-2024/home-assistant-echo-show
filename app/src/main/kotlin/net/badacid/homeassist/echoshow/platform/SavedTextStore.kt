package net.badacid.homeassist.echoshow.platform

import java.io.File
import java.io.IOException

/** Turns text into bytes that only this app on this Echo can read, and back. */
interface TextCipher {
    fun encrypt(text: String): ByteArray

    /** Throws when the bytes were not made by this cipher, were changed, or the key is gone. */
    fun decrypt(bytes: ByteArray): String
}

/**
 * The app's saved state (core's SavedState, as text) in one encrypted file in the app's own storage.
 *
 * Reading never fails: a missing, damaged or undecryptable file reads as null, which the app treats as
 * "nothing saved" (signed out). That happens if Android loses the keystore key. The next write replaces
 * the file. A write goes to a temporary file first and then replaces the old one in one step, so a write
 * that fails part-way leaves the old file as it was.
 */
class SavedTextStore(private val file: File, private val cipher: TextCipher) {
    fun read(): String? {
        if (!file.exists()) return null
        return try {
            cipher.decrypt(file.readBytes())
        } catch (_: Exception) {
            null
        }
    }

    /** Throws IOException when the text could not be saved; the old file is then unchanged. */
    fun write(text: String) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        try {
            temporary.writeBytes(cipher.encrypt(text))
            if (!temporary.renameTo(file)) throw IOException("Could not replace ${file.name}")
        } catch (problem: Exception) {
            temporary.delete()
            throw problem as? IOException ?: IOException("Could not save the app's state", problem)
        }
    }
}
