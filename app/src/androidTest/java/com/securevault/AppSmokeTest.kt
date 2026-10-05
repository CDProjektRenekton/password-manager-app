package com.securevault

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith

/**
 * Drives the real UI on a device: first-run setup, add a credential, lock, unlock.
 * Requires a fresh install (CI emulator), because the app decides between setup and unlock at
 * process start.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(object : TestWatcher() {
            override fun starting(description: Description) =
                resetVaultStorage(InstrumentationRegistry.getInstrumentation().targetContext)
        })
        .around(compose)

    @After
    fun tearDown() = resetVaultStorage(InstrumentationRegistry.getInstrumentation().targetContext)

    private val master = "correct horse battery staple 42"

    private fun waitForText(text: String, timeoutMillis: Long = 30_000) =
        compose.waitUntil(timeoutMillis) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun field(index: Int): SemanticsNodeInteraction =
        compose.onAllNodes(hasSetTextAction())[index]

    @Test
    fun setupAddLockUnlock() {
        // First run: create the vault (Argon2id takes a moment).
        waitForText("Create your vault")
        field(0).performTextInput(master)
        field(1).performTextInput(master)
        compose.onNode(hasText("Create vault") and hasClickAction()).performClick()
        waitForText("No credentials yet. Tap + to add one.")

        // Add a credential.
        compose.onNode(hasContentDescription("Add credential")).performClick()
        waitForText("New credential")
        field(0).performTextInput("Example account")
        field(1).performTextInput("https://example.com")
        field(2).performTextInput("alice")
        field(3).performTextInput("Tr0ub4dor&3-but-longer")
        compose.onNode(hasContentDescription("Save")).performClick() // top-bar action, always on screen
        waitForText("Website / app") // details screen
        compose.onNode(hasContentDescription("Back")).performClick()
        waitForText("Example account")

        // Lock, then unlock with the master password.
        compose.onNode(hasContentDescription("Lock vault")).performClick()
        waitForText("Vault locked")
        field(0).performTextInput(master)
        compose.onNode(hasText("Unlock") and hasClickAction()).performClick()
        waitForText("Example account")
    }
}
