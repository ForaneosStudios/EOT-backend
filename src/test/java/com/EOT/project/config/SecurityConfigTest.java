package com.EOT.project.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {
    @Test
    void checksSignatureIssuerAudienceAndExpiration() throws Exception {
        RSAKey signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        ECKey ecKey = new ECKeyGenerator(Curve.P_256).keyID("ec-key").generate();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] publicKeys = new JWKSet(List.of(signingKey.toPublicJWK(), ecKey.toPublicJWK()))
                .toString().getBytes(StandardCharsets.UTF_8);
        server.createContext("/auth/v1/.well-known/jwks.json", request -> {
            request.getResponseHeaders().set("Content-Type", "application/json");
            request.sendResponseHeaders(200, publicKeys.length);
            try (var body = request.getResponseBody()) { body.write(publicKeys); }
        });
        server.start();
        try {
            String issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/auth/v1";
            JwtDecoder decoder = new SecurityConfig().jwtDecoder(issuer);
            assertNotNull(decoder.decode(sign(signingKey, issuer, "authenticated", Instant.now().plusSeconds(300))));
            JWTClaimsSet ecClaims = new JWTClaimsSet.Builder().issuer(issuer).subject("test-user")
                    .audience("authenticated").expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
            SignedJWT ecToken = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("ec-key").build(), ecClaims);
            ecToken.sign(new ECDSASigner(ecKey));
            assertNotNull(decoder.decode(ecToken.serialize()));
            String wrongIssuer = sign(signingKey, issuer + "/other", "authenticated", Instant.now().plusSeconds(300));
            String wrongAudience = sign(signingKey, issuer, "other", Instant.now().plusSeconds(300));
            String expired = sign(signingKey, issuer, "authenticated", Instant.now().minusSeconds(600));
            assertThrows(JwtException.class, () -> decoder.decode(wrongIssuer));
            assertThrows(JwtException.class, () -> decoder.decode(wrongAudience));
            assertThrows(JwtException.class, () -> decoder.decode(expired));
            RSAKey otherKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
            String unknownSignature = sign(otherKey, issuer, "authenticated", Instant.now().plusSeconds(300));
            assertThrows(JwtException.class, () -> decoder.decode(unknownSignature));
        } finally {
            server.stop(0);
        }
    }

    private String sign(RSAKey key, String issuer, String audience, Instant expires) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(issuer).subject("test-user")
                .audience(audience).expirationTime(Date.from(expires)).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
