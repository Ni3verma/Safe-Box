package com.andryoga.safebox.di

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import com.andryoga.safebox.ui.core.DeviceSecurityAuthProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stands in for the system prompt only. A scripted success hands back the very [BiometricPrompt.CryptoObject]
 * the screen bound, so the unwrap behind it runs for real against the JCE keys of
 * `FakeKeystoreGateway` — the crypto is faked at the Keystore seam, never here.
 */
@Singleton
class FakeDeviceSecurityAuthProvider @Inject constructor() : DeviceSecurityAuthProvider {
    var canAuthenticateOverride: Boolean = false
    var authHandlerOverride: (
        @Composable (
            onSuccess: (BiometricPrompt.CryptoObject?) -> Unit,
            onErrorOrCancel: () -> Unit,
        ) -> Unit
    )? = null
    var invocationCount = 0

    /** The `CryptoObject` bound to the most recent prompt, `null` when the caller bound none. */
    var lastCryptoObject: BiometricPrompt.CryptoObject? = null
        private set

    fun reset() {
        canAuthenticateOverride = false
        authHandlerOverride = null
        invocationCount = 0
        lastCryptoObject = null
    }

    override fun canAuthenticate(
        context: Context,
        allowDeviceCredential: Boolean,
    ): Boolean = canAuthenticateOverride

    @Composable
    override fun Authenticate(
        title: String?,
        subtitle: String?,
        allowDeviceCredential: Boolean,
        cryptoObject: BiometricPrompt.CryptoObject?,
        onSuccess: (BiometricPrompt.CryptoObject?) -> Unit,
        onErrorOrCancel: () -> Unit,
    ) {
        invocationCount++
        lastCryptoObject = cryptoObject
        authHandlerOverride?.invoke({ onSuccess(cryptoObject) }, onErrorOrCancel)
    }
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DeviceSecurityModule::class]
)
abstract class TestDeviceSecurityModule {
    @Binds
    @Singleton
    abstract fun bindDeviceSecurityAuthProvider(
        fake: FakeDeviceSecurityAuthProvider
    ): DeviceSecurityAuthProvider
}
