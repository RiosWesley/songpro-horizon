package com.andrerinas.openheadunit.utils.adb;

import android.util.Base64;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

public class AdbCrypto {
    public static final int KEY_LENGTH_BITS = 2048;
    public static final int KEY_LENGTH_WORDS = 64;
    public static final byte[] SIGNATURE_PADDING;

    /** DER DigestInfo prefix for SHA-1: adbd signs a 20-byte token as a SHA-1 digest. */
    private static final byte[] SHA1_DIGEST_INFO = {
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    };

    private static final int TOKEN_LENGTH = 20;

    static {
        // PKCS#1 v1.5 block for the token: 00 01 FF..FF 00 DigestInfo, sized so that with the
        // token appended it fills the modulus exactly. The previous hand-typed table was four FF
        // bytes short, so adbd rejected every signature and asked the user to authorise again.
        int length = KEY_LENGTH_BITS / 8 - TOKEN_LENGTH;
        SIGNATURE_PADDING = new byte[length];
        SIGNATURE_PADDING[0] = 0x00;
        SIGNATURE_PADDING[1] = 0x01;
        int digestInfoStart = length - SHA1_DIGEST_INFO.length;
        for (int i = 2; i < digestInfoStart - 1; i++) {
            SIGNATURE_PADDING[i] = (byte) 0xff;
        }
        SIGNATURE_PADDING[digestInfoStart - 1] = 0x00;
        System.arraycopy(SHA1_DIGEST_INFO, 0, SIGNATURE_PADDING, digestInfoStart, SHA1_DIGEST_INFO.length);
    }

    private KeyPair keyPair;

    private static byte[] convertRsaPublicKeyToAdbFormat(RSAPublicKey rsaPublicKey) {
        BigInteger bit = BigInteger.ZERO.setBit(32);
        BigInteger modulus = rsaPublicKey.getModulus();
        BigInteger r32 = BigInteger.ZERO.setBit(KEY_LENGTH_BITS).modPow(BigInteger.valueOf(2L), modulus);
        BigInteger n0inv = modulus.remainder(bit).modInverse(bit);

        int[] n = new int[64];
        int[] rr = new int[64];

        for (int i = 0; i < 64; i++) {
            BigInteger[] rrDiv = r32.divideAndRemainder(bit);
            r32 = rrDiv[0];
            rr[i] = rrDiv[1].intValue();

            BigInteger[] nDiv = modulus.divideAndRemainder(bit);
            modulus = nDiv[0];
            n[i] = nDiv[1].intValue();
        }

        ByteBuffer buffer = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(64); // nwords
        buffer.putInt(n0inv.negate().intValue()); // n0inv
        for (int i = 0; i < 64; i++) {
            buffer.putInt(n[i]); // modulus
        }
        for (int i = 0; i < 64; i++) {
            buffer.putInt(rr[i]); // R^2 as little endian words
        }
        buffer.putInt(rsaPublicKey.getPublicExponent().intValue()); // exponent
        return buffer.array();
    }

    public static AdbCrypto generateAdbKeyPair() throws NoSuchAlgorithmException {
        AdbCrypto crypto = new AdbCrypto();
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(KEY_LENGTH_BITS);
        crypto.keyPair = keyPairGenerator.genKeyPair();
        return crypto;
    }

    public static AdbCrypto loadAdbKeyPair(File privateKeyFile, File publicKeyFile) throws Exception {
        AdbCrypto crypto = new AdbCrypto();
        byte[] privBytes = new byte[(int) privateKeyFile.length()];
        byte[] pubBytes = new byte[(int) publicKeyFile.length()];

        try (FileInputStream privIn = new FileInputStream(privateKeyFile)) {
            privIn.read(privBytes);
        }
        try (FileInputStream pubIn = new FileInputStream(publicKeyFile)) {
            pubIn.read(pubBytes);
        }

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        crypto.keyPair = new KeyPair(
            keyFactory.generatePublic(new X509EncodedKeySpec(pubBytes)),
            keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privBytes))
        );
        return crypto;
    }

    public void saveAdbKeyPair(File privateKeyFile, File publicKeyFile) throws IOException {
        try (FileOutputStream privOut = new FileOutputStream(privateKeyFile)) {
            privOut.write(this.keyPair.getPrivate().getEncoded());
        }
        try (FileOutputStream pubOut = new FileOutputStream(publicKeyFile)) {
            pubOut.write(this.keyPair.getPublic().getEncoded());
        }
    }

    public byte[] getAdbPublicKeyPayload() {
        byte[] converted = convertRsaPublicKeyToAdbFormat((RSAPublicKey) this.keyPair.getPublic());
        String base64 = Base64.encodeToString(converted, Base64.NO_WRAP);
        String keyString = base64 + " OpenHU@localhost\u0000";
        return keyString.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] signAdbTokenPayload(byte[] token) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, this.keyPair.getPrivate());
        cipher.update(SIGNATURE_PADDING);
        return cipher.doFinal(token);
    }
}
