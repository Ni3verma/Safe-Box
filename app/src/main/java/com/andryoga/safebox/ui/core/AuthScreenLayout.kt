package com.andryoga.safebox.ui.core

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.ui.core.motion.entrancePopIn
import com.andryoga.safebox.ui.core.motion.entranceSlideIn
import com.andryoga.safebox.ui.core.motion.rememberEntranceState

/**
 * Shared chrome of the two authentication screens (login and signup): the animated gradient
 * header with a greeting, and a centred card headed by the lock glyph that holds the form.
 *
 * On first appearance the pieces enter in sequence - greeting from above, card from below, then
 * the lock pops in - which makes the hand-off from the loading screen (same background, no card)
 * read as the form arriving rather than a hard cut. The choreography plays once per screen
 * instance; see [rememberEntranceState].
 *
 * @param title Greeting drawn over the gradient header.
 * @param content The form, laid out in a centred column inside the card below the lock glyph.
 */
@Composable
fun AuthScreenLayout(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val entrance = rememberEntranceState()
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedCurveBackground()
        AnimatedVisibility(
            visibleState = entrance,
            enter = entranceSlideIn(order = 0, fromTop = true),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 90.dp),
        ) {
            Text(
                text = title,
                color = colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        AnimatedVisibility(
            visibleState = entrance,
            enter = entranceSlideIn(order = 1),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .imePadding(),
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Scale and alpha only, so the glyph occupies its full slot from the first frame
                    // and the form below never shifts while it pops in.
                    AnimatedVisibility(
                        visibleState = entrance,
                        enter = entrancePopIn(order = 2),
                    ) {
                        LockGlyph()
                    }
                    content()
                }
            }
        }
    }
}

private val LOCK_GLYPH_SIZE = 80.dp

@Composable
private fun LockGlyph() {
    Icon(
        imageVector = Icons.Filled.Lock,
        contentDescription = null,
        modifier = Modifier
            .size(LOCK_GLYPH_SIZE)
            .background(
                color = colorScheme.primary.copy(alpha = 0.8f),
                shape = RoundedCornerShape(percent = 50),
            )
            .padding(16.dp),
        tint = Color.White,
    )
}
