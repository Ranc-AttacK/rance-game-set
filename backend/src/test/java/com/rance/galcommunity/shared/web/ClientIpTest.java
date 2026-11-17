package com.rance.galcommunity.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpTest {

    @Test
    void ignoresSpoofedHeadersFromUntrustedPeer() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("8.8.8.8");
        request.addHeader("X-Real-IP", "1.2.3.4");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        assertThat(ClientIp.of(request)).isEqualTo("8.8.8.8");
    }

    @Test
    void usesXRealIpFromTrustedProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "1.2.3.4");
        assertThat(ClientIp.of(request)).isEqualTo("1.2.3.4");
    }

    @Test
    void usesLastForwardedHopWhenXRealIpMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.2");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8");
        assertThat(ClientIp.of(request)).isEqualTo("5.6.7.8");
    }
}
