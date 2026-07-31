package com.andryoga.safebox.data.db

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.util.Date

class ConvertersTest {

    private lateinit var converters: Converters

    @Before
    fun setUp() {
        converters = Converters()
    }

    @Test
    fun fromTimestamp_withPositiveTimestamp_returnsMatchingDate() {
        val timestamp = 1773918000000L

        val resultDate = converters.fromTimestamp(timestamp)

        assertThat(resultDate).isEqualTo(Date(timestamp))
        assertThat(resultDate?.time).isEqualTo(timestamp)
    }

    @Test
    fun toTimestamp_withValidDate_returnsMatchingEpochMillis() {
        val timestamp = 1773918000000L
        val date = Date(timestamp)

        val resultTimestamp = converters.toTimestamp(date)

        assertThat(resultTimestamp).isEqualTo(timestamp)
    }

    @Test
    fun roundTrip_fromTimestampToDateAndBack_preservesTimestamp() {
        val originalTimestamp = 1680000000123L

        val date = converters.fromTimestamp(originalTimestamp)
        val resultTimestamp = converters.toTimestamp(date)

        assertThat(resultTimestamp).isEqualTo(originalTimestamp)
    }

    @Test
    fun roundTrip_fromDateToTimestampAndBack_preservesDate() {
        val originalDate = Date(1680000000123L)

        val timestamp = converters.toTimestamp(originalDate)
        val resultDate = converters.fromTimestamp(timestamp)

        assertThat(resultDate).isEqualTo(originalDate)
    }

    @Test
    fun fromTimestamp_withNull_returnsNull() {
        val result = converters.fromTimestamp(null)

        assertThat(result).isNull()
    }

    @Test
    fun toTimestamp_withNull_returnsNull() {
        val result = converters.toTimestamp(null)

        assertThat(result).isNull()
    }

    @Test
    fun fromTimestamp_withZeroEpochTimestamp_returnsEpochDate() {
        val zeroTimestamp = 0L

        val resultDate = converters.fromTimestamp(zeroTimestamp)
        val resultTimestamp = converters.toTimestamp(resultDate)

        assertThat(resultDate).isEqualTo(Date(0L))
        assertThat(resultTimestamp).isEqualTo(0L)
    }

    @Test
    fun fromTimestamp_withNegativeTimestamp_returnsPreEpochDate() {
        val negativeTimestamp = -86400000L // 1 day before Jan 1, 1970

        val resultDate = converters.fromTimestamp(negativeTimestamp)
        val resultTimestamp = converters.toTimestamp(resultDate)

        assertThat(resultDate).isEqualTo(Date(negativeTimestamp))
        assertThat(resultTimestamp).isEqualTo(negativeTimestamp)
    }

    @Test
    fun fromTimestampAndToTimestamp_withMaxLongBoundary_preservesValue() {
        val maxTimestamp = Long.MAX_VALUE

        val date = converters.fromTimestamp(maxTimestamp)
        val resultTimestamp = converters.toTimestamp(date)

        assertThat(resultTimestamp).isEqualTo(maxTimestamp)
    }

    @Test
    fun fromTimestampAndToTimestamp_withMinLongBoundary_preservesValue() {
        val minTimestamp = Long.MIN_VALUE

        val date = converters.fromTimestamp(minTimestamp)
        val resultTimestamp = converters.toTimestamp(date)

        assertThat(resultTimestamp).isEqualTo(minTimestamp)
    }
}
