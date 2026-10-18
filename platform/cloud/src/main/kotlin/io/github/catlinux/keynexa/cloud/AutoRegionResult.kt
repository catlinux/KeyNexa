@file:Suppress("MatchingDeclarationName")

package io.github.catlinux.keynexa.cloud

/** The region to show after the endpoint changed, and the new "last region the assistant filled in". */
data class AutoRegionResult(
    val region: String,
    val lastAutoRegion: String?,
)

/**
 * Decides what the region field should hold after [endpoint] changed.
 *
 * The assistant only overwrites a region it filled in itself: when [currentRegion] is empty or still equals
 * [lastAutoRegion]. A region the user typed is never touched, and the "last automatic region" is kept so a later
 * change of endpoint can tell them apart. When the new endpoint says nothing ([regionFromEndpoint] is null) and the
 * region was automatic, the field is cleared.
 */
fun regionAfterEndpointChange(
    endpoint: String,
    currentRegion: String,
    lastAutoRegion: String?,
): AutoRegionResult {
    val userEdited = currentRegion.isNotEmpty() && currentRegion != lastAutoRegion
    if (userEdited) return AutoRegionResult(currentRegion, lastAutoRegion)
    val deduced = regionFromEndpoint(endpoint)
    return AutoRegionResult(deduced.orEmpty(), deduced)
}
