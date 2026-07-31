package com.andryoga.safebox.security

import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.Base64 as JvmBase64

/**
 * JUnit4 TestWatcher Rule that mocks [android.util.Base64] using JVM's [java.util.Base64]
 * for local desktop unit tests.
 */
class Base64Rule : TestWatcher() {

    override fun starting(description: Description) {
        mockBase64()
    }

    override fun finished(description: Description) {
        unmockBase64()
    }

    companion object {
        fun mockBase64() {
            mockkStatic(Base64::class)

            every { Base64.encodeToString(any<ByteArray>(), any()) } answers {
                val input = firstArg<ByteArray>()
                val flags = secondArg<Int>()
                val isUrlSafe = (flags and Base64.URL_SAFE) != 0
                val encoder =
                    if (isUrlSafe) JvmBase64.getUrlEncoder() else JvmBase64.getEncoder()
                val encoded = if ((flags and Base64.NO_PADDING) != 0) {
                    encoder.withoutPadding().encodeToString(input)
                } else {
                    encoder.encodeToString(input)
                }
                formatBase64String(encoded, flags)
            }

            every { Base64.encode(any<ByteArray>(), any()) } answers {
                val input = firstArg<ByteArray>()
                val flags = secondArg<Int>()
                val isUrlSafe = (flags and Base64.URL_SAFE) != 0
                val encoder =
                    if (isUrlSafe) JvmBase64.getUrlEncoder() else JvmBase64.getEncoder()
                val encoded = if ((flags and Base64.NO_PADDING) != 0) {
                    encoder.withoutPadding().encodeToString(input)
                } else {
                    encoder.encodeToString(input)
                }
                formatBase64String(encoded, flags).toByteArray(Charsets.UTF_8)
            }

            every { Base64.decode(any<String>(), any()) } answers {
                val input = firstArg<String>()
                val flags = secondArg<Int>()
                val isUrlSafe = (flags and Base64.URL_SAFE) != 0
                val cleaned = input.replace("\\s".toRegex(), "")
                val decoder =
                    if (isUrlSafe) JvmBase64.getUrlDecoder() else JvmBase64.getDecoder()
                decoder.decode(cleaned)
            }

            every { Base64.decode(any<ByteArray>(), any()) } answers {
                val input = firstArg<ByteArray>()
                val flags = secondArg<Int>()
                val isUrlSafe = (flags and Base64.URL_SAFE) != 0
                val stringInput = String(input, Charsets.UTF_8).replace("\\s".toRegex(), "")
                val decoder =
                    if (isUrlSafe) JvmBase64.getUrlDecoder() else JvmBase64.getDecoder()
                decoder.decode(stringInput)
            }
        }

        private fun formatBase64String(encoded: String, flags: Int): String {
            val isNoWrap = (flags and Base64.NO_WRAP) != 0
            if (isNoWrap) return encoded
            if (encoded.isEmpty()) return ""
            val separator = if ((flags and Base64.CRLF) != 0) "\r\n" else "\n"
            val sb = StringBuilder()
            var i = 0
            while (i < encoded.length) {
                val end = (i + 76).coerceAtMost(encoded.length)
                sb.append(encoded.substring(i, end)).append(separator)
                i += 76
            }
            return sb.toString()
        }

        fun unmockBase64() {
            unmockkStatic(Base64::class)
        }
    }
}
