package com.andryoga.safebox.ui.core

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class DeviceSecurityAuthTest {

    @Test
    fun `canAuthenticateUsingDeviceSecurity returns true when provider returns true`() {
        val context = mockk<Context>()
        val provider = mockk<DeviceSecurityAuthProvider> {
            every { canAuthenticate(context, any()) } returns true
        }

        val result = canAuthenticateUsingDeviceSecurity(context, provider)

        assertThat(result).isTrue()
    }

    @Test
    fun `canAuthenticateUsingDeviceSecurity returns false when provider returns false`() {
        val context = mockk<Context>()
        val provider = mockk<DeviceSecurityAuthProvider> {
            every { canAuthenticate(context, any()) } returns false
        }

        val result = canAuthenticateUsingDeviceSecurity(context, provider)

        assertThat(result).isFalse()
    }

    @Test
    fun `canAuthenticateUsingDeviceSecurity forwards allowDeviceCredential to provider`() {
        val context = mockk<Context>()
        val provider = mockk<DeviceSecurityAuthProvider> {
            every { canAuthenticate(context, allowDeviceCredential = true) } returns true
        }

        val result =
            canAuthenticateUsingDeviceSecurity(context, provider, allowDeviceCredential = true)

        assertThat(result).isTrue()
    }
}
