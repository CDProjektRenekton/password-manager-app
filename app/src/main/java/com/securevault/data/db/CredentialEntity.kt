package com.securevault.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One vault item as stored on disk.
 *
 * Two independent layers of encryption at rest:
 *  1. The whole database file is encrypted by SQLCipher (AES-256, page-level, HMAC-SHA512).
 *  2. Every user-supplied column is additionally an AES-256-GCM blob (see EncryptedPayload),
 *     encrypted with a field key that only exists in memory while the vault is unlocked, with
 *     AAD = "<id>|<column>" so blobs can't be swapped between rows or columns undetected.
 *
 * Only the random UUID and timestamps are stored as cleartext *inside* the SQLCipher file.
 */
@Entity(
    tableName = "credentials",
    indices = [Index(value = ["updated_at"])],
)
data class CredentialEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title", typeAffinity = ColumnInfo.BLOB)
    val title: ByteArray,

    @ColumnInfo(name = "url", typeAffinity = ColumnInfo.BLOB)
    val url: ByteArray,

    @ColumnInfo(name = "username", typeAffinity = ColumnInfo.BLOB)
    val username: ByteArray,

    @ColumnInfo(name = "password", typeAffinity = ColumnInfo.BLOB)
    val password: ByteArray,

    @ColumnInfo(name = "notes", typeAffinity = ColumnInfo.BLOB)
    val notes: ByteArray,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    // ByteArray uses identity equality by default; compare contents instead.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CredentialEntity) return false
        return id == other.id &&
            title.contentEquals(other.title) &&
            url.contentEquals(other.url) &&
            username.contentEquals(other.username) &&
            password.contentEquals(other.password) &&
            notes.contentEquals(other.notes) &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.contentHashCode()
        result = 31 * result + url.contentHashCode()
        result = 31 * result + username.contentHashCode()
        result = 31 * result + password.contentHashCode()
        result = 31 * result + notes.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }

    companion object Columns {
        const val TITLE = "title"
        const val URL = "url"
        const val USERNAME = "username"
        const val PASSWORD = "password"
        const val NOTES = "notes"
    }
}
