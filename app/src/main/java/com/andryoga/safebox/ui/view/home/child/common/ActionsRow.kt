package com.andryoga.safebox.ui.view.home.child.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ActionsRow(
    actionIconSize: Dp = 56.dp,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .height(64.dp)
            .padding(4.dp)
            .background(Color.Red)
    ) {
        IconButton(
            modifier = Modifier.size(actionIconSize),
            onClick = {
                onDelete()
            },
            content = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        )
    }
}
