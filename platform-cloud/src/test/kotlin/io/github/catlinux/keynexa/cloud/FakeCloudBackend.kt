@file:Suppress("MatchingDeclarationName")

package io.github.catlinux.keynexa.cloud

import io.github.catlinux.keynexa.sync.CloudBackend
import io.github.catlinux.keynexa.sync.CloudCapabilities
import io.github.catlinux.keynexa.sync.CloudObject
import java.io.IOException

/** An in-memory cloud store for the platform-cloud tests, with programmable failures and call counters. */
internal class FakeCloudBackend(
    override val capabilities: CloudCapabilities = CloudCapabilities(conditionalWrites = false),
) : CloudBackend {
    val objects: MutableMap<String, ByteArray> = LinkedHashMap()

    var failList: IOException? = null
    var failPut: IOException? = null
    var failDelete: IOException? = null

    var lists: Int = 0
        private set
    var puts: Int = 0
        private set
    var deletes: Int = 0
        private set

    override fun put(
        name: String,
        content: ByteArray,
        ifMatch: String?,
    ): CloudObject {
        puts++
        failPut?.let { throw it }
        objects[name] = content.copyOf()
        return CloudObject(name, content.size.toLong(), null, "etag-$puts")
    }

    override fun get(name: String): ByteArray = objects[name]?.copyOf() ?: throw IOException("missing")

    override fun list(prefix: String): List<CloudObject> {
        lists++
        failList?.let { throw it }
        return objects
            .filterKeys { it.startsWith(prefix) }
            .map { (name, bytes) -> CloudObject(name, bytes.size.toLong(), null, null) }
    }

    override fun delete(name: String) {
        deletes++
        failDelete?.let { throw it }
        objects.remove(name) ?: throw IOException("missing")
    }
}
