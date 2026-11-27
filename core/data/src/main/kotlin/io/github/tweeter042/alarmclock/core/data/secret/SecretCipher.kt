package io.github.tweeter042.alarmclock.core.data.secret

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Symmetric authenticated encryption used by [KeystoreSecretStore]. Hidden behind an interface so
 * tests can use a fake (Robolectric has no AndroidKeyStore).
 *
 * [encrypt] returns a self-contained blob (IV followed by ciphertext + tag); [decrypt] takes that
 * blob back. [associatedData] is authenticated but not encrypted: the store passes the secret's key
 * name, so a value copied under another key fails to decrypt.
 */
internal interface SecretCipher {
    @Throws(GeneralSecurityException::class)
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray

    @Throws(GeneralSecurityException::class)
    fun decrypt(blob: ByteArray, associatedData: ByteArray): ByteArray
}

/**
 * AES-256/GCM/NoPadding with a non-exportable key in the Android Keystore (alias [alias]).
 *
 * The key does not require user authentication or an unlocked screen, because SmartThings tokens
 * are needed at alarm time while the phone is locked (but after the first unlock: the secrets file
 * itself lives in credential-encrypted storage).
 */
internal class KeystoreSecretCipher(private val alias: String = KEY_ALIAS) : SecretCipher {

    private val lock = Any()

    @Volatile
    private var cachedKey: SecretKey? = null

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(associatedData)
        val iv = cipher.iv
        if (iv.size != IV_BYTES) throw GeneralSecurityException("Unexpected GCM IV length ${iv.size}")
        return iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(blob: ByteArray, associatedData: ByteArray): ByteArray {
        if (blob.size <= IV_BYTES) throw GeneralSecurityException("Secret blob too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    private fun key(): SecretKey = cachedKey ?: synchronized(lock) {
        cachedKey ?: loadOrCreateKey().also { cachedKey = it }
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val KEY_ALIAS = "alarmclock_secrets"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
