package com.securevault

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.securevault.data.db.VaultDatabase
import com.securevault.di.AppContainer

class SecureVaultApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        VaultDatabase.loadNativeLibrary()
        container = AppContainer(this)
        // App-wide foreground/background signal -> lock on background.
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.autoLockManager)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // UI hidden / system under memory pressure: never leave keys resident longer than needed.
        if (level >= TRIM_MEMORY_UI_HIDDEN) container.vaultSession.lock()
    }
}
