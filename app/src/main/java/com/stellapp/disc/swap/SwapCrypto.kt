package com.stellapp.disc.swap

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encryption for Swap transfers. The key is only ever in the QR code (shown on one screen,
 * scanned by the other camera), never sent over Wi-Fi, so someone watching the network
 * sees only scrambled bytes.
 *
 * - The track list is sealed with AES-GCM (tamper-proof).
 * - Files stream with AES-CTR, which can start at any byte, so interrupted transfers resume.
 */
object SwapCrypto {
    private const val GCM_IV = 12

    fun newKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    fun seal(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return iv + c.doFinal(plain)
    }

    fun open(key: ByteArray, sealed: ByteArray): ByteArray {
        require(sealed.size > GCM_IV) { "Too short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed.copyOfRange(0, GCM_IV)))
        return c.doFinal(sealed.copyOfRange(GCM_IV, sealed.size))
    }

    /**
     * A stream cipher for track [index], positioned at byte [offset] of the file. Encrypting and
     * decrypting are the same operation in CTR mode.
     */
    fun fileCipher(key: ByteArray, index: Int, offset: Long): Cipher {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val nonce = mac.doFinal("file:$index".toByteArray()).copyOfRange(0, 8)
        val block = offset / 16
        val iv = ByteArray(16)
        nonce.copyInto(iv, 0)
        for (i in 0 until 8) iv[15 - i] = (block ushr (8 * i)).toByte()
        val c = Cipher.getInstance("AES/CTR/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val skip = (offset % 16).toInt()
        if (skip > 0) c.update(ByteArray(skip))
        return c
    }
}
