package net.badacid.homeassist.echoshow.core

/** What became of the address the person typed. */
sealed interface AddressResult {
    /** A usable base address such as 'http://192.168.1.100:8123', with no path and no trailing slash. */
    data class Ok(val url: String) : AddressResult

    /** Nothing was typed. */
    data object Empty : AddressResult

    /** What was typed cannot be an http or https address. */
    data object Invalid : AddressResult
}

/** Home Assistant's own name on the network, offered when nothing better is known. */
const val DEFAULT_ADDRESS = "homeassistant.local:8123"

private const val DEFAULT_PORT = "8123"
private val SCHEME = Regex("^(https?)://", RegexOption.IGNORE_CASE)
private val HOST_AND_PORT = Regex("^([A-Za-z0-9._-]+)(?::(\\d+))?$")

/**
 * Turns what the person typed into the address the app talks to. A bare host gets http:// and port 8123,
 * Home Assistant's own; someone who typed a scheme or a port knows what they want, so those are kept.
 */
fun normaliseAddress(input: String): AddressResult {
    val typed = input.trim()
    if (typed.isEmpty()) return AddressResult.Empty
    if (typed.any { it.isWhitespace() }) return AddressResult.Invalid

    val scheme = SCHEME.find(typed)
    // Some other scheme, such as ftp://. Only http and https reach Home Assistant.
    if (scheme == null && typed.contains("://")) return AddressResult.Invalid

    val protocol = scheme?.groupValues?.get(1)?.lowercase() ?: "http"
    val rest = if (scheme == null) typed else typed.substring(scheme.value.length)
    val hostAndPort = rest.split('/', '?', '#').first()
    val match = HOST_AND_PORT.matchEntire(hostAndPort) ?: return AddressResult.Invalid
    val host = match.groupValues[1].lowercase()
    val typedPort = match.groupValues[2]

    // Someone who typed a full address knows their port. Only a bare host gets the default.
    val port = when {
        typedPort.isNotEmpty() -> typedPort.trimStart('0').ifEmpty { "0" }
        scheme != null -> ""
        else -> DEFAULT_PORT
    }
    // As a browser does, the scheme's own port is left out.
    val defaultForScheme = if (protocol == "https") "443" else "80"
    val shownPort = if (port == defaultForScheme) "" else port

    return AddressResult.Ok("$protocol://$host" + if (shownPort.isEmpty()) "" else ":$shownPort")
}
