package io.github.tweeter042.alarmclock.core.data.secret

import android.content.Context
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.model.repository.SecretStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.AEADBadTagException

/**
 * Reversible stand-in for the Keystore cipher (Robolectric has no AndroidKeyStore). It keeps the
 * properties the store relies on: output differs from the input, associated data is authenticated.
 */
private class FakeCipher : SecretCipher {
    var encryptCalls = 0

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        encryptCalls++
        return MAGIC + tag(associatedData) + plaintext.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }

    override fun decrypt(blob: ByteArray, associatedData: ByteArray): ByteArray {
        if (blob.size < MAGIC.size + TAG_BYTES || !blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw GeneralSecurityException("not a fake blob")
        }
        val tag = blob.copyOfRange(MAGIC.size, MAGIC.size + TAG_BYTES)
        if (!tag.contentEquals(tag(associatedData))) throw AEADBadTagException("associated data mismatch")
        return blob.copyOfRange(MAGIC.size + TAG_BYTES, blob.size).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }

    private fun tag(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).copyOf(TAG_BYTES)

    private companion object {
        val MAGIC = byteArrayOf(0x46, 0x4B)
        const val TAG_BYTES = 4
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SecretStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(KeystoreSecretStore.PREFS_NAME, Context.MODE_PRIVATE)

    private fun store(cipher: SecretCipher = FakeCipher(), unlocked: () -> Boolean = { true }) =
        KeystoreSecretStore(context, cipher, unlocked)

    @Test
    fun roundTripStoresOnlyCiphertext() = runTest {
        val store = store()
        store.put(SecretStore.SMARTTHINGS_ACCESS_TOKEN, "token-123")

        assertEquals("token-123", store.get(SecretStore.SMARTTHINGS_ACCESS_TOKEN))
        val raw = prefs.getString(SecretStore.SMARTTHINGS_ACCESS_TOKEN, null)!!
        assertFalse("plaintext must never be stored", raw.contains("token-123"))
        assertTrue(Base64.getDecoder().decode(raw).isNotEmpty())
    }

    @Test
    fun valuesSurviveANewStoreInstance() = runTest {
        val cipher = FakeCipher()
        store(cipher).put(SecretStore.SMARTTHINGS_REFRESH_TOKEN, "refresh-ทดสอบ-✓")
        assertEquals("refresh-ทดสอบ-✓", store(cipher).get(SecretStore.SMARTTHINGS_REFRESH_TOKEN))
    }

    @Test
    fun nullRemovesTheKey() = runTest {
        val store = store()
        store.put(SecretStore.SMARTTHINGS_CLIENT_SECRET, "s3cret")
        store.put(SecretStore.SMARTTHINGS_CLIENT_SECRET, null)

        assertNull(store.get(SecretStore.SMARTTHINGS_CLIENT_SECRET))
        assertFalse(prefs.contains(SecretStore.SMARTTHINGS_CLIENT_SECRET))
        assertNull(store.get("never.stored"))
    }

    @Test
    fun corruptValuesReadAsNullAndAreRemoved() = runTest {
        prefs.edit()
            .putString("not.base64", "%%%***")
            .putString("not.ours", Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)))
            .commit()
        val store = store()

        assertNull(store.get("not.base64"))
        assertNull(store.get("not.ours"))
        assertFalse(prefs.contains("not.base64"))
        assertFalse(prefs.contains("not.ours"))
    }

    @Test
    fun aValueCopiedUnderAnotherKeyDoesNotDecrypt() = runTest {
        val store = store()
        store.put(SecretStore.SMARTTHINGS_ACCESS_TOKEN, "access")
        val raw = prefs.getString(SecretStore.SMARTTHINGS_ACCESS_TOKEN, null)
        prefs.edit().putString(SecretStore.SMARTTHINGS_REFRESH_TOKEN, raw).commit()

        assertNull(store.get(SecretStore.SMARTTHINGS_REFRESH_TOKEN))
        assertEquals("access", store.get(SecretStore.SMARTTHINGS_ACCESS_TOKEN))
    }

    @Test
    fun lockedUserReadsNullAndWritesNothing() = runTest {
        var unlocked = true
        val cipher = FakeCipher()
        val store = store(cipher) { unlocked }
        store.put(SecretStore.SMARTTHINGS_ACCESS_TOKEN, "before-reboot")

        unlocked = false
        assertNull(store.get(SecretStore.SMARTTHINGS_ACCESS_TOKEN))
        store.put(SecretStore.SMARTTHINGS_ACCESS_TOKEN, "while-locked")
        store.put(SecretStore.SMARTTHINGS_REFRESH_TOKEN, null)
        assertEquals("no encryption attempted while locked", 1, cipher.encryptCalls)

        unlocked = true
        assertEquals("before-reboot", store.get(SecretStore.SMARTTHINGS_ACCESS_TOKEN))
    }

    @Test
    fun lockedUserFromTheRealUserManager() = runTest {
        val userManager = context.getSystemService(UserManager::class.java)
        val store = KeystoreSecretStore(context, FakeCipher())
        store.put("k", "v")

        shadowOf(userManager).setUserUnlocked(false)
        try {
            assertNull(store.get("k"))
            store.put("k", "changed")
        } finally {
            shadowOf(userManager).setUserUnlocked(true)
        }
        assertEquals("v", store.get("k"))
    }

    @Test
    fun keystoreCipherRejectsTruncatedBlobsBeforeTouchingTheKeystore() {
        // The production cipher needs AndroidKeyStore (not available under Robolectric); a blob that
        // cannot even hold the 12-byte GCM IV is rejected up front, which the store turns into null.
        val error = runCatching { KeystoreSecretCipher().decrypt(ByteArray(12), byteArrayOf()) }.exceptionOrNull()
        assertTrue(error is GeneralSecurityException)
        assertEquals("alarmclock_secrets", KeystoreSecretCipher.KEY_ALIAS)
    }

    @Test
    fun fakeCipherSanity() {
        val cipher = FakeCipher()
        val blob = cipher.encrypt("hi".encodeToByteArray(), "a".encodeToByteArray())
        assertArrayEquals("hi".encodeToByteArray(), cipher.decrypt(blob, "a".encodeToByteArray()))
    }
}
