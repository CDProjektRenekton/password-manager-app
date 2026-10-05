package com.securevault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.securevault.security.password.PasswordStrengthEstimator
import com.securevault.security.password.StrengthResult
import com.securevault.ui.theme.StrengthColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext

/**
 * Live zxcvbn meter for a password [TextFieldState]. Estimation is debounced and runs on
 * Dispatchers.Default; the text is passed as a CharSequence, never converted to a String.
 */
@OptIn(FlowPreview::class)
@Composable
fun PasswordStrengthMeter(
    state: TextFieldState,
    estimator: PasswordStrengthEstimator,
    modifier: Modifier = Modifier,
    onResult: (StrengthResult) -> Unit = {},
) {
    var result by remember { mutableStateOf(StrengthResult.EMPTY) }
    val currentOnResult by rememberUpdatedState(onResult)
    LaunchedEffect(state) {
        snapshotFlow { state.text }
            .debounce(120)
            .collectLatest { text ->
                val r = withContext(Dispatchers.Default) { estimator.estimate(text) }
                result = r
                currentOnResult(r)
            }
    }
    StrengthBar(result, empty = state.text.isEmpty(), modifier = modifier)
}

@Composable
fun StrengthBar(result: StrengthResult, empty: Boolean, modifier: Modifier = Modifier) {
    val color by animateColorAsState(StrengthColors[result.score.coerceIn(0, 4)], label = "strength")
    Column(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(5) { i ->
                val active = !empty && i <= result.score
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(
                            if (active) color else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(3.dp),
                        ),
                )
            }
        }
        if (!empty) {
            Text(
                "${result.label} - offline crack time: ${result.crackTimeDisplay}",
                style = MaterialTheme.typography.bodySmall,
                color = color,
                modifier = Modifier.padding(top = 4.dp),
            )
            result.warning?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            result.suggestions.firstOrNull()?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
