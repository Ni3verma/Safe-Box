package com.andryoga.composeapp.ui.singleRecord.dynamicLayout.visualTransformers

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Visual transformer which adds "/" after two month digits
 * e.g.12/28
 * */
class ExpiryDateTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length >= 4) text.text.substring(0..3) else text.text
        var out = ""
        for (i in trimmed.indices) {
            out += trimmed[i]
            if (i == 1) out += "/" // Add slash after 2nd digit
        }

        val expiryDateOffsetTranslator = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                // If cursor is before index 2, no change
                if (offset < 2) return offset
                // If cursor is after index 2, account for the added slash
                if (offset <= 4) return offset + 1
                return 5
            }

            override fun transformedToOriginal(offset: Int): Int {
                // Bidirectional mapping for cursor and selection
                if (offset <= 2) return offset
                if (offset <= 5) return offset - 1
                return 4
            }
        }

        return TransformedText(AnnotatedString(out), expiryDateOffsetTranslator)
    }
}