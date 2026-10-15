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
    }
}
