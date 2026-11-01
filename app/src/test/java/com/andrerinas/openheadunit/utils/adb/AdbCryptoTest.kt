package com.andrerinas.openheadunit.utils.adb

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.io.path.createTempDirectory

class AdbCryptoTest {

    /**
     * adbd checks the AUTH signature as PKCS#1 v1.5 over SHA-1 DigestInfo + the 20-byte token.
     * A malformed block makes adbd fall back to asking the user every time, even for a key that
     * was "always allowed", so the signature must verify against the JCA's own implementation.
     */
    @Test
    fun tokenSignatureIsStandardPkcs1Sha1() {
        val dir = createTempDirectory("adbcrypto").toFile()
        try {
            val crypto = AdbCrypto.generateAdbKeyPair()
            val priv = File(dir, "priv")
            val pub = File(dir, "pub")
            crypto.saveAdbKeyPair(priv, pub)
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(pub.readBytes()))

            val token = ByteArray(20) { it.toByte() }
            val signature = crypto.signAdbTokenPayload(token)

            val sha1DigestInfo = byteArrayOf(
                0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
            )
            val verifier = Signature.getInstance("NONEwithRSA").apply {
                initVerify(publicKey)
                update(sha1DigestInfo + token)
            }
            assertTrue(verifier.verify(signature))
        } finally {
            dir.deleteRecursively()
        }
    }
}
