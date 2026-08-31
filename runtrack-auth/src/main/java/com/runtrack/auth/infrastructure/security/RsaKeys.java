package com.runtrack.auth.infrastructure.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/** Chargement des clés RSA au format PEM, et génération d'une paire éphémère pour le poste local. */
final class RsaKeys {

    private static final int EPHEMERAL_KEY_SIZE = 2_048;

    private RsaKeys() {
    }

    static RSAPrivateKey readPrivateKey(ResourceLoader loader, String location) {
        byte[] der = readDer(loader, location, "PRIVATE KEY");
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Clé privée RSA illisible : " + location, e);
        }
    }

    static RSAPublicKey readPublicKey(ResourceLoader loader, String location) {
        byte[] der = readDer(loader, location, "PUBLIC KEY");
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Clé publique RSA illisible : " + location, e);
        }
    }

    static KeyPair generateEphemeral() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(EPHEMERAL_KEY_SIZE);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA doit être disponible sur toute JVM", e);
        }
    }

    private static byte[] readDer(ResourceLoader loader, String location, String label) {
        Resource resource = loader.getResource(location);
        try (InputStream stream = resource.getInputStream()) {
            String pem = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            String base64 = pem
                    .replace("-----BEGIN " + label + "-----", "")
                    .replace("-----END " + label + "-----", "")
                    .replaceAll("\\s", "");
            return Base64.getDecoder().decode(base64);
        } catch (IOException e) {
            throw new IllegalStateException("Clé introuvable : " + location, e);
        }
    }
}
