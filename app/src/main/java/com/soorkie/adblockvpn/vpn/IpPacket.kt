package com.soorkie.adblockvpn.vpn

import java.nio.ByteBuffer

/**
 * IPv4 + UDP packet helpers used by [LocalVpnService].
 *
 * We don't try to be a general-purpose IP stack — only what's needed to
 * recognize and rebuild a UDP/53 round-trip.
 */
internal object IpPacket {

    const val PROTO_UDP = 17

    data class UdpV4(
        val srcIp: ByteArray,   // 4 bytes
        val dstIp: ByteArray,   // 4 bytes
        val srcPort: Int,
        val dstPort: Int,
        val payload: ByteBuffer // position..limit is UDP data
    )

    /** Parse a buffer containing one IPv4 packet starting at offset 0. */
    fun parseUdpV4(buf: ByteArray, length: Int): UdpV4? {
        if (length < 20) return null
        val versionIhl = buf[0].toInt() and 0xFF
        if (versionIhl ushr 4 != 4) return null
        val ihl = (versionIhl and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return null
        val proto = buf[9].toInt() and 0xFF
        if (proto != PROTO_UDP) return null

        val totalLen = ((buf[2].toInt() and 0xFF) shl 8) or (buf[3].toInt() and 0xFF)
        val pktLen = minOf(totalLen, length)

        val srcIp = buf.copyOfRange(12, 16)
        val dstIp = buf.copyOfRange(16, 20)

        val udpStart = ihl
        val srcPort = ((buf[udpStart].toInt() and 0xFF) shl 8) or (buf[udpStart + 1].toInt() and 0xFF)
        val dstPort = ((buf[udpStart + 2].toInt() and 0xFF) shl 8) or (buf[udpStart + 3].toInt() and 0xFF)
        val udpLen = ((buf[udpStart + 4].toInt() and 0xFF) shl 8) or (buf[udpStart + 5].toInt() and 0xFF)
        val payloadStart = udpStart + 8
        val payloadEnd = minOf(udpStart + udpLen, pktLen)
        if (payloadEnd < payloadStart) return null

        val payload = ByteBuffer.wrap(buf, payloadStart, payloadEnd - payloadStart).slice()
        return UdpV4(srcIp, dstIp, srcPort, dstPort, payload)
    }

    /**
     * Build a complete IPv4 + UDP packet. UDP checksum is left zero (legal for IPv4).
     */
    fun buildUdpV4(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val totalLen = 20 + 8 + payload.size
        val pkt = ByteArray(totalLen)

        // ---- IPv4 header (20 bytes, no options) ----
        pkt[0] = 0x45                                             // version 4, IHL 5
        pkt[1] = 0                                                // DSCP/ECN
        pkt[2] = ((totalLen ushr 8) and 0xFF).toByte()
        pkt[3] = (totalLen and 0xFF).toByte()
        pkt[4] = 0; pkt[5] = 0                                    // identification
        pkt[6] = 0x40.toByte(); pkt[7] = 0                        // flags=DF, frag offset=0
        pkt[8] = 64                                               // TTL
        pkt[9] = PROTO_UDP.toByte()
        pkt[10] = 0; pkt[11] = 0                                  // checksum (filled below)
        System.arraycopy(srcIp, 0, pkt, 12, 4)
        System.arraycopy(dstIp, 0, pkt, 16, 4)
        val ipChecksum = checksum(pkt, 0, 20)
        pkt[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        pkt[11] = (ipChecksum and 0xFF).toByte()

        // ---- UDP header (8 bytes) ----
        val udpLen = 8 + payload.size
        pkt[20] = ((srcPort ushr 8) and 0xFF).toByte()
        pkt[21] = (srcPort and 0xFF).toByte()
        pkt[22] = ((dstPort ushr 8) and 0xFF).toByte()
        pkt[23] = (dstPort and 0xFF).toByte()
        pkt[24] = ((udpLen ushr 8) and 0xFF).toByte()
        pkt[25] = (udpLen and 0xFF).toByte()
        pkt[26] = 0; pkt[27] = 0                                  // checksum 0 = unused (IPv4)

        // ---- UDP payload ----
        System.arraycopy(payload, 0, pkt, 28, payload.size)
        return pkt
    }

    private fun checksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            if (sum and 0x10000 != 0) sum = (sum and 0xFFFF) + 1
            i += 2
        }
        if (i < end) {
            sum += (buf[i].toInt() and 0xFF) shl 8
            if (sum and 0x10000 != 0) sum = (sum and 0xFFFF) + 1
        }
        return sum.inv() and 0xFFFF
    }
}
