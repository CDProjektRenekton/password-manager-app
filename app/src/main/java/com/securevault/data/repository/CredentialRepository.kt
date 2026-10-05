package com.securevault.data.repository

import com.securevault.data.db.CredentialEntity
import com.securevault.security.crypto.AesGcm
import com.securevault.security.crypto.SecureMemory
import com.securevault.security.crypto.useThenWipe
import com.securevault.security.vault.VaultLockedException
import com.securevault.security.vault.VaultSession
import com.securevault.security.vault.VaultState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.crypto.SecretKey

/**
 * List-row projection. Holds only what the vault list must render/search; password and notes are
 * never decrypted for the list, only on demand in the details screen.
 */
data class CredentialSummary(
    val id: String,
    val title: String,
    val url: String,
    val username: String,
)

/**
 * Decrypted secret fields. Kept as CharArrays so they can be zeroed; call [wipe] as soon as the
 * screen that needed them goes away (or the vault locks).
 */
class CredentialSecrets(val password: CharArray, val notes: CharArray) {
    fun wipe() = SecureMemory.wipe(password, notes)
}

/** Input for create/update. All fields are CharArrays; the repository wipes them after encrypting. */
class CredentialDraft(
    val id: String?,
    val title: CharArray,
    val url: CharArray,
    val username: CharArray,
    val password: CharArray,
    val notes: CharArray,
) {
    fun wipe() = SecureMemory.wipe(title, url, username, password, notes)
}

class CredentialRepository(private val session: VaultSession) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSummaries(): Flow<List<CredentialSummary>> =
        session.state.flatMapLatest { state ->
            if (state !is VaultState.Unlocked) {
                flowOf(emptyList())
            } else {
                try {
                    session.withUnlocked { db, key ->
                        db.credentialDao().observeAll()
                            .map { rows -> rows.map { it.toSummary(key) } }
                            // The DB may be closed under us by an auto-lock racing this emission.
                            .catch { emit(emptyList()) }
                    }
                } catch (e: VaultLockedException) {
                    flowOf(emptyList())
                }
            }
        }.flowOn(Dispatchers.Default)

    suspend fun getSummary(id: String): CredentialSummary? = withContext(Dispatchers.Default) {
        val (db, key) = session.withUnlocked { db, key -> db to key }
        db.credentialDao().getById(id)?.toSummary(key)
    }

    /** Decrypts password + notes for one item. Caller must [CredentialSecrets.wipe]. */
    suspend fun revealSecrets(id: String): CredentialSecrets? = withContext(Dispatchers.Default) {
        val (db, key) = session.withUnlocked { db, key -> db to key }
        val row = db.credentialDao().getById(id) ?: return@withContext null
        CredentialSecrets(
            password = decryptChars(key, row.id, CredentialEntity.PASSWORD, row.password),
            notes = decryptChars(key, row.id, CredentialEntity.NOTES, row.notes),
        )
    }

    /** Encrypts and stores [draft], then wipes it. Returns the item id. */
    suspend fun save(draft: CredentialDraft): String = withContext(Dispatchers.Default) {
        try {
            val (db, key) = session.withUnlocked { db, key -> db to key }
            val dao = db.credentialDao()
            val now = System.currentTimeMillis()
            val id = draft.id ?: UUID.randomUUID().toString()
            val createdAt = draft.id?.let { dao.getById(it)?.createdAt } ?: now
            dao.upsert(
                CredentialEntity(
                    id = id,
                    title = encryptChars(key, id, CredentialEntity.TITLE, draft.title),
                    url = encryptChars(key, id, CredentialEntity.URL, draft.url),
                    username = encryptChars(key, id, CredentialEntity.USERNAME, draft.username),
                    password = encryptChars(key, id, CredentialEntity.PASSWORD, draft.password),
                    notes = encryptChars(key, id, CredentialEntity.NOTES, draft.notes),
                    createdAt = createdAt,
                    updatedAt = now,
                ),
            )
            id
        } finally {
            draft.wipe()
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.Default) {
        val (db, _) = session.withUnlocked { db, key -> db to key }
        db.credentialDao().deleteById(id)
        Unit
    }

    // ---------------------------------------------------------------------------------------------

    private fun CredentialEntity.toSummary(key: SecretKey) = CredentialSummary(
        id = id,
        // Non-secret display fields become Strings: Compose's Text() can only render Strings.
        title = decryptString(key, id, CredentialEntity.TITLE, title),
        url = decryptString(key, id, CredentialEntity.URL, url),
        username = decryptString(key, id, CredentialEntity.USERNAME, username),
    )

    private fun aad(id: String, column: String) = "$id|$column".toByteArray(Charsets.UTF_8)

    private fun encryptChars(key: SecretKey, id: String, column: String, value: CharArray): ByteArray =
        SecureMemory.toUtf8Bytes(value).useThenWipe { AesGcm.encrypt(key, it, aad(id, column)) }

    private fun decryptChars(key: SecretKey, id: String, column: String, blob: ByteArray): CharArray =
        AesGcm.decrypt(key, blob, aad(id, column)).useThenWipe(SecureMemory::toUtf8Chars)

    private fun decryptString(key: SecretKey, id: String, column: String, blob: ByteArray): String =
        AesGcm.decrypt(key, blob, aad(id, column)).useThenWipe { String(it, Charsets.UTF_8) }
}
