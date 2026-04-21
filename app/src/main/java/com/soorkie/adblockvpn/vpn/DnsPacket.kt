package com.soorkie.adblockvpn.vpn

import java.nio.ByteBuffer

/**
 * Minimal DNS message helpers. Only what we need:
 *  - Read QNAME of the first question in a query.
 *  - We never construct DNS messages ourselves; responses are taken verbatim
 *    from the upstream resolver and only the IP/UDP envelope is rewritten.
 */
internal object DnsPacket {

    /**
     * Parses the first question's QNAME from a DNS message payload.
     * Returns null if the payload is malformed or has no questions.
     */
    fun extractFirstQName(payload: ByteBuffer): String? {
        // DNS header is 12 bytes; bail if too small.
        if (payload.remaining() < 12) return null
        val start = payload.position()
        val qdCount = payload.getShort(start + 4).toInt() and 0xFFFF
        if (qdCount < 1) return null

        var pos = start + 12
        val end = payload.limit()
        val sb = StringBuilder()
        var hops = 0
        while (pos < end) {
            val len = payload.get(pos).toInt() and 0xFF
            if (len == 0) {
                return if (sb.isEmpty()) null else sb.toString()
            }
            // Compression pointer (top two bits set) — follow once for safety.
            if (len and 0xC0 == 0xC0) {
                if (pos + 1 >= end || hops++ > 5) return null
                val ptr = ((len and 0x3F) shl 8) or (payload.get(pos + 1).toInt() and 0xFF)
                pos = start + ptr
                continue
            }
            if (len > 63 || pos + 1 + len > end) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until len) {
                val b = payload.get(pos + 1 + i).toInt() and 0xFF
                sb.append(b.toChar())
            }
            pos += 1 + len
        }
        return null
    }

    /**
     * Build an NXDOMAIN response for a DNS query by reusing the query bytes:
     *  - sets QR=1 (response), RA=1, RCODE=3 (NXDOMAIN)
     *  - zeros ANCOUNT/NSCOUNT/ARCOUNT
     *  - keeps the question section intact
     */
    fun synthesizeNxDomain(query: ByteArray): ByteArray {
        val resp = query.copyOf()
        if (resp.size < 12) return resp
        // Flags byte 1 (offset 2): set QR (top bit). Preserve Opcode/AA/TC/RD.
        resp[2] = (resp[2].toInt() or 0x80).toByte()
        // Flags byte 2 (offset 3): RA=1, Z=0, RCODE=3 → 0b1000_0011
        resp[3] = 0x83.toByte()
        // ANCOUNT/NSCOUNT/ARCOUNT (offsets 6..11) → 0
        for (i in 6..11) resp[i] = 0
        return resp
    }
}
