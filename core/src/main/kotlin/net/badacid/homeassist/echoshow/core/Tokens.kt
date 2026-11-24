/*
 * Keeping a password sign-in alive, and ending it. Home Assistant's access tokens last half an hour; the
 * refresh token gets new ones (https://developers.home-assistant.io/docs/auth_api). A long-lived token
 * never runs out and has nothing to refresh or revoke.
 */
package net.badacid.homeassist.echoshow.core

import java.io.IOException

sealed interface RefreshResult {
    /** `signIn` has the new access token: save it in place of the old one. */
    data class Refreshed(val signIn: SavedSignIn) : RefreshResult

    /** Home Assistant no longer accepts the sign-in. The person must sign in again. */
    data object Rejected : RefreshResult

    /** Home Assistant could not be reached, or answered with something other than tokens. Try again later. */
    data object CannotReach : RefreshResult
}

/** A token is refreshed this long before it runs out, so it does not expire while the connection opens. */
private const val REFRESH_MARGIN_MS = 10_000L

fun needsRefresh(signIn: SavedSignIn, now: Long): Boolean =
    signIn.kind == SignInKind.ACCOUNT && now >= signIn.expiresAt - REFRESH_MARGIN_MS

suspend fun refreshTokens(send: HttpSend, signIn: SavedSignIn, now: Long): RefreshResult {
    val answer = try {
        send(
            HttpRequest(
                "POST",
                "${signIn.address}/auth/token",
                formBody = mapOf("grant_type" to "refresh_token", "refresh_token" to signIn.refreshToken, "client_id" to CLIENT_ID),
            ),
        )
    } catch (_: IOException) {
        return RefreshResult.CannotReach
    }
    // Home Assistant refuses a revoked or unknown refresh token with 400 (invalid_grant), or 403 for a
    // user who has been switched off. Anything else (a proxy's 502, say) is not a verdict on the sign-in.
    if (answer.status == 400 || answer.status == 403) return RefreshResult.Rejected
    if (answer.status >= 300) return RefreshResult.CannotReach

    val tokens = readJsonObject(answer) ?: return RefreshResult.CannotReach
    val accessToken = textOrNull(tokens["access_token"]) ?: return RefreshResult.CannotReach
    val expiresIn = numberOrNull(tokens["expires_in"]) ?: return RefreshResult.CannotReach
    return RefreshResult.Refreshed(signIn.copy(accessToken = accessToken, expiresAt = now + (expiresIn * 1000).toLong()))
}

/** Asks Home Assistant to cancel the sign-in. Best effort: never throws, and does nothing for a long-lived token. */
suspend fun revokeTokens(send: HttpSend, signIn: SavedSignIn) {
    if (signIn.kind != SignInKind.ACCOUNT || signIn.refreshToken.isEmpty()) return
    try {
        send(HttpRequest("POST", "${signIn.address}/auth/revoke", formBody = mapOf("token" to signIn.refreshToken)))
    } catch (_: IOException) {
        // Signing out goes ahead on the Echo either way.
    }
}
