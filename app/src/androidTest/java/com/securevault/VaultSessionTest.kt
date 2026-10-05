package com.securevault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.securevault.data.db.VaultDatabase
import com.securevault.data.repository.CredentialDraft
import com.securevault.data.repository.CredentialRepository
import com.securevault.security.crypto.Argon2KeyDerivation
import com.securevault.security.crypto.CryptographyManager
import com.securevault.security.vault.UnlockResult
import com.securevault.security.vault.UnlockThrottle
import com.securevault.security.vault.VaultLockedException
import com.securevault.security.vault.VaultMetadataStore
import com.securevault.security.vault.VaultSession
import com.securevault.security.vault.VaultState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end check of the real key hierarchy on a device: AndroidKeyStore, native Argon2id,
 * SQLCipher and Room. Uses its own VaultSession (not the app's), so the app-wide auto-lock
 * doesn't lock it mid-test while no activity is in the foreground.
 */
@RunWith(AndroidJUnit4::class)
class VaultSessionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var session: VaultSession
    private lateinit var repository: CredentialRepository

    private fun masterPassword() = "correct horse battery staple 42".toCharArray()

    @Before
    fun setUp() {
        resetVaultStorage(context)
        session = newSession()
        repository = CredentialRepository(session)
    }

    @After
    fun tearDown() {
        session.lock()
        resetVaultStorage(context)
    }

    private fun newSession(): VaultSession {
        val crypto = CryptographyManager(strongBoxAvailable = false)
        return VaultSession(context, crypto, Argon2KeyDerivation(), VaultMetadataStore(context, crypto), UnlockThrottle(context))
    }

    private fun draft(title: String, password: String) = CredentialDraft(
        id = null,
        title = title.toCharArray(),
        url = "https://github.com".toCharArray(),
        username = "octocat".toCharArray(),
        password = password.toCharArray(),
        notes = "recovery codes in the safe".toCharArray(),
    )

    @Test
    fun createSaveLockUnlockRoundTrip() = runBlocking {
        assertEquals(VaultState.NotInitialized, session.state.value)
        session.createVault(masterPassword())
        assertTrue(session.state.value is VaultState.Unlocked)

        val id = repository.save(draft("GitHub", "s3cret-Passw0rd!"))
        val summary = requireNotNull(repository.getSummary(id))
        assertEquals("GitHub", summary.title)
        assertEquals("octocat", summary.username)
        assertArrayEquals("s3cret-Passw0rd!".toCharArray(), requireNotNull(repository.revealSecrets(id)).password)

        session.lock()
        assertEquals(VaultState.Locked, session.state.value)
        assertThrows(VaultLockedException::class.java) { runBlocking { repository.revealSecrets(id) } }

        assertEquals(UnlockResult.WrongPassword, session.unlockWithPassword("not the password".toCharArray()))
        assertEquals(VaultState.Locked, session.state.value)

        assertEquals(UnlockResult.Success, session.unlockWithPassword(masterPassword()))
        val secrets = requireNotNull(repository.revealSecrets(id))
        assertArrayEquals("s3cret-Passw0rd!".toCharArray(), secrets.password)
        assertArrayEquals("recovery codes in the safe".toCharArray(), secrets.notes)
    }

    @Test
    fun freshProcessCanUnlockExistingVault() = runBlocking {
        session.createVault(masterPassword())
        val id = repository.save(draft("Bank", "another-Secret-9"))
        session.lock()

        // Simulates an app restart: brand-new objects, only on-disk state + Keystore survive.
        val restarted = newSession()
        assertEquals(VaultState.Locked, restarted.state.value)
        assertEquals(UnlockResult.Success, restarted.unlockWithPassword(masterPassword()))
        assertEquals("Bank", requireNotNull(CredentialRepository(restarted).getSummary(id)).title)
        restarted.lock()
    }

    @Test
    fun databaseFileHoldsNoPlaintext() = runBlocking {
        val marker = "PLAINTEXT-MARKER-7f3a"
        session.createVault(masterPassword())
        repository.save(draft(marker, marker))
        session.lock()

        val bytes = context.getDatabasePath(VaultDatabase.DATABASE_NAME).readBytes()
        assertTrue("database file missing", bytes.isNotEmpty())
        val sqliteHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        assertFalse("database is not encrypted", bytes.copyOf(sqliteHeader.size).contentEquals(sqliteHeader))
        assertFalse("plaintext found in database file", String(bytes, Charsets.ISO_8859_1).contains(marker))
    }

    @Test
    fun repeatedWrongPasswordsAreThrottled() = runBlocking {
        session.createVault(masterPassword())
        session.lock()
        repeat(5) {
            assertEquals(UnlockResult.WrongPassword, session.unlockWithPassword("wrong guess $it".toCharArray()))
        }
        assertTrue(session.unlockWithPassword(masterPassword()) is UnlockResult.Throttled)
    }
}
