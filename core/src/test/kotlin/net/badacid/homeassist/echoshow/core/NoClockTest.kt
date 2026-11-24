package net.badacid.homeassist.echoshow.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/** :core takes the time as a parameter everywhere, so its rules can be tested at any moment. */
class NoClockTest {
    @Test
    fun `nothing in core reads the clock`() {
        val clockCalls = listOf("System.currentTimeMillis", "System.nanoTime", "Instant.now", "Clock.System", "LocalDateTime.now", "Date()")
        val offenders = File("src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> clockCalls.filter { it in file.readText() }.map { "${file.name}: $it" } }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
