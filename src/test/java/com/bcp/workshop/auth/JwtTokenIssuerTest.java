package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(JwtTestProfile.class)
class JwtTokenIssuerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    JwtTokenIssuer issuer;

    @Test
    void issuesRs256TokenVerifiableWithPublicKey() throws Exception {
        JwtTokenIssuer.IssuedToken issued = issuer.issue("student", "STUDENT", "angular-workshop");
        String[] parts = issued.token().split("\\.");
        assertEquals(3, parts.length);

        JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
        assertEquals("RS256", header.get("alg").asText());

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(readPublicKey());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(parts[2])));
    }

    @Test
    void issuesRequiredClaimsWithConsistentLifetime() throws Exception {
        JwtTokenIssuer.IssuedToken issued = issuer.issue("student", "STUDENT", "angular-workshop");
        String payload = issued.token().split("\\.")[1];
        JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(payload));

        assertEquals("student", claims.get("sub").asText());
        assertEquals("angular-workshop", claims.get("client").asText());
        assertTrue(claims.get("groups").isArray());
        assertEquals("STUDENT", claims.get("groups").get(0).asText());
        assertTrue(claims.get("exp").asLong() > claims.get("iat").asLong());
        assertEquals(3600, issued.expiresIn());
        assertEquals(3600, claims.get("exp").asLong() - claims.get("iat").asLong());
    }

    private static PublicKey readPublicKey() throws Exception {
        String pem = Files.readString(JwtTestProfile.PUBLIC_KEY);
        String base64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }
}
