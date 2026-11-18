package com.rance.galcommunity.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JwtKeyProviderTest {

    private static AppProperties.Jwt jwt(boolean requireConfiguredKeys) {
        return new AppProperties.Jwt("", "", "", "", "gal-community",
                Duration.ofMinutes(30), Duration.ofDays(7), requireConfiguredKeys);
    }

    @Test
    void failsWhenConfiguredKeysAreRequiredButMissing() {
        assertThatThrownBy(() -> new JwtKeyProvider(jwt(true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required");
    }

    @Test
    void generatesEphemeralKeyWhenAllowed() {
        JwtKeyProvider provider = new JwtKeyProvider(jwt(false));
        assertThat(provider.publicKey()).isNotNull();
        assertThat(provider.kid()).isNotBlank();
    }

    @Test
    void loadsKeysFromFiles(@TempDir Path dir) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        Path privateFile = writePem(dir.resolve("jwt-private.pem"), "PRIVATE KEY", pair.getPrivate().getEncoded());
        Path publicFile = writePem(dir.resolve("jwt-public.pem"), "PUBLIC KEY", pair.getPublic().getEncoded());

        AppProperties.Jwt props = new AppProperties.Jwt("", "",
                privateFile.toString(), publicFile.toString(), "gal-community",
                Duration.ofMinutes(30), Duration.ofDays(7), true);

        JwtKeyProvider provider = new JwtKeyProvider(props);

        assertThat(provider.publicKey().getModulus()).isEqualTo(
                ((java.security.interfaces.RSAPublicKey) pair.getPublic()).getModulus());
    }

    private static Path writePem(Path file, String label, byte[] der) throws Exception {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
        String pem = "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
        Files.writeString(file, pem);
        return file;
    }
}
