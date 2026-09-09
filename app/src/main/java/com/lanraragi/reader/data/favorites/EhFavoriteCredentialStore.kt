package com.lanraragi.reader.data.favorites

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cookie material held only in a Keystore-encrypted private preference. */
class EhFavoriteCredentialStore(context: Context) : EhAccountConnector {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Stores a raw Cookie header; blank values remove the account. */
    fun saveCookie(cookieHeader: String) {
        val value = cookieHeader.trim()
        if (value.isEmpty()) {
            invalidateCredentialsSync()
            return
        }
        val encrypted = encrypt(value) ?: throw IllegalStateException("Android Keystore unavailable")
        preferences.edit().putString(KEY_COOKIE, encrypted).commit()
    }

    fun currentCookie(): String? = preferences.getString(KEY_COOKIE, null)?.let(::decrypt)

    override suspend fun currentCredentials(): EhCredentials? =
        currentCookie()?.takeIf(String::isNotBlank)?.let { EhCredentials(EhCookieJar(it)) }

    override suspend fun invalidateCredentials() {
        invalidateCredentialsSync()
    }

    private fun invalidateCredentialsSync() {
        preferences.edit().remove(KEY_COOKIE).commit()
    }

    private fun encrypt(value: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(value: String): String? = runCatching {
        require(value.startsWith(PREFIX))
        val payload = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
        require(payload.size > IV_LENGTH_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(TAG_LENGTH_BITS, payload.copyOfRange(0, IV_LENGTH_BYTES)),
        )
        String(cipher.doFinal(payload.copyOfRange(IV_LENGTH_BYTES, payload.size)), Charsets.UTF_8)
    }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        private const val PREFS_NAME = "eh_favorite_credentials"
        private const val KEY_COOKIE = "cookie"
        private const val KEY_ALIAS = "lanraragi_eh_favorite_cookie"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFIX = "enc:v1:"
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
    }
}

/** Opaque runtime wrapper; its value is never logged or persisted directly. */
class EhCookieJar internal constructor(internal val cookieHeader: String) {
    override fun toString(): String = "EhCookieJar(redacted)"
}
