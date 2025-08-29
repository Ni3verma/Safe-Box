package com.andryoga.composeapp.ui.core

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun MandatoryText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            append(text)
            withStyle(
                style = SpanStyle(
                    color = Color.Red,
                    baselineShift = BaselineShift.Superscript,
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                )
            ) {
                append("*")
            }
        },
        modifier = modifier
    )
}

@Preview
@Composable
fun MandatoryTextPreview() {
    MandatoryText(text = "Mandatory text")
}