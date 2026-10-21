package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

public class JwtTestProfile implements QuarkusTestProfile {
    static final Path KEY_DIRECTORY = Path.of("target", "test-keys");
    static final Path PRIVATE_KEY = KEY_DIRECTORY.resolve("auth-test-private.pem");
    static final Path PUBLIC_KEY = KEY_DIRECTORY.resolve("auth-test-public.pem");

    static {
        try {
            Files.createDirectories(KEY_DIRECTORY);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            Files.writeString(PRIVATE_KEY, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
            Files.writeString(PUBLIC_KEY, pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo generar el par de claves de prueba", error);
        }
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("smallrye.jwt.sign.key.location", PRIVATE_KEY.toAbsolutePath().toString());
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
    }
}
