package io.github.catlinux.keynexa.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The official SigV4 examples of the AWS documentation ("Signature Calculations for the Authorization Header"):
 * if the implementation is right, these signatures come out exactly.
 */
class S3SignerTest {
    private val credentials = S3Credentials(ACCESS_KEY, SECRET_KEY)
    private val at: Instant = Instant.parse("2013-05-24T00:00:00Z")

    @Test
    fun theGetObjectWithRangeVectorMatches() {
        val signed =
            sign(
                method = "GET",
                url = "https://examplebucket.s3.amazonaws.com/test.txt",
                headers = mapOf("Range" to "bytes=0-9"),
            )

        assertEquals("f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41", signature(signed))
        assertTrue(
            signed.getValue("authorization").contains("SignedHeaders=host;range;x-amz-content-sha256;x-amz-date"),
        )
        assertEquals(EMPTY_HASH, signed.getValue("x-amz-content-sha256"))
    }

    @Test
    fun thePutObjectVectorMatches() {
        val signed =
            sign(
                method = "PUT",
                url = "https://examplebucket.s3.amazonaws.com/test\$file.text",
                headers =
                    mapOf(
                        "Date" to "Fri, 24 May 2013 00:00:00 GMT",
                        "x-amz-storage-class" to "REDUCED_REDUNDANCY",
                    ),
                body = "Welcome to Amazon S3.".toByteArray(),
            )

        assertEquals("98ad721746da40c64f1a55b78f14c238d841ea1380cd77a1b5971af0ece108bd", signature(signed))
        val authorization = signed.getValue("authorization")
        val expectedSignedHeaders = "date;host;x-amz-content-sha256;x-amz-date;x-amz-storage-class"
        assertTrue(authorization.contains("SignedHeaders=$expectedSignedHeaders"))
        assertEquals(
            "44ce7dd67c959e0d3524ffac1771dfbba87d2b6b4b4e99e42034a8b803f8b072",
            signed.getValue("x-amz-content-sha256"),
        )
    }

    @Test
    fun theGetBucketLifecycleVectorMatches() {
        val signed =
            sign(
                method = "GET",
                url = "https://examplebucket.s3.amazonaws.com/?lifecycle",
            )

        assertEquals("fea454ca298b7da1c68078a5d1bdbfbbe0d65c699e0f91ac7a200a0136783543", signature(signed))
    }

    @Test
    fun theListObjectsVectorMatches() {
        val signed =
            sign(
                method = "GET",
                url = "https://examplebucket.s3.amazonaws.com/?max-keys=2&prefix=J",
            )

        assertEquals("34b48302e7b5fa45bde8084f4b7868a86f0a534bc59db6670ed5711ef69dc6f7", signature(signed))
    }

    private fun sign(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
    ): Map<String, String> = S3Signer.sign(method, url, headers, body, credentials, REGION, at)

    private fun signature(signed: Map<String, String>): String =
        requireNotNull(SIGNATURE.find(signed.getValue("authorization"))).groupValues[1]

    private companion object {
        const val ACCESS_KEY = "AKIAIOSFODNN7EXAMPLE"
        const val SECRET_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
        const val REGION = "us-east-1"
        const val EMPTY_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val SIGNATURE = Regex("Signature=([0-9a-f]+)")
    }
}
