/*
 * The conversation with Home Assistant that turns a username and password into tokens, using Home
 * Assistant's own sign-in flow (https://developers.home-assistant.io/docs/auth_api). Each function takes
 * the sender, so tests can answer from a script.
 */
package net.badacid.homeassist.echoshow.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException

enum class SignInKind(val savedName: String) {
    /** Signed in with a username and password. */
    ACCOUNT("account"),

    /** The Advanced route: a long-lived access token. */
    LONG_LIVED_TOKEN("long-lived-token"),
    ;

    companion object {
        fun fromSavedName(name: String): SignInKind? = entries.firstOrNull { it.savedName == name }
    }
}

/** What the app keeps to talk to Home Assistant. Never the password. */
data class SavedSignIn(
    /** For example 'http://192.168.1.100:8123'. */
    val address: String,
    val kind: SignInKind,
    val accessToken: String,
    /** The key that gets new access tokens. Empty for a long-lived token. */
    val refreshToken: String,
    /** When the access token stops working, in milliseconds since 1970. 0 for a long-lived token. */
    val expiresAt: Long,
)

/** Where the sign-in has got to, and what to show. */
sealed interface SignInStep {
    /** Show the username and password form. `message` is an error to show with it, if any. */
    data class Credentials(val flowId: String, val message: String?) : SignInStep

    /** Show the two-step code form. */
    data class Code(val flowId: String, val message: String?) : SignInStep

    /** Finished. `signIn` is what to save. */
    data class SignedIn(val signIn: SavedSignIn) : SignInStep

    /** Nothing answered at the address. */
    data class CannotReach(val message: String) : SignInStep

    /** Something answered, but it is not Home Assistant. */
    data class NotHomeAssistant(val message: String) : SignInStep

    /** Home Assistant refused. `message` is its reason. */
    data class Failed(val message: String) : SignInStep
}

/** Home Assistant sends error keys, not sentences. These are its own English words for them. */
private val ERROR_WORDS = mapOf(
    "invalid_auth" to "Invalid username or password",
    "invalid_code" to "Invalid authentication code",
)

private const val CANNOT_REACH = "Could not reach Home Assistant at this address."
private const val NOT_HOME_ASSISTANT = "Something answered at this address, but it is not Home Assistant."
private const val TOOK_TOO_LONG = "Sign-in took too long. Please try again."

/**
 * Shown when Home Assistant refuses to start a sign-in (HTTP 403). On the TV that was its cross-origin
 * check; the Echo app sends no browser origin, so this should be rare. The access token route goes round it.
 */
const val SIGN_IN_REFUSED =
    "Home Assistant refused the sign-in from this Echo. Use \"Advanced: use an access token\" instead."

suspend fun beginSignIn(send: HttpSend, address: String): SignInStep {
    val body = buildJsonObject {
        put("client_id", CLIENT_ID)
        // The handler names the kind of account: Home Assistant's own usernames and passwords.
        putJsonArray("handler") {
            add("homeassistant")
            add(JsonNull)
        }
        put("redirect_uri", CLIENT_ID)
    }
    val answer = try {
        send(HttpRequest("POST", "$address/auth/login_flow", jsonBody = body))
    } catch (_: IOException) {
        return cannotReach()
    }
    if (answer.status == 403) return SignInStep.Failed(SIGN_IN_REFUSED)
    if (answer.status >= 400) return refusal(answer)

    val flow = readJsonObject(answer) ?: return notHomeAssistant()
    val flowId = textOrNull(flow["flow_id"])
    if (textOrNull(flow["type"]) != "form" || flowId == null) return notHomeAssistant()
    return SignInStep.Credentials(flowId, null)
}

suspend fun submitCredentials(
    send: HttpSend,
    address: String,
    flowId: String,
    username: String,
    password: String,
    now: Long,
): SignInStep = submitFlowStep(
    send, address, flowId,
    mapOf("client_id" to CLIENT_ID, "username" to username, "password" to password),
    now,
)

suspend fun submitCode(send: HttpSend, address: String, flowId: String, code: String, now: Long): SignInStep =
    submitFlowStep(send, address, flowId, mapOf("client_id" to CLIENT_ID, "code" to code), now)

