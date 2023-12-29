package com.andryoga.safebox.ui.view.home.child.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .height(70.dp)
            .padding(start = 4.dp, top = 8.dp)
            .fillMaxWidth()
            .background(Color.Red),
        horizontalArrangement = Arrangement.End,
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
                    tint = Color.White,
                )
            },
        )
    }
}
