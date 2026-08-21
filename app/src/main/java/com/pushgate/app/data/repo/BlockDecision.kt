package com.pushgate.app.data.repo

/** The verdict for one app at one instant. */
sealed interface BlockDecision {

    /** Not on the block list, or temporarily disabled. Nothing to do. */
    data object NotTracked : BlockDecision

    /** Running on today's remaining quota. The service counts down against this. */
    data class AllowedByQuota(
        val packageName: String,
        val remainingMs: Long,
        val budgetMs: Long,
        val usedMs: Long
    ) : BlockDecision

    /** Running on a grant the user paid push-ups for (or an explicit manual override). */
    data class AllowedByGrant(
        val packageName: String,
        val remainingMs: Long,
        val source: String
    ) : BlockDecision

    /** Out of quota and out of grants. Intercept immediately. */
    data class Blocked(
        val packageName: String,
        val label: String,
        val budgetMs: Long,
        val usedMs: Long,
        val earnedUnlocksToday: Int,
        val canEarn: Boolean,
        val repsRequired: Int,
        val minutesOffered: Int
    ) : BlockDecision

    val allowedRemainingMs: Long
        get() = when (this) {
            is AllowedByQuota -> remainingMs
            is AllowedByGrant -> remainingMs
            else -> 0L
        }

    val isAllowed: Boolean get() = this is AllowedByQuota || this is AllowedByGrant
}
