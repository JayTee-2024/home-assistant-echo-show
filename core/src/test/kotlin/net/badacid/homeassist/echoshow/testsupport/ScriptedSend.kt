package net.badacid.homeassist.echoshow.testsupport

import net.badacid.homeassist.echoshow.core.HttpAnswer
import net.badacid.homeassist.echoshow.core.HttpRequest
import net.badacid.homeassist.echoshow.core.HttpSend
import java.io.IOException

/** A sender that gives the scripted answers in order (an HttpAnswer, or an IOException to throw) and remembers what it was asked. */
class ScriptedSend(vararg answers: Any) {
    private val queue = ArrayDeque(answers.toList())
    val requests = mutableListOf<HttpRequest>()
    val send: HttpSend = { request ->
        requests += request
        when (val next = queue.removeFirstOrNull()) {
            null -> throw IOException("the test scripted too few answers")
            is IOException -> throw next
            else -> next as HttpAnswer
        }
    }
}
