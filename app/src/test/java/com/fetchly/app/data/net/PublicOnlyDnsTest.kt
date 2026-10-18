package com.fetchly.app.data.net

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicOnlyDnsTest {

    private fun addressOf(literal: String): InetAddress = InetAddress.getByName(literal)

    @Test
    fun `loopback, lan and link-local literals are private`() {
        assertTrue(isPrivateAddress(addressOf("127.0.0.1")))
        assertTrue(isPrivateAddress(addressOf("10.1.2.3")))
        assertTrue(isPrivateAddress(addressOf("192.168.1.1")))
        assertTrue(isPrivateAddress(addressOf("172.20.0.1")))
        assertTrue(isPrivateAddress(addressOf("169.254.1.1")))
        assertTrue(isPrivateAddress(addressOf("0.0.0.0")))
        assertTrue(isPrivateAddress(addressOf("::1")))
        assertTrue(isPrivateAddress(addressOf("fd00::1")))
        assertTrue(isPrivateAddress(addressOf("fe80::1")))
    }

    @Test
    fun `public literals are not private`() {
        assertFalse(isPrivateAddress(addressOf("8.8.8.8")))
        assertFalse(isPrivateAddress(addressOf("1.1.1.1")))
        assertFalse(isPrivateAddress(addressOf("2606:4700:4700::1111")))
        assertFalse(isPrivateAddress(addressOf("2001:4860:4860::8888")))
    }
}
