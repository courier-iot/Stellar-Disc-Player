package com.stellapp.disc.uplink

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the YouTube sign-in cookies encrypted with a key that never leaves the phone's
 * secure hardware (Android Keystore). yt-dlp needs a plain cookies.txt, so each run gets a
 * short-lived plain copy in no-backup storage that is deleted straight afterwards.
 */
object CookieVault {
    private const val ALIAS = "stellar_disc_cookies"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun enc(context: Context) = File(context.applicationContext.filesDir, "yt_cookies.enc")
    /** The old unencrypted file (v2.7 and earlier), and the fallback on phones without a keystore. */
    private fun plain(context: Context) = File(context.applicationContext.filesDir, "yt_cookies.txt")
    private fun tempDir(context: Context) = context.applicationContext.noBackupFilesDir

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun has(context: Context): Boolean =
        listOf(enc(context), plain(context)).any { it.exists() && it.length() > 0 }

    fun save(context: Context, text: String) {
        try {
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.ENCRYPT_MODE, key())
            val iv = c.iv
            val data = c.doFinal(text.toByteArray(Charsets.UTF_8))
            enc(context).writeBytes(byteArrayOf(iv.size.toByte()) + iv + data)
            plain(context).delete()
        } catch (e: Exception) {
            // No working keystore on this phone: keep the app-private file so sign-in still works.
            plain(context).writeText(text)
            enc(context).delete()
        }
    }

    fun load(context: Context): String? {
        val old = plain(context)
        if (old.exists() && old.length() > 0 && !enc(context).exists()) {
            val text = old.readText()
            save(context, text) // upgrades the old plain file to the encrypted one
            return text
        }
        val f = enc(context)
        if (!f.exists() || f.length() == 0L) return if (old.exists()) old.readText() else null
        return try {
            val bytes = f.readBytes()
            val ivLen = bytes[0].toInt()
            val iv = bytes.copyOfRange(1, 1 + ivLen)
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(c.doFinal(bytes.copyOfRange(1 + ivLen, bytes.size)), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * False when a saved sign-in exists but can no longer be unlocked (the phone's key store was
     * reset by a system update or restore). The unreadable file is removed so the app stops
     * pretending to be signed in.
     */
    fun verify(context: Context): Boolean {
        if (!has(context)) return true
        if (load(context) != null) return true
        clear(context)
        return false
    }

    fun clear(context: Context) {
        enc(context).delete()
        plain(context).delete()
        cleanup(context)
    }

    /** A plain copy for one yt-dlp run. Delete it when the run ends. */
    fun tempPlainCopy(context: Context): File? {
        val text = load(context) ?: return null
        return File(tempDir(context), "ck_${System.nanoTime()}.txt").apply { writeText(text) }
    }

    /** Removes plain copies left behind if the app was killed mid-download. */
    fun cleanup(context: Context) {
        tempDir(context).listFiles { f -> f.name.startsWith("ck_") }?.forEach { it.delete() }
    }
}
