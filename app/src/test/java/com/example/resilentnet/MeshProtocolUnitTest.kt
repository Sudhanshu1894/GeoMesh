package com.example.resilentnet

import com.example.resilentnet.data.model.DeliveryStatus
import com.example.resilentnet.data.model.Message
import com.example.resilentnet.data.model.MessagePriority
import com.example.resilentnet.data.model.MessageScope
import com.example.resilentnet.data.model.NetworkPacket
import com.example.resilentnet.data.model.PacketType
import com.example.resilentnet.data.protocol.BloomFilter
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.BitSet
import kotlin.math.abs

class MeshProtocolUnitTest {

    private val gson = Gson()

    @Test
    fun testMessageEmergencyKeywordEscalation() {
        val normalMsg = Message.createOutgoing(
            content = "Standard status report from Sector 4",
            senderId = "A1B2C3",
            priority = MessagePriority.NORMAL
        )
        assertFalse("Normal message should not be marked urgent", normalMsg.urgent)
        assertEquals(MessagePriority.NORMAL, normalMsg.priority)

        val sosMsg = Message.createOutgoing(
            content = "Need immediate SOS evacuation at base camp",
            senderId = "A1B2C3",
            priority = MessagePriority.NORMAL
        )
        assertTrue("SOS keyword must trigger urgent flag", sosMsg.urgent)
        assertEquals("SOS keyword must escalate priority to CRITICAL", MessagePriority.CRITICAL, sosMsg.priority)

        val helpMsg = Message.createOutgoing(
            content = "Please send help urgently!",
            senderId = "A1B2C3",
            priority = MessagePriority.NORMAL
        )
        assertTrue("HELP keyword must trigger urgent flag", helpMsg.urgent)
        assertEquals("HELP keyword must escalate priority to CRITICAL", MessagePriority.CRITICAL, helpMsg.priority)
    }

    @Test
    fun testPriorityQueueOrdering() {
        val low = Message.createOutgoing("Low", "N1", priority = MessagePriority.LOW)
        val normal = Message.createOutgoing("Normal", "N1", priority = MessagePriority.NORMAL)
        val high = Message.createOutgoing("High", "N1", priority = MessagePriority.HIGH)
        val critical = Message.createOutgoing("Critical", "N1", priority = MessagePriority.CRITICAL)

        val list = mutableListOf(normal, low, critical, high)
        list.sortWith(compareByDescending { it.priority.ordinal })

        assertEquals("CRITICAL must be first in offline flush queue", MessagePriority.CRITICAL, list[0].priority)
        assertEquals("HIGH must be second", MessagePriority.HIGH, list[1].priority)
        assertEquals("NORMAL must be third", MessagePriority.NORMAL, list[2].priority)
        assertEquals("LOW must be fourth", MessagePriority.LOW, list[3].priority)
    }

    @Test
    fun testPacketSerializationAndDesktopCompatibility() {
        val originalMsg = Message.createOutgoing(
            content = "Interoperability Test Packet",
            senderId = "DESK01",
            priority = MessagePriority.HIGH,
            scope = MessageScope.BROADCAST
        )
        originalMsg.ttl = 3

        val outgoingPacket = NetworkPacket(
            type = PacketType.DATA,
            senderId = "DESK01",
            displayName = "DeskNode-1",
            payload = originalMsg
        )

        val json = gson.toJson(outgoingPacket)
        assertTrue("JSON must contain PacketType DATA", json.contains("\"DATA\""))
        assertTrue("JSON must contain senderId DESK01", json.contains("\"DESK01\""))
        assertTrue("JSON must contain payload content", json.contains("Interoperability Test Packet"))

        // Simulate receiving and deserializing by another node
        val incomingPacket = gson.fromJson(json, NetworkPacket::class.java)
        assertNotNull("Deserialized packet must not be null", incomingPacket)
        assertEquals(PacketType.DATA, incomingPacket.type)
        assertEquals("DESK01", incomingPacket.senderId)
        assertNotNull(incomingPacket.payload)
        assertEquals("Interoperability Test Packet", incomingPacket.payload?.content)
        assertEquals(3, incomingPacket.payload?.ttl)
        assertEquals(MessagePriority.HIGH, incomingPacket.payload?.priority)
    }

    @Test
    fun testTtlDecrementAndRelayEligibility() {
        val msg = Message.createOutgoing("Relayed packet", "ORIGIN", scope = MessageScope.BROADCAST)
        msg.ttl = 3

        // First hop
        val hop1 = msg.copy(ttl = msg.ttl - 1)
        assertEquals(2, hop1.ttl)
        assertTrue("Hop 1 should be eligible for relay", hop1.ttl > 0)

        // Second hop
        val hop2 = hop1.copy(ttl = hop1.ttl - 1)
        assertEquals(1, hop2.ttl)
        assertTrue("Hop 2 should be eligible for relay", hop2.ttl > 0)

        // Third hop
        val hop3 = hop2.copy(ttl = hop2.ttl - 1)
        assertEquals(0, hop3.ttl)
        assertFalse("Hop 3 with ttl=0 must not be forwarded again", hop3.ttl > 0)
    }

    @Test
    fun testBloomFilterHashOperations() {
        val size = 512
        val bitset = BitSet(size)

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

        val id1 = "MSG-UUID-1001"
        val id2 = "MSG-UUID-1002"
        val id3 = "MSG-UUID-1003"

        add(id1)
        add(id2)

        assertTrue("BloomFilter must contain added id1", mightContain(id1))
        assertTrue("BloomFilter must contain added id2", mightContain(id2))
        assertFalse("BloomFilter should not contain unadded id3", mightContain(id3))
    }

    @Test
    fun testMalformedJsonPacketRejection() {
        val corruptJson = "{ type: DATA, senderId: 12345 " // Missing closing bracket and quotes
        var errorCaught = false
        val parsed: NetworkPacket? = try {
            gson.fromJson(corruptJson, NetworkPacket::class.java)
        } catch (_: Exception) {
            errorCaught = true
            null
        }

        assertTrue("Malformed JSON must be rejected safely", errorCaught || parsed == null)
    }
}