/** The "Advanced" route: a long-lived access token made on the person's Home Assistant profile page. */
fun signInWithToken(address: String, token: String): SavedSignIn =
    SavedSignIn(address, SignInKind.LONG_LIVED_TOKEN, token.trim(), "", 0)

/** Sends one step of the flow and works out what its answer means. */
private suspend fun submitFlowStep(
    send: HttpSend,
    address: String,
    flowId: String,
    fields: Map<String, String>,
    now: Long,
): SignInStep {
    val body = JsonObject(fields.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) })
    val answer = try {
        send(HttpRequest("POST", "$address/auth/login_flow/$flowId", jsonBody = body))
    } catch (_: IOException) {
        return cannotReach()
    }
    // Home Assistant forgets a flow after a while, or when it restarts. Start a new one.
    if (answer.status == 404) return restart(send, address)
    if (answer.status >= 400) return refusal(answer)

    val result = readJsonObject(answer) ?: return notHomeAssistant()
    val code = textOrNull(result["result"])
    return when (textOrNull(result["type"])) {
        "create_entry" -> if (code != null) exchangeCode(send, address, code, now) else notHomeAssistant()
        "abort" -> SignInStep.Failed(textOrNull(result["reason"]) ?: "Sign-in was stopped.")
        "form" -> {
            val message = errorMessage(result["errors"] as? JsonObject)
            if (textOrNull(result["step_id"]) == "mfa") {
                SignInStep.Code(flowId, message)
            } else {
                SignInStep.Credentials(flowId, message)
            }
        }
        else -> notHomeAssistant()
    }
}

/** Begins a fresh flow and tells the person why they are being asked again. */
private suspend fun restart(send: HttpSend, address: String): SignInStep =
    when (val step = beginSignIn(send, address)) {
        is SignInStep.Credentials -> SignInStep.Credentials(step.flowId, TOOK_TOO_LONG)
        else -> step
    }

/** Swaps the one-time code from the flow for the tokens the app keeps. */
private suspend fun exchangeCode(send: HttpSend, address: String, code: String, now: Long): SignInStep {
    val answer = try {
        send(
            HttpRequest(
                "POST",
                "$address/auth/token",
                formBody = mapOf("grant_type" to "authorization_code", "code" to code, "client_id" to CLIENT_ID),
            ),
        )
    } catch (_: IOException) {
        return cannotReach()
    }
    if (answer.status >= 400) return refusal(answer)

    val tokens = readJsonObject(answer) ?: return notHomeAssistant()
    val accessToken = textOrNull(tokens["access_token"])
    val refreshToken = textOrNull(tokens["refresh_token"])
    val expiresIn = numberOrNull(tokens["expires_in"])
    if (accessToken == null || refreshToken == null || expiresIn == null) return notHomeAssistant()

    // Home Assistant gives the lifetime in seconds.
    val expiresAt = now + (expiresIn * 1000).toLong()
    return SignInStep.SignedIn(SavedSignIn(address, SignInKind.ACCOUNT, accessToken, refreshToken, expiresAt))
}

/** The answer's body as a JSON object, or null when it is anything else. */
internal fun readJsonObject(answer: HttpAnswer): JsonObject? =
    try {
        Json.parseToJsonElement(answer.body) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    }

/** The sentence for the first error in a flow answer's `errors`, or null when there is none. */
private fun errorMessage(errors: JsonObject?): String? {
    val base = textOrNull(errors?.get("base")) ?: return null
    return ERROR_WORDS[base] ?: base
}

/** A failed step carrying Home Assistant's own reason, when it gave one. */
private fun refusal(answer: HttpAnswer): SignInStep {
    val body = readJsonObject(answer)
    val reason = textOrNull(body?.get("message")) ?: textOrNull(body?.get("error_description"))
    return SignInStep.Failed(reason ?: "Home Assistant answered with error ${answer.status}.")
}

private fun cannotReach() = SignInStep.CannotReach(CANNOT_REACH)

private fun notHomeAssistant() = SignInStep.NotHomeAssistant(NOT_HOME_ASSISTANT)
