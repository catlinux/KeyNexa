package io.github.catlinux.keynexa.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The storable S3 configuration: the value round trip, what is rejected and what [toString] hides. */
class S3SetupTest {
    @Test
    fun valuesRoundTrip() {
        val setup = setup()

        assertEquals(setup, S3Setup.fromValues(setup.toValues()))
    }

    @Test
    fun missingRequiredValuesAreRejected() {
        assertNull(S3Setup.fromValues(emptyMap()))
        assertNull(S3Setup.fromValues(setup().toValues() - "endpoint"))
        assertNull(S3Setup.fromValues(setup().toValues() - "region"))
        assertNull(S3Setup.fromValues(setup().toValues() - "bucket"))
        assertNull(S3Setup.fromValues(setup().toValues() - "accessKeyId"))
        assertNull(S3Setup.fromValues(setup().toValues() - "secretAccessKey"))
    }

    @Test
    fun aMissingKeepFallsBackToTheDefault() {
        assertEquals(S3Setup.DEFAULT_KEEP, S3Setup.fromValues(setup().toValues() - "keep")?.keep)
    }

    @Test
    fun invalidValuesAreRejected() {
        assertNull(S3Setup.fromValues(setup().toValues() + ("endpoint" to "http://s3.example.com")))
        assertNull(S3Setup.fromValues(setup().toValues() + ("region" to "  ")))
        assertNull(S3Setup.fromValues(setup().toValues() + ("bucket" to "")))
        assertNull(S3Setup.fromValues(setup().toValues() + ("keep" to "0")))
        assertNull(S3Setup.fromValues(setup().toValues() + ("keep" to "101")))
    }

    @Test
    fun toStringNeverShowsTheCredentials() {
        val text = setup().toString()

        assertFalse(text.contains(ACCESS_KEY))
        assertFalse(text.contains(SECRET_KEY))
    }

    @Test
    fun synchronisationRoundTrips() {
        val setup = setup().copy(sync = true, syncProbe = S3Setup.probeKey(ENDPOINT, BUCKET))

        val restored = S3Setup.fromValues(setup.toValues())

        assertEquals(setup, restored)
        assertEquals(true, restored?.syncEnabled)
    }

    @Test
    fun changingTheServiceVoidsSynchronisation() {
        val otherEndpoint =
            setup().copy(sync = true, syncProbe = S3Setup.probeKey("https://s3.other.example.com", BUCKET))
        val otherBucket = setup().copy(sync = true, syncProbe = S3Setup.probeKey(ENDPOINT, "other-bucket"))

        assertFalse(S3Setup.fromValues(otherEndpoint.toValues())!!.syncEnabled)
        assertFalse(S3Setup.fromValues(otherBucket.toValues())!!.syncEnabled)
    }

    @Test
    fun oldDataLoadsWithSynchronisationOff() {
        val restored = S3Setup.fromValues(setup().toValues() - "sync" - "syncProbe")

        assertFalse(restored!!.sync)
        assertNull(restored.syncProbe)
        assertFalse(restored.syncEnabled)
    }

    @Test
    fun aBackblazeEndpointGivesItsRegion() {
        assertEquals("eu-central-003", regionFromEndpoint("https://s3.eu-central-003.backblazeb2.com"))
    }

    @Test
    fun anAmazonEndpointGivesItsRegion() {
        assertEquals("us-east-1", regionFromEndpoint("https://s3.us-east-1.amazonaws.com"))
    }

    @Test
    fun aCloudflareR2EndpointGivesAuto() {
        assertEquals("auto", regionFromEndpoint("https://abc123.r2.cloudflarestorage.com"))
    }

    @Test
    fun anUnknownHostHasNoRegion() {
        assertNull(regionFromEndpoint("https://s3.example.com"))
        assertNull(regionFromEndpoint("https://example.com"))
    }

    @Test
    fun theHostIsReadIgnoringCase() {
        assertEquals("us-west-004", regionFromEndpoint("https://S3.US-WEST-004.BACKBLAZEB2.COM"))
    }

    @Test
    fun anEndpointWithoutHttpsHasNoRegion() {
        assertNull(regionFromEndpoint("s3.us-west-004.backblazeb2.com"))
    }

    @Test
    fun anEmptyRegionIsFilledFromTheEndpoint() {
        val result =
            regionAfterEndpointChange(
                endpoint = "https://s3.eu-central-003.backblazeb2.com",
                currentRegion = "",
                lastAutoRegion = null,
            )

        assertEquals("eu-central-003", result.region)
        assertEquals("eu-central-003", result.lastAutoRegion)
    }

    @Test
    fun aRegionTheUserTypedIsNotOverwritten() {
        val result =
            regionAfterEndpointChange(
                endpoint = "https://s3.us-east-1.amazonaws.com",
                currentRegion = "my-region",
                lastAutoRegion = null,
            )

        assertEquals("my-region", result.region)
        assertNull(result.lastAutoRegion)
    }

    @Test
    fun changingTheEndpointUpdatesTheAutomaticRegion() {
        val result =
            regionAfterEndpointChange(
                endpoint = "https://s3.us-east-1.amazonaws.com",
                currentRegion = "eu-central-003",
                lastAutoRegion = "eu-central-003",
            )

        assertEquals("us-east-1", result.region)
        assertEquals("us-east-1", result.lastAutoRegion)
    }

    @Test
    fun anUnknownEndpointClearsTheAutomaticRegion() {
        val result =
            regionAfterEndpointChange(
                endpoint = "https://s3.example.com",
                currentRegion = "us-east-1",
                lastAutoRegion = "us-east-1",
            )

        assertEquals("", result.region)
        assertNull(result.lastAutoRegion)
    }

    @Test
    fun anEditedRegionSurvivesAnEndpointChange() {
        val result =
            regionAfterEndpointChange(
                endpoint = "https://s3.us-east-1.amazonaws.com",
                currentRegion = "my-region",
                lastAutoRegion = "eu-central-003",
            )

        assertEquals("my-region", result.region)
        assertEquals("eu-central-003", result.lastAutoRegion)
    }

    private fun setup(): S3Setup =
        S3Setup(
            endpoint = "https://s3.example.com",
            region = "us-east-1",
            bucket = "mybucket",
            pathStyle = true,
            accessKeyId = ACCESS_KEY,
            secretAccessKey = SECRET_KEY,
            keep = 5,
        )

    private companion object {
        const val ACCESS_KEY = "AKIAIOSFODNN7EXAMPLE"
        const val SECRET_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
        const val ENDPOINT = "https://s3.example.com"
        const val BUCKET = "mybucket"
    }
}
