package com.rance.galcommunity.identity.security;

import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final String kid;
    private final RSAKey rsaKey;

    public JwtKeyProvider(AppProperties.Jwt props) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            String privateMaterial = readKey(props.privateKey(), props.privateKeyFile(), "private");
            String publicMaterial = readKey(props.publicKey(), props.publicKeyFile(), "public");
            boolean hasPrivate = privateMaterial != null && !privateMaterial.isBlank();
            boolean hasPublic = publicMaterial != null && !publicMaterial.isBlank();

            if (!hasPrivate && hasPublic) {
                throw new IllegalStateException(
                        "Public key configured without private key; token signing requires a private key");
            }
            if (!hasPrivate && props.requireConfiguredKeys()) {
                throw new IllegalStateException(
                        "JWT private key is required in this environment; set JWT_PRIVATE_KEY or JWT_PRIVATE_KEY_FILE");
            }

            if (hasPrivate) {
                privateKey = (RSAPrivateKey) keyFactory.generatePrivate(
                        new PKCS8EncodedKeySpec(decodePem(privateMaterial)));
                if (hasPublic) {
                    publicKey = (RSAPublicKey) keyFactory.generatePublic(
                            new X509EncodedKeySpec(decodePem(publicMaterial)));
                } else if (privateKey instanceof RSAPrivateCrtKey crt) {
                    publicKey = (RSAPublicKey) keyFactory.generatePublic(
                            new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
                } else {
                    throw new IllegalStateException("Public key is required when private key is not CRT format");
                }
            } else {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair pair = generator.generateKeyPair();
                privateKey = (RSAPrivateKey) pair.getPrivate();
                publicKey = (RSAPublicKey) pair.getPublic();
                log.warn("JWT keys are not configured; generated ephemeral RSA key. Do not use in production.");
            }

            kid = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded()))
                    .substring(0, 16);
            rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(kid).build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize JWT keys", e);
        }
    }

    public RSAKey rsaKey() {
        return rsaKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public String kid() {
        return kid;
    }

    private static String readKey(String inline, String file, String kind) {
        if (inline != null && !inline.isBlank()) {
            return inline;
        }
        if (file == null || file.isBlank()) {
            return null;
        }
        try {
            return Files.readString(Path.of(file.trim()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read JWT " + kind + " key file: " + file.trim(), e);
        }
    }

    private static byte[] decodePem(String pem) {
        String body = pem
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
