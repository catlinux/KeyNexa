package io.github.catlinux.keynexa.ui.fields

/** How many suggestions the dropdown offers at most. */
private const val SUGGESTION_LIMIT = 6

/** The first [limit] suggestions that contain [typed] ignoring case, leaving out the value already written. */
internal fun filterSuggestions(
    all: List<String>,
    typed: String,
    limit: Int = SUGGESTION_LIMIT,
): List<String> =
    if (typed.isBlank()) {
        all.take(limit)
    } else {
        all.filter { it != typed && it.contains(typed, ignoreCase = true) }.take(limit)
    }
