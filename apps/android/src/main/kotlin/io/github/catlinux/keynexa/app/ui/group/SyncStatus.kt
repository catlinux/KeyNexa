package io.github.catlinux.keynexa.app.ui.group

fun syncWarning(
    lastModifiedMillis: Long?,
    nowMillis: Long,
    warnDays: Int,
): Int? =
    io.github.catlinux.keynexa.session
        .syncWarning(lastModifiedMillis, nowMillis, warnDays)
