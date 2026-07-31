package com.andryoga.safebox.test.fakes

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.File

object TestDataStoreHelper {
    fun createMockContextWithFilesDir(tempDir: File): Context {
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.filesDir } returns tempDir
        return mockContext
    }
}
