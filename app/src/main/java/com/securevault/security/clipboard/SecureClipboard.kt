package com.securevault.security.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.CharBuffer
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Copies secrets to the clipboard and wipes them after exactly [CLEAR_DELAY_MILLIS].
 *
 * - Primary path: a coroutine in the application scope (precise timing, survives the vault
 *   locking and the activity being destroyed).
 * - Fallback: a WorkManager job, so the clipboard is still cleared if the process is killed
 *   within the 30 s window. Whichever runs first cancels the other.
 * - The clip is flagged IS_SENSITIVE so Android 13+ hides it in the copy confirmation overlay and
 *   keyboards don't surface it in clipboard suggestions.
 * - Only *our* clip is cleared when we can verify it; if the user copied something else in the
 *   meantime we leave it alone. If we can't read the clipboard (background on Android 10+), we
 *   err on the side of clearing.
 */
class SecureClipboard(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private var clearJob: Job? = null

    /** Copies [secret] (not wiped here; the caller owns it). Must be called on the main thread. */
    fun copySensitive(secret: CharArray) {
        val token = TOKEN_PREFIX + UUID.randomUUID()
        // setPrimaryClip() parcels the text to the system clipboard service synchronously, so
        // wrapping the caller's array (no copy) is safe; they may wipe it right after this call.
        // A transient String inside Parcel.writeCharSequence is outside the app's control.
        val clip = ClipData.newPlainText(token, CharBuffer.wrap(secret))
        clip.description.extras = PersistableBundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            } else {
                putBoolean(EXTRA_IS_SENSITIVE_COMPAT, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        scheduleClear(token)
    }

    private fun scheduleClear(token: String) {
        clearJob?.cancel()
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ClipboardClearWorker>()
                .setInitialDelay(CLEAR_DELAY_MILLIS + FALLBACK_GRACE_MILLIS, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_TOKEN to token))
                .build(),
        )
        clearJob = scope.launch(Dispatchers.Main) {
            delay(CLEAR_DELAY_MILLIS)
            clearIfOurs(clipboard, token)
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }

    companion object {
        const val CLEAR_DELAY_MILLIS = 30_000L
        private const val FALLBACK_GRACE_MILLIS = 2_000L
        private const val TOKEN_PREFIX = "securevault:"
        private const val WORK_NAME = "securevault.clipboard.clear"
        internal const val KEY_TOKEN = "token"
        private const val EXTRA_IS_SENSITIVE_COMPAT = "android.content.extra.IS_SENSITIVE"

        internal fun clearIfOurs(clipboard: ClipboardManager, token: String) {
            // Null when the clipboard is empty OR when we may not read it (background on
            // Android 10+). Clearing is harmless in the first case and required in the second.
            val description = runCatching { clipboard.primaryClipDescription }.getOrNull()
            if (description == null || description.label?.toString() == token) {
                clipboard.clearPrimaryClip()
            }
        }
    }
}

/** Fallback clipboard wipe that survives process death. */
class ClipboardClearWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val token = inputData.getString(SecureClipboard.KEY_TOKEN) ?: return Result.success()
        val clipboard = applicationContext.getSystemService(ClipboardManager::class.java)
        withContext(Dispatchers.Main) {
            SecureClipboard.clearIfOurs(clipboard, token)
        }
        return Result.success()
    }
}
