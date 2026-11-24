/*
 * Showing a press at once, before Home Assistant confirms it. Each prediction is laid over the real state
 * until Home Assistant reports that entity anew, or PENDING_LIMIT_MS passes (so a lost command cannot show
 * a wrong state for ever).
 */
package net.badacid.homeassist.echoshow.core

/** A predicted state and when it was made (milliseconds since 1970). */
data class Pending(val state: EntityState, val since: Long)

/** Predicted states by entity id. */
typealias PendingStates = Map<String, Pending>

/** How long a predicted state is shown without confirmation, in milliseconds. */
const val PENDING_LIMIT_MS = 5000L

/** Adds (or replaces) the predicted state for one entity. `now` is the time of the press. */
fun withPending(pending: PendingStates, predicted: EntityState, now: Long): PendingStates =
    pending + (predicted.entityId to Pending(predicted, now))

/** Removes one entity's predicted state. Gives back the same list when there was none. */
fun withoutPending(pending: PendingStates, entityId: String): PendingStates =
    if (entityId in pending) pending - entityId else pending

/**
 * Drops the predictions that are finished with: those whose entity Home Assistant has reported anew
 * between `previous` and `next`, and those older than PENDING_LIMIT_MS. Gives back the same list when
 * nothing was dropped.
 *
 * "Reported anew" is checked by comparing the two state objects themselves (===): the connection keeps the
 * same object for an entity until Home Assistant sends a change for it.
 */
fun settle(pending: PendingStates, previous: HomeSnapshot, next: HomeSnapshot, now: Long): PendingStates {
    var settled = pending
    for ((entityId, entry) in pending) {
        val wasReported = previous.states[entityId] !== next.states[entityId]
        val isTooOld = now - entry.since >= PENDING_LIMIT_MS
        if (wasReported || isTooOld) settled = withoutPending(settled, entityId)
    }
    return settled
}

/** The home as it should be shown: the real states with the predicted ones laid over them. */
fun applyPending(home: HomeSnapshot, pending: PendingStates): HomeSnapshot {
    val shown = pending.filterKeys { it in home.states }
    if (shown.isEmpty()) return home
    return home.copy(states = home.states + shown.mapValues { it.value.state })
}
