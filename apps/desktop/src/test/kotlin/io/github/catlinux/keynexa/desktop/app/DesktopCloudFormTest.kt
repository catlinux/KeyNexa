package io.github.catlinux.keynexa.desktop.app

import io.github.catlinux.keynexa.cloud.ConnectionResult
import io.github.catlinux.keynexa.cloud.S3Setup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cloud form: what it refuses, how it fills the region in, and how the steps go back. */
class DesktopCloudFormTest {
    private companion object {
        const val B2_ENDPOINT = "https://s3.us-west-004.backblazeb2.com"
        const val R2_ENDPOINT = "https://abc123.r2.cloudflarestorage.com"
    }

    @Test
    fun refusesEveryEmptyField() {
        val errors = validate(SetupForm())

        assertTrue(errors.endpoint)
        assertTrue(errors.region)
        assertTrue(errors.bucket)
        assertTrue(errors.accessKeyId)
        assertTrue(errors.secretAccessKey)
        assertTrue(errors.any)
    }

    @Test
    fun refusesAnEndpointWithoutHttps() {
        val form = filled(endpoint = "s3.us-west-004.backblazeb2.com")

        assertTrue(validate(form).endpoint)
    }

    @Test
    fun acceptsAnEndpointWithHttps() {
        val form = filled(endpoint = B2_ENDPOINT)

        assertFalse(validate(form).endpoint)
    }

    @Test
    fun asksForTheRegionOnlyWhenTheEndpointDoesNotSayIt() {
        assertFalse(validate(filled(endpoint = B2_ENDPOINT, region = "")).region)
        assertTrue(validate(filled(endpoint = "https://storage.example.com", region = "")).region)
    }

    @Test
    fun refusesAnEmptyBucketKeyOrSecret() {
        assertTrue(validate(filled(bucket = "")).bucket)
        assertTrue(validate(filled(accessKeyId = "")).accessKeyId)
        assertTrue(validate(filled(secretAccessKey = "")).secretAccessKey)
    }

    @Test
    fun fillsTheRegionFromTheEndpoint() {
        val form = SetupForm()

        form.onEndpointChange(B2_ENDPOINT)

        assertEquals("us-west-004", form.region)
    }

    @Test
    fun fillsTheCloudflareRegionWithAuto() {
        val form = SetupForm()

        form.onEndpointChange(R2_ENDPOINT)

        assertEquals("auto", form.region)
    }

    @Test
    fun neverOverwritesTheRegionTheUserTyped() {
        val form = SetupForm()
        form.onEndpointChange(B2_ENDPOINT)
        form.onRegionChange("eu-central-1")

        form.onEndpointChange("https://s3.us-east-005.backblazeb2.com")

        assertEquals("eu-central-1", form.region)
    }

    @Test
    fun clearsAnAutomaticRegionWhenTheEndpointStopsSayingIt() {
        val form = SetupForm()
        form.onEndpointChange(B2_ENDPOINT)

        form.onEndpointChange("https://storage.example.com")

        assertEquals("", form.region)
    }

    @Test
    fun resetForgetsEverything() {
        val form = filled()
        form.secretVisible = true

        form.reset()

        assertEquals("", form.endpoint)
        assertEquals("", form.region)
        assertEquals("", form.bucket)
        assertEquals("", form.accessKeyId)
        assertEquals("", form.secretAccessKey)
        assertTrue(form.pathStyle)
        assertEquals(S3Setup.DEFAULT_KEEP, form.keep)
        assertFalse(form.secretVisible)
        assertFalse(form.errors.any)
    }

    @Test
    fun toSetupRoundTripsThroughItsValues() {
        val setup = filled(endpoint = B2_ENDPOINT, region = "us-west-004").toSetup()

        assertEquals(setup, S3Setup.fromValues(setup.toValues()))
    }

    @Test
    fun toSetupTrimsTheFieldsAndKeepsTheSecret() {
        val form = SetupForm()
        form.endpoint = "  $B2_ENDPOINT  "
        form.region = "  "
        form.bucket = "  my-bucket  "
        form.accessKeyId = "  key-id  "
        form.secretAccessKey = "  secret  "

        val setup = form.toSetup()

        assertEquals(B2_ENDPOINT, setup.endpoint)
        assertEquals("us-west-004", setup.region)
        assertEquals("my-bucket", setup.bucket)
        assertEquals("key-id", setup.accessKeyId)
        assertEquals("  secret  ", setup.secretAccessKey)
    }

    @Test
    fun backClosesTheFirstAndTheLastStep() {
        var closed = false

        assertEquals(CloudStep.Consent, backStep(CloudStep.Consent) { closed = true })
        assertTrue(closed)

        closed = false
        assertEquals(CloudStep.Status, backStep(CloudStep.Status) { closed = true })
        assertTrue(closed)
    }

    @Test
    fun backWalksTheMiddleSteps() {
        val noClose = {}

        assertEquals(CloudStep.Consent, backStep(CloudStep.Service, noClose))
        assertEquals(CloudStep.Service, backStep(CloudStep.Needs, noClose))
        assertEquals(CloudStep.Needs, backStep(CloudStep.Form, noClose))
    }

    private fun filled(
        endpoint: String = B2_ENDPOINT,
        region: String = "us-west-004",
        bucket: String = "my-bucket",
        accessKeyId: String = "key-id",
        secretAccessKey: String = "secret",
    ): SetupForm =
        SetupForm().apply {
            this.endpoint = endpoint
            this.region = region
            this.bucket = bucket
            this.accessKeyId = accessKeyId
            this.secretAccessKey = secretAccessKey
        }

    @Test
    fun changingAnyFieldThrowsAwayTheLastTest() {
        val changes: List<Pair<String, (SetupForm) -> Unit>> =
            listOf(
                "endpoint" to { it.endpoint = "https://s3.example.org" },
                "region" to { it.region = "eu-west-1" },
                "bucket" to { it.bucket = "keynexa" },
                "accessKeyId" to { it.accessKeyId = "access" },
                "secretAccessKey" to { it.secretAccessKey = "secret" },
                "pathStyle" to { it.pathStyle = false },
            )
        changes.forEach { (field, change) ->
            val form = SetupForm()
            form.testedOk = true
            form.testResult = ConnectionResult.Ok

            change(form)

            assertFalse("changing $field must throw the test away", form.testedOk)
            assertNull(form.testResult)
        }
    }
}
