package com.soorkie.adblockvpn.data

/**
 * Plain-text blocklist serialization used for import/export.
 *
 * Format:
 *   - One domain per line.
 *   - Lines may use the hosts-file form `0.0.0.0 example.com` (or
 *     `127.0.0.1 example.com`); the leading IP is stripped.
 *   - Anything after `#` is treated as a comment and ignored.
 *   - Blank lines are ignored.
 *   - Domains are normalised to lowercase and basic syntactic validation
 *     is applied (label chars, length).
 */
object BlocklistFormat {

    private val DOMAIN_REGEX =
        Regex("^(?=.{1,253}$)([a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9_])?)(\\.[a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9_])?)+$")

    fun export(domains: List<String>): String = buildString {
        append("# Adblock VPN blocklist export\n")
        append("# ").append(domains.size).append(" domains\n")
        domains.forEach { append(it).append('\n') }
    }

    fun parse(text: String): List<String> {
        val out = LinkedHashSet<String>()
        text.lineSequence().forEach { raw ->
            val noComment = raw.substringBefore('#').trim()
            if (noComment.isEmpty()) return@forEach
            // Hosts-file style: strip a leading IP if the line has whitespace.
            val token = noComment.split(Regex("\\s+")).let { parts ->
                when {
                    parts.size >= 2 && looksLikeIp(parts[0]) -> parts[1]
                    else -> parts[0]
                }
            }.trim().lowercase().trimEnd('.')
            if (token.isNotEmpty() && DOMAIN_REGEX.matches(token)) {
                out += token
            }
        }
        return out.toList()
    }

    private fun looksLikeIp(s: String): Boolean {
        if (s == "0.0.0.0" || s == "127.0.0.1" || s == "::" || s == "::1") return true
        // Generic dotted-quad / IPv6 detection (no validation needed beyond a hint).
        return s.matches(Regex("^[0-9.]+$")) || s.contains(':')
    }
}
