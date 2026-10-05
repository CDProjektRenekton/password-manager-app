package com.securevault

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import com.securevault.ui.components.LocalUserActivity
import com.securevault.ui.navigation.AppNavHost
import com.securevault.ui.theme.SecureVaultTheme

/**
 * Single activity. FragmentActivity (not ComponentActivity) because BiometricPrompt hosts its
 * fallback UI in a Fragment.
 */
class MainActivity : FragmentActivity() {

    private val container get() = (application as SecureVaultApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        // FLAG_SECURE before any content exists: blocks screenshots and screen recording/casting,
        // and makes the Recents thumbnail blank. Compose Dialogs, Popups and BottomSheets default
        // to SecureFlagPolicy.Inherit, so every window this app opens is covered.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Belt and braces for the Recents snapshot on 13+.
            setRecentsScreenshotEnabled(false)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val autoLock = container.autoLockManager
        setContent {
            SecureVaultTheme {
                CompositionLocalProvider(LocalUserActivity provides autoLock::onUserInteraction) {
                    Surface {
                        AppNavHost(container.vaultSession)
                    }
                }
            }
        }
    }

    /** Every touch / key / trackball event resets the 60 s inactivity timer. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        container.autoLockManager.onUserInteraction()
    }
}
