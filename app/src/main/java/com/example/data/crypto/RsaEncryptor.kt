package com.example.data.crypto

import com.example.data.logger.AppLogger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

object RsaEncryptor {

    /**
     * Parses an RSA public key in PEM format (SubjectPublicKeyInfo) and creates a PublicKey object.
     */
    fun parsePublicKeyFromPem(pem: String): PublicKey {
        try {
            val cleanKey = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace(" ", "")
                .trim()

            if (cleanKey.isEmpty()) {
                AppLogger.logWarn("RsaEncryptor", "ParsePublicKey", "Empty RSA public key PEM supplied")
            }

            val decodedBytes = decodeBase64(cleanKey)
            val spec = X509EncodedKeySpec(decodedBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val key = keyFactory.generatePublic(spec)
            AppLogger.logDebug("RsaEncryptor", "ParsePublicKey", "Parsed RSA public key (${decodedBytes.size} DER bytes)")
            return key
        } catch (e: Exception) {
            AppLogger.logError("RsaEncryptor", "Failed to parse RSA public key PEM: ${e.message}", e, operation = "ParsePublicKey")
            throw e
        }
    }

    /**
     * Encrypts plaintext using RSA PKCS#1 v1.5 padding and returns a Base64-encoded string,
     * matching the exact behavior of JSEncrypt in the Cullinan web client.
     */
    fun encrypt(plainText: String, publicKey: PublicKey): String {
        return try {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val b64 = encodeBase64(encryptedBytes)
            AppLogger.logDebug("RsaEncryptor", "Encrypt", "Encrypted credential payload (inputLen=${plainText.length}, cipherB64Len=${b64.length})")
            b64
        } catch (e: Exception) {
            AppLogger.logError("RsaEncryptor", "RSA encryption failed: ${e.message}", e, operation = "Encrypt")
            throw e
        }
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
