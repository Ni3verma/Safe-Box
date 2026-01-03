package com.andryoga.composeapp.ui.singleRecord.dynamicLayout.visualTransformers

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Visual transformer which adds space after every 4 chars.
 * e.g. use case is Credit card number. 1322 4223 422 ...
 * It is easier to read or tell someone if we have a space after 4 chars.
 * */
class SpaceAfterEveryFourCharsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val rawText = text.text
        var out = ""

        // Build the string with spaces after every 4th character
        for (i in rawText.indices) {
            out += rawText[i]
            if (i % 4 == 3 && i != rawText.lastIndex) {
                out += " "
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                // For every 4 chars, the transformed index shifts by 1 space
                val spaces = if (offset <= 0) 0 else (offset - 1) / 4
                return offset + spaces
            }

            override fun transformedToOriginal(offset: Int): Int {
                // Shift back by the number of spaces present before the cursor
                val spaces = if (offset <= 0) 0 else (offset / 5)
                return offset - spaces
            }
        }

        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}