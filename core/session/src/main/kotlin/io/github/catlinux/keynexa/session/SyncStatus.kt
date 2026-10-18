package io.github.catlinux.keynexa.session

/**
 * Heuristic: warn when the vault file has not changed for at least [warnDays] days. Returns the number of days when
 * the warning applies, or null otherwise (warning disabled, or no modification time to judge from).
 */
fun syncWarning(
    lastModifiedMillis: Long?,
    nowMillis: Long,
    warnDays: Int,
): Int? {
    if (warnDays <= 0 || lastModifiedMillis == null) return null
    val elapsedDays = (nowMillis - lastModifiedMillis) / MILLIS_PER_DAY
    return if (elapsedDays >= warnDays) elapsedDays.toInt() else null
}

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
