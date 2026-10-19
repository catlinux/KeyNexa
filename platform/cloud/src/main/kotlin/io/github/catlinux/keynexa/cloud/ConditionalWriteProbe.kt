package io.github.catlinux.keynexa.cloud

import io.github.catlinux.keynexa.sync.CloudAuthException
import io.github.catlinux.keynexa.sync.CloudBackend
import io.github.catlinux.keynexa.sync.CloudContent
import io.github.catlinux.keynexa.sync.CloudObject
import io.github.catlinux.keynexa.sync.CloudPreconditionFailed
import java.io.IOException
import java.security.SecureRandom

/** What [ConditionalWriteProbe.run] found about a service's conditional writes (ADR-0009 §3). */
sealed interface ProbeResult {
    /** Every conditional write the probe tried was honoured. */
    data object Supported : ProbeResult

    /** The service ignored or refused a conditional write at [step]; synchronisation cannot rely on it. */
    data class NotSupported(
        val step: Int,
    ) : ProbeResult

    /** The probe could not finish: the credentials were rejected or the network failed. */
    data class Failed(
        val error: IOException,
    ) : ProbeResult
}

/**
 * Tests, against the user's own bucket, that a service honours conditional writes (ADR-0009 §3).
 *
 * It writes a temporary object under the sync prefix and checks, in order, that a create-only upload over a free
 * name succeeds, that a create-only upload over the existing object is refused and leaves it unchanged, that an
 * upload with the current entity tag succeeds and that one with a stale tag is refused and leaves it unchanged. The
 * object is always deleted. The content is a constant, never a vault, and nothing is logged.
 */
@Suppress("TooManyFunctions")
class ConditionalWriteProbe(
    private val backend: CloudBackend,
    private val random: SecureRandom = SecureRandom(),
) {
    /** Runs the probe; the temporary object is deleted whatever the outcome. */
    fun run(): ProbeResult {
        val name = "$PROBE_PREFIX${randomHex()}"
        return try {
            probe(name)
        } finally {
            deleteQuietly(name)
        }
    }

    private fun probe(name: String): ProbeResult =
        when (val created = create(name)) {
            is Step.Stop -> created.result
            is Step.Created -> {
                val etag = created.objectInfo.etag
                if (etag == null) ProbeResult.NotSupported(STEP_CREATE) else later(name, etag)
            }
        }

    private fun later(
        name: String,
        etag: String,
    ): ProbeResult = createOver(name) ?: update(name, etag) ?: stale(name, etag) ?: ProbeResult.Supported

    private fun create(name: String): Step =
        when (val write = put(name, CONTENT_A, ifNoneMatch = true, step = STEP_CREATE)) {
            is Write.Ok -> Step.Created(write.objectInfo)
            Write.Refused -> Step.Stop(ProbeResult.NotSupported(STEP_CREATE))
            is Write.Stop -> Step.Stop(write.result)
        }

    private fun createOver(name: String): ProbeResult? =
        when (val write = put(name, CONTENT_B, ifNoneMatch = true, step = STEP_CREATE_OVER)) {
            is Write.Ok -> ProbeResult.NotSupported(STEP_CREATE_OVER)
            Write.Refused -> verify(name, CONTENT_A, STEP_CREATE_OVER)
            is Write.Stop -> write.result
        }

    private fun update(
        name: String,
        etag: String,
    ): ProbeResult? =
        when (val write = put(name, CONTENT_C, ifMatch = etag, step = STEP_UPDATE)) {
            is Write.Ok -> null
            Write.Refused -> ProbeResult.NotSupported(STEP_UPDATE)
            is Write.Stop -> write.result
        }

    private fun stale(
        name: String,
        etag: String,
    ): ProbeResult? =
        when (val write = put(name, CONTENT_D, ifMatch = etag, step = STEP_STALE)) {
            is Write.Ok -> ProbeResult.NotSupported(STEP_STALE)
            Write.Refused -> verify(name, CONTENT_C, STEP_STALE)
            is Write.Stop -> write.result
        }

    private fun verify(
        name: String,
        expected: ByteArray,
        step: Int,
    ): ProbeResult? =
        when (val read = fetch(name)) {
            is Read.Stop -> read.result
            is Read.Ok ->
                if (read.content?.bytes?.contentEquals(expected) == true) {
                    null
                } else {
                    ProbeResult.NotSupported(step)
                }
        }

    private fun put(
        name: String,
        content: ByteArray,
        ifMatch: String? = null,
        ifNoneMatch: Boolean = false,
        step: Int,
    ): Write =
        try {
            Write.Ok(backend.put(name, content, ifMatch, ifNoneMatch))
        } catch (expected: CloudPreconditionFailed) {
            Write.Refused
        } catch (unauthorized: CloudAuthException) {
            Write.Stop(ProbeResult.Failed(unauthorized))
        } catch (failure: IOException) {
            Write.Stop(if (unsupportedHeader(failure)) ProbeResult.NotSupported(step) else ProbeResult.Failed(failure))
        }

    private fun fetch(name: String): Read =
        try {
            Read.Ok(backend.fetch(name))
        } catch (unauthorized: CloudAuthException) {
            Read.Stop(ProbeResult.Failed(unauthorized))
        } catch (failure: IOException) {
            Read.Stop(ProbeResult.Failed(failure))
        }

    // A conditional request a service does not understand answers 400 or 501 with a code such as NotImplemented;
    // the S3 backend carries the status and the code in the exception message.
    private fun unsupportedHeader(failure: IOException): Boolean {
        val message = failure.message.orEmpty()
        return UNSUPPORTED_MARKERS.any(message::contains)
    }

    private fun randomHex(): String =
        ByteArray(PROBE_ID_BYTES).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    private fun deleteQuietly(name: String) {
        try {
            backend.delete(name)
        } catch (ignored: IOException) {
            // The probe is over; a failed cleanup must not change its result.
        }
    }

    private sealed interface Write {
        data class Ok(
            val objectInfo: CloudObject,
        ) : Write

        data object Refused : Write

        data class Stop(
            val result: ProbeResult,
        ) : Write
    }

    private sealed interface Read {
        data class Ok(
            val content: CloudContent?,
        ) : Read

        data class Stop(
            val result: ProbeResult,
        ) : Read
    }

    private sealed interface Step {
        data class Created(
            val objectInfo: CloudObject,
        ) : Step

        data class Stop(
            val result: ProbeResult,
        ) : Step
    }

    private companion object {
        const val STEP_CREATE = 1
        const val STEP_CREATE_OVER = 2
        const val STEP_UPDATE = 3
        const val STEP_STALE = 4
        const val PROBE_PREFIX = "KeyNexa/sync/.probe-"
        const val PROBE_ID_BYTES = 8
        val CONTENT_A = "keynexa-probe-a".toByteArray()
        val CONTENT_B = "keynexa-probe-b".toByteArray()
        val CONTENT_C = "keynexa-probe-c".toByteArray()
        val CONTENT_D = "keynexa-probe-d".toByteArray()
        val UNSUPPORTED_MARKERS =
            listOf("HTTP 400", "HTTP 501", "NotImplemented", "InvalidArgument", "InvalidRequest")
    }
}
