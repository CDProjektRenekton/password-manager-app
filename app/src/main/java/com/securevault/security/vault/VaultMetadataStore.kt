package com.securevault.security.vault

import android.content.Context
import androidx.core.util.AtomicFile
import com.securevault.security.crypto.CryptographyManager
import com.securevault.security.crypto.KdfParams
import org.json.JSONObject
import java.io.File
import java.util.Base64

/**
 * Non-secret-but-sensitive vault metadata: Argon2id salt/params and the *wrapped* vault key(s).
 *
 * Nothing here is plaintext key material, but the file is still sealed with the hardware,
 * device-bound Keystore key so that a copied data directory (backup leak, forensic image) can't be
 * used to brute-force the master password offline: the attacker would also need this exact
 * device's TEE/StrongBox, unlocked.
 */
class VaultMetadataStore(
    context: Context,
    private val cryptographyManager: CryptographyManager,
) {
    private val file = AtomicFile(File(File(context.noBackupFilesDir, "vault"), "vault.meta"))

    fun exists(): Boolean = file.baseFile.exists()

    fun read(): VaultMetadata {
        val sealed = file.readFully()
        val json = cryptographyManager.decryptWithDeviceKey(
            CryptographyManager.ALIAS_DEVICE_KEY,
            sealed,
            AAD,
        )
        return VaultMetadata.fromJson(JSONObject(String(json, Charsets.UTF_8)))
    }

    fun write(metadata: VaultMetadata) {
        val json = metadata.toJson().toString().toByteArray(Charsets.UTF_8)
        val sealed = cryptographyManager.encryptWithDeviceKey(CryptographyManager.ALIAS_DEVICE_KEY, json, AAD)
        file.baseFile.parentFile?.mkdirs()
        val stream = file.startWrite()
        try {
            stream.write(sealed)
            file.finishWrite(stream) // fsync + atomic rename: never a half-written metadata file.
        } catch (t: Throwable) {
            file.failWrite(stream)
            throw t
        }
    }

    fun delete() {
        file.delete()
    }

    private companion object {
        val AAD = "securevault.metadata.v1".toByteArray(Charsets.UTF_8)
    }
}

class VaultMetadata(
    val kdfParams: KdfParams,
    /** AES-256-GCM(Argon2id(masterPassword), vaultKey), see VaultSession.AAD_PASSWORD_WRAP. */
    val passwordWrappedVaultKey: ByteArray,
    /** AES-256-GCM(biometric-bound Keystore key, vaultKey), or null when biometrics are off. */
    val biometricWrappedVaultKey: ByteArray?,
) {
    fun copy(biometricWrappedVaultKey: ByteArray?): VaultMetadata =
        VaultMetadata(kdfParams, passwordWrappedVaultKey, biometricWrappedVaultKey)

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", VERSION)
        put("kdf", JSONObject().apply {
            put("algorithm", "argon2id")
            put("salt", b64(kdfParams.salt))
            put("memoryKiB", kdfParams.memoryKiB)
            put("iterations", kdfParams.iterations)
            put("parallelism", kdfParams.parallelism)
        })
        put("passwordWrappedVaultKey", b64(passwordWrappedVaultKey))
        biometricWrappedVaultKey?.let { put("biometricWrappedVaultKey", b64(it)) }
    }

    companion object {
        private const val VERSION = 1
        private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
        private fun unb64(value: String) = Base64.getDecoder().decode(value)

        fun fromJson(json: JSONObject): VaultMetadata {
            require(json.getInt("version") == VERSION) { "Unsupported vault metadata version" }
            val kdf = json.getJSONObject("kdf")
            require(kdf.getString("algorithm") == "argon2id")
            return VaultMetadata(
                kdfParams = KdfParams(
                    salt = unb64(kdf.getString("salt")),
                    memoryKiB = kdf.getInt("memoryKiB"),
                    iterations = kdf.getInt("iterations"),
                    parallelism = kdf.getInt("parallelism"),
                ),
                passwordWrappedVaultKey = unb64(json.getString("passwordWrappedVaultKey")),
                biometricWrappedVaultKey = json.optString("biometricWrappedVaultKey")
                    .takeIf { it.isNotEmpty() }
                    ?.let(::unb64),
            )
        }
    }
}
