package com.fetchly.app.data.net

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns

/**
 * Refuses to resolve any host that points at the phone's own network.
 *
 * The check in [com.fetchly.app.domain.security.UrlSecurity] covers what a
 * user pastes; this covers what happens after that — redirects, and DNS
 * answers that might differ from (or outlive) the original validation.
 */
object PublicOnlyDns : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.any(::isPrivateAddress)) {
            throw UnknownHostException("$hostname resolves to a private address")
        }
        return addresses
    }
}

/** Loopback, LAN, link-local, unspecified, multicast — v4 and v6 alike. */
internal fun isPrivateAddress(address: InetAddress): Boolean =
    address.isLoopbackAddress ||
        address.isAnyLocalAddress ||
        address.isLinkLocalAddress ||
        address.isSiteLocalAddress ||
        address.isMulticastAddress ||
        address.isUniqueLocalIpv6()

/** fd00::/8 — Java's isSiteLocalAddress doesn't cover unique-local IPv6. */
internal fun InetAddress.isUniqueLocalIpv6(): Boolean {
    val bytes = address
    return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
}
