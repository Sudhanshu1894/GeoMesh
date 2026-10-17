package com.example.resilentnet.data.protocol

import android.util.Base64
import java.util.BitSet
import kotlin.math.abs

/**
 * 512-bit BloomFilter implementation matching desktop BloomFilter.java.
 * Used for epidemic message summary reconciliation between peers.
 */
class BloomFilter(private val size: Int = 512) {
    private val bitset: BitSet = BitSet(size)

    fun add(value: String) {
        val h1 = abs(value.hashCode()) % size
        val h2 = abs((value + "salt").hashCode()) % size
        bitset.set(h1)
        bitset.set(h2)
    }

    fun mightContain(value: String): Boolean {
        val h1 = abs(value.hashCode()) % size
        val h2 = abs((value + "salt").hashCode()) % size
        return bitset.get(h1) && bitset.get(h2)
    }

    fun getBitSet(): BitSet = bitset

    /**
     * Serializes BitSet bytes into a compact Base64 string for network packets.
     */
    fun toBase64(): String {
        val bytes = bitset.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    companion object {
        fun fromBase64(base64String: String, size: Int = 512): BloomFilter {
            val filter = BloomFilter(size)
            try {
                val bytes = Base64.decode(base64String, Base64.NO_WRAP)
                val loadedBitSet = BitSet.valueOf(bytes)
                filter.bitset.or(loadedBitSet)
            } catch (_: Exception) {}
            return filter
        }
    }
}
