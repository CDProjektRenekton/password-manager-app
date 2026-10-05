package com.securevault.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialDao {

    /**
     * Columns are ciphertext, so SQL can't filter or sort by title; search/sort happen in memory
     * after decryption (see CredentialRepository). This is the price of field-level encryption.
     */
    @Query("SELECT * FROM credentials ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<CredentialEntity>>

    @Query("SELECT * FROM credentials WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CredentialEntity?

    @Upsert
    suspend fun upsert(entity: CredentialEntity)

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
