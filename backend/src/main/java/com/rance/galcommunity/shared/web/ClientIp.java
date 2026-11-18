package com.rance.galcommunity.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;

public final class ClientIp {

    private static final Pattern IPV4_LITERAL = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$");
    private static final Pattern IPV6_LITERAL = Pattern.compile("^[0-9a-fA-F:.]+$");

    private ClientIp() {
    }

    public static String of(HttpServletRequest request, List<String> trustedProxies) {
        String remote = request.getRemoteAddr();
        if (!isTrustedProxy(remote, trustedProxies)) {
            return remote;
        }
        String realIp = request.getHeader("X-Real-IP");
        if (isValidIp(realIp)) {
            return realIp.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            // The nearest proxy appends the peer it saw; take the last hop, not the client-controlled first.
            String candidate = hops[hops.length - 1].trim();
            if (isValidIp(candidate)) {
                return candidate;
            }
        }
        return remote;
    }

    private static boolean isTrustedProxy(String remote, List<String> trustedProxies) {
        if (remote == null || remote.isBlank() || trustedProxies == null) {
            return false;
        }
        for (String cidr : trustedProxies) {
            if (matchesCidr(remote, cidr)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesCidr(String ip, String cidr) {
        if (cidr == null || cidr.isBlank()) {
            return false;
        }
        String trimmed = cidr.trim();
        int slash = trimmed.indexOf('/');
        String network = slash < 0 ? trimmed : trimmed.substring(0, slash);
        int prefix;
        try {
            prefix = slash < 0 ? -1 : Integer.parseInt(trimmed.substring(slash + 1));
        } catch (NumberFormatException ex) {
            return false;
        }
        if (!isValidIp(ip.trim()) || !isValidIp(network.trim())) {
            return false;
        }
        try {
            byte[] address = normalize(InetAddress.getByName(ip.trim()).getAddress());
            byte[] base = normalize(InetAddress.getByName(network.trim()).getAddress());
            if (address.length != base.length) {
                return false;
            }
            int bits = prefix < 0 ? address.length * 8 : prefix;
            if (bits < 0 || bits > address.length * 8) {
                return false;
            }
            int fullBytes = bits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != base[i]) {
                    return false;
                }
            }
            int remainder = bits % 8;
            if (remainder == 0) {
                return true;
            }
            int mask = (0xFF << (8 - remainder)) & 0xFF;
            return (address[fullBytes] & mask) == (base[fullBytes] & mask);
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    private static boolean isValidIp(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 45) {
            return false;
        }
        if (IPV4_LITERAL.matcher(trimmed).matches()) {
            return true;
        }
        // Colons cannot appear in hostnames, so this parse never triggers DNS resolution.
        return trimmed.contains(":") && IPV6_LITERAL.matcher(trimmed).matches() && parses(trimmed);
    }

    private static boolean parses(String literal) {
        try {
            InetAddress.getByName(literal);
            return true;
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    private static byte[] normalize(byte[] address) {
        if (address.length == 16) {
            for (int i = 0; i < 10; i++) {
                if (address[i] != 0) {
                    return address;
                }
            }
            if (address[10] == (byte) 0xFF && address[11] == (byte) 0xFF) {
                return new byte[] {address[12], address[13], address[14], address[15]};
            }
        }
        return address;
    }
}
