package com.lanraragi.reader.data.local.incremental

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class DerivedCacheKey(val identity: String, val revision: String, val variant: String = "default") {
    override fun toString(): String = "derived|${identity.length}:$identity|${revision.length}:$revision|${variant.length}:$variant"
}

interface CacheLease : AutoCloseable { val key: DerivedCacheKey; override fun close() }

interface DerivedCache<T> {
    fun get(key: DerivedCacheKey): T?
    fun put(key: DerivedCacheKey, value: T)
    fun acquire(key: DerivedCacheKey): CacheLease
    fun invalidate(identity: String, revision: String? = null)
}

/** Small thread-safe cache primitive; production storage can implement the same contract. */
class MemoryDerivedCache<T> : DerivedCache<T> {
    private data class Entry<T>(var value: T?, var leases: Int = 0)
    private val values = ConcurrentHashMap<DerivedCacheKey, Entry<T>>()
    private val generation = AtomicLong()
    override fun get(key: DerivedCacheKey): T? = values[key]?.value
    override fun put(key: DerivedCacheKey, value: T) { values[key] = Entry(value) }
    override fun acquire(key: DerivedCacheKey): CacheLease {
        val entry = values.computeIfAbsent(key) { Entry(null) }
        synchronized(entry) { entry.leases++ }
        return object : CacheLease {
            private var closed = false
            override val key: DerivedCacheKey = key
            override fun close() { if (!closed) synchronized(entry) { closed = true; entry.leases = (entry.leases - 1).coerceAtLeast(0) } }
        }
    }
    override fun invalidate(identity: String, revision: String?) {
        generation.incrementAndGet()
        values.keys.removeIf { it.identity == identity && (revision == null || it.revision == revision) && values[it]?.leases == 0 }
    }
    fun size(): Int = values.size
    fun generation(): Long = generation.get()
}
