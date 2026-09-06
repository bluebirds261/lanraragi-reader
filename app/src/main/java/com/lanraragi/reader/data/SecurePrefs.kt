package com.lanraragi.reader.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API Key 的加密存储 helper（F1）。
 *
 * 用 AndroidKeyStore 生成一个随机 AES-256 密钥（GCM/NoPadding），密钥保存在硬件/系统
 * Keystore 中，永不离开安全硬件；加密后的密文仍写在 DataStore 的 api_key 字段，因此
 * settings Flow 结构、连接页回显、运行时拦截器逻辑全部保持不变——上层始终拿到明文，
 * 只有磁盘上的值是密文。
 *
 * 本改动不新增"清除凭据/忘记凭据"入口，也不改任何 UI。
 */
object SecurePrefs {

    /** 密文前缀，同时充当 v1 版本号；用于识别已加密数据并区分旧明文。 */
    const val PREFIX = "enc:v1:"

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "lanraragi_api_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    /**
     * 从 AndroidKeyStore 取密钥，不存在则生成一个随机 AES key。
     * 所有 KeyStore 操作都可能失败（例如设备不支持、被锁定），这里统一吞掉返回 null，
     * 由调用方回退明文，避免破坏首帧读取。
     */
    private fun getOrCreateKey(): SecretKey? = try {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: run {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generator.generateKey()
        }
    } catch (_: Exception) {
        null
    }

    /**
     * AES/GCM/NoPadding 加密，IV 随机 12 字节，返回 "enc:v1:" + Base64(iv + ciphertext)。
     * 失败返回 null，调用方（saveServer）回退明文存储，不阻断保存。
     */
    fun encrypt(plain: String): String? = try {
        val key = getOrCreateKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        PREFIX + Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }

    /**
     * 解密：stored 以 "enc:v1:" 开头则解码并拆出 IV + ciphertext 解密返回明文；
     * 否则（旧明文或空值）原样返回，天然兼容升级前的旧数据。
     * 解密失败不抛异常，原样返回输入，避免破坏首帧读取。
     */
    fun decrypt(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            val key = getOrCreateKey() ?: return stored
            val data = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            if (data.size <= IV_LENGTH_BYTES) return stored
            val iv = data.copyOfRange(0, IV_LENGTH_BYTES)
            val ciphertext = data.copyOfRange(IV_LENGTH_BYTES, data.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            stored
        }
    }
}
