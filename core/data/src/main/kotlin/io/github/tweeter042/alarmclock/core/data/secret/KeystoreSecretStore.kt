package io.github.tweeter042.alarmclock.core.data.secret

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import androidx.core.content.edit
import io.github.tweeter042.alarmclock.core.data.DataLog
import io.github.tweeter042.alarmclock.core.model.repository.SecretStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Base64

/**
 * [SecretStore] backed by Keystore-encrypted values (base64 of IV + ciphertext) in the
 * CREDENTIAL-ENCRYPTED SharedPreferences file `secrets` (excluded from Android backups by the
 * app's backup rules, and never written into JSON backups).
 *
 * Before the first unlock after a reboot, credential-encrypted storage does not exist yet: [get]
 * then returns null and [put] does nothing (both logged). Nothing on the ring path may depend on a
 * secret. Values that cannot be decoded or decrypted are removed and read as null.
 *
 * @param context a credential-encrypted context (the Application). Never pass a device-protected one.
 */
internal class KeystoreSecretStore(
    context: Context,
    private val cipher: SecretCipher = KeystoreSecretCipher(),
    private val isUserUnlocked: () -> Boolean = defaultUnlockCheck(context),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : SecretStore {

    private val appContext: Context = context.applicationContext ?: context
    private val lock = Any()

    @Volatile
    private var prefs: SharedPreferences? = null

    init {
        if (appContext.isDeviceProtectedStorage) {
            DataLog.w("SecretStore was given a device-protected context; secrets must use credential-encrypted storage")
        }
    }

    override suspend fun get(key: String): String? = withContext(io) {
        val prefs = availablePrefs("read") ?: return@withContext null
        val stored = prefs.getString(key, null) ?: return@withContext null
        try {
            cipher.decrypt(Base64.getDecoder().decode(stored), associatedData(key)).decodeToString()
        } catch (e: Exception) {
            // Bad base64, truncated blob, wrong/rotated key or tampering: the value is unusable.
            DataLog.w("Removing undecryptable secret (${DataLog.describe(e)})")
            prefs.edit(commit = true) { remove(key) }
            null
        }
    }

    /**
     * Stores (or with a null [value] removes) a secret, synchronously on disk.
     * @throws java.security.GeneralSecurityException if the Keystore cannot encrypt.
     * @throws IOException if the preferences file cannot be written.
     */
    override suspend fun put(key: String, value: String?) {
        withContext(io) {
            val prefs = availablePrefs("write") ?: return@withContext
            val editor = prefs.edit()
            if (value == null) {
                editor.remove(key)
            } else {
                val blob = cipher.encrypt(value.encodeToByteArray(), associatedData(key))
                editor.putString(key, Base64.getEncoder().encodeToString(blob))
            }
            if (!editor.commit()) {
                DataLog.e("Secret could not be written to storage")
                throw IOException("Secret could not be written to storage")
            }
        }
    }

    /** The CE preferences, or null while the user has not unlocked the device since boot. */
    private fun availablePrefs(operation: String): SharedPreferences? {
        if (!isUserUnlocked()) {
            DataLog.i("Secret $operation skipped: credential-encrypted storage is locked until first unlock")
            return null
        }
        prefs?.let { return it }
        return synchronized(lock) {
            prefs ?: try {
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { prefs = it }
            } catch (e: IllegalStateException) {
                // Thrown by the framework when CE storage is not available yet.
                DataLog.w("Secret $operation skipped: storage unavailable (${DataLog.describe(e)})")
                null
            }
        }
    }

    private fun associatedData(key: String): ByteArray = "$PREFS_NAME/$key".encodeToByteArray()

    companion object {
        /** SharedPreferences file name (`shared_prefs/secrets.xml`, excluded from Android backups). */
        const val PREFS_NAME = "secrets"

        private fun defaultUnlockCheck(context: Context): () -> Boolean {
            val userManager = context.getSystemService(UserManager::class.java)
            return { userManager?.isUserUnlocked ?: true }
        }
    }
}
