package com.rance.galcommunity.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpTest {

    @Test
    void ignoresSpoofedHeadersFromUntrustedPeer() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("8.8.8.8");
        request.addHeader("X-Real-IP", "1.2.3.4");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        assertThat(ClientIp.of(request, List.of())).isEqualTo("8.8.8.8");
    }

    @Test
    void usesXRealIpFromTrustedProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "1.2.3.4");
        assertThat(ClientIp.of(request, List.of("127.0.0.1/32"))).isEqualTo("1.2.3.4");
    }

    @Test
    void usesLastForwardedHopWhenXRealIpMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.2");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8");
        assertThat(ClientIp.of(request, List.of("172.18.0.0/16"))).isEqualTo("5.6.7.8");
    }

    @Test
    void ignoresProxyHeadersWhenPeerOutsideAllowlist() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.2");
        request.addHeader("X-Real-IP", "1.2.3.4");
        assertThat(ClientIp.of(request, List.of("192.168.0.0/16"))).isEqualTo("172.18.0.2");
    }

    @Test
    void ignoresMalformedAllowlistEntries() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "1.2.3.4");
        assertThat(ClientIp.of(request, List.of("not-a-cidr"))).isEqualTo("127.0.0.1");
    }

    @Test
    void treatsIpv4MappedPeerAsTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("::ffff:172.18.0.2");
        request.addHeader("X-Real-IP", "1.2.3.4");
        assertThat(ClientIp.of(request, List.of("172.18.0.0/16"))).isEqualTo("1.2.3.4");
    }

    @Test
    void ignoresNonIpRealIpHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "deadbeef");
        assertThat(ClientIp.of(request, List.of("127.0.0.1/32"))).isEqualTo("127.0.0.1");
    }

    @Test
    void matchesExactIpWithoutPrefix() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("X-Real-IP", "9.9.9.9");
        assertThat(ClientIp.of(request, List.of("10.1.2.3"))).isEqualTo("9.9.9.9");
    }
}
