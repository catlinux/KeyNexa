package io.github.catlinux.keynexa.app.ui.group

fun syncWarning(
    lastModifiedMillis: Long?,
    nowMillis: Long,
    warnDays: Int,
): Int? =
    io.github.catlinux.keynexa.app.vault
        .syncWarning(lastModifiedMillis, nowMillis, warnDays)
