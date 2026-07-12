package com.andryoga.safebox.ui.singleRecord.dynamicLayout.visualTransformers

import androidx.compose.ui.text.AnnotatedString
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VisualTransformationsUnitTest {

    private val expiryTransformer = ExpiryDateTransformation()
    private val spaceTransformer = SpaceAfterEveryFourCharsTransformation()

    @Test
    fun expiryDateTransformation_validFourDigits_shouldAddSlashAfterMonth() {
        val transformed = expiryTransformer.filter(AnnotatedString("1228"))
        assertThat(transformed.text.text).isEqualTo("12/28")
    }

    @Test
    fun expiryDateTransformation_shortString_shouldTransformAccordingToLength() {
        val oneDigit = expiryTransformer.filter(AnnotatedString("1"))
        assertThat(oneDigit.text.text).isEqualTo("1")

        val twoDigits = expiryTransformer.filter(AnnotatedString("12"))
        assertThat(twoDigits.text.text).isEqualTo("12/")
    }

    @Test
    fun expiryDateTransformation_offsetMapping_shouldMapOriginalAndTransformedOffsetsCorrectly() {
        val transformed = expiryTransformer.filter(AnnotatedString("1228"))
        val mapping = transformed.offsetMapping

        assertThat(mapping.originalToTransformed(0)).isEqualTo(0)
        assertThat(mapping.originalToTransformed(2)).isEqualTo(3)
        assertThat(mapping.originalToTransformed(4)).isEqualTo(5)

        assertThat(mapping.transformedToOriginal(0)).isEqualTo(0)
        assertThat(mapping.transformedToOriginal(3)).isEqualTo(2)
        assertThat(mapping.transformedToOriginal(5)).isEqualTo(4)
    }

    @Test
    fun spaceAfterEveryFourCharsTransformation_cardDigits_shouldInsertSpaceEveryFourCharacters() {
        val transformed = spaceTransformer.filter(AnnotatedString("4111222233334444"))
        assertThat(transformed.text.text).isEqualTo("4111 2222 3333 4444")
    }

    @Test
    fun spaceAfterEveryFourCharsTransformation_shortOrExactGroup_shouldHandleSpacesCorrectly() {
        val exactFour = spaceTransformer.filter(AnnotatedString("1234"))
        assertThat(exactFour.text.text).isEqualTo("1234")

        val fiveDigits = spaceTransformer.filter(AnnotatedString("12345"))
        assertThat(fiveDigits.text.text).isEqualTo("1234 5")
    }

    @Test
    fun spaceAfterEveryFourCharsTransformation_offsetMapping_shouldMapCorrectlyAcrossSpaces() {
        val transformed = spaceTransformer.filter(AnnotatedString("4111222233334444"))
        val mapping = transformed.offsetMapping

        assertThat(mapping.originalToTransformed(0)).isEqualTo(0)
        assertThat(mapping.originalToTransformed(4)).isEqualTo(4)
        assertThat(mapping.originalToTransformed(5)).isEqualTo(6)

        assertThat(mapping.transformedToOriginal(0)).isEqualTo(0)
        assertThat(mapping.transformedToOriginal(6)).isEqualTo(5)
    }
}
