package io.github.catlinux.keynexa.desktop.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The initial shown on an avatar, or `?` when there is no name to take it from. */
internal fun initialOf(text: String): String {
    val trimmed = text.trim()
    return if (trimmed.isEmpty()) "?" else trimmed.take(1).uppercase()
}

/** The one-time code with a space in the middle, which is how it is read out. */
internal fun formatTotpCode(code: String): String {
    if (code.length < 2) return code
    val half = code.length / 2
    return code.substring(0, half) + " " + code.substring(half)
}

/** A short, locale-aware date for [instant]; empty when there is none. */
internal fun shortDate(
    instant: Instant?,
    zone: ZoneId,
    locale: Locale,
): String {
    if (instant == null) return ""
    return DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .withZone(zone)
        .format(instant)
}
