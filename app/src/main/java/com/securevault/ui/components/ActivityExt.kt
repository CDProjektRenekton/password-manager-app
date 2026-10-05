package com.securevault.ui.components

import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity

/** Unwraps Compose's LocalContext to the hosting FragmentActivity (needed by BiometricPrompt). */
tailrec fun Context.findFragmentActivity(): FragmentActivity = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> error("No FragmentActivity in context chain")
}
