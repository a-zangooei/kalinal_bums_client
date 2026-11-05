package com.example.data.crypto

import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

object RsaEncryptor {

    /**
     * Parses an RSA public key in PEM format (SubjectPublicKeyInfo) and creates a PublicKey object.
     */
    fun parsePublicKeyFromPem(pem: String): PublicKey {
        val cleanKey = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
            .trim()

        val decodedBytes = decodeBase64(cleanKey)
        val spec = X509EncodedKeySpec(decodedBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(spec)
    }

    /**
     * Encrypts plaintext using RSA PKCS#1 v1.5 padding and returns a Base64-encoded string,
     * matching the exact behavior of JSEncrypt in the Cullinan web client.
     */
    fun encrypt(plainText: String, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return encodeBase64(encryptedBytes)
    }

    /**
     * Convenience method to encrypt using a PEM string directly.
     */
    fun encryptWithPem(plainText: String, pemKey: String): String {
        val publicKey = parsePublicKeyFromPem(pemKey)
        return encrypt(plainText, publicKey)
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            android.util.Base64.decode(str, android.util.Base64.DEFAULT)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(str)
        }
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }
}
