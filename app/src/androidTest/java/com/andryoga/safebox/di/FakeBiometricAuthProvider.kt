package com.andryoga.safebox.di

import android.content.Context
import androidx.compose.runtime.Composable
import com.andryoga.safebox.ui.core.BiometricAuthProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeBiometricAuthProvider @Inject constructor() : BiometricAuthProvider {
    var canAuthenticateOverride: Boolean = false
    var authHandlerOverride: (@Composable (onSuccess: () -> Unit, onErrorOrCancel: () -> Unit) -> Unit)? =
        null
    var invocationCount = 0

    fun reset() {
        canAuthenticateOverride = false
        authHandlerOverride = null
        invocationCount = 0
    }

    override fun canAuthenticate(context: Context): Boolean = canAuthenticateOverride

    @Composable
    override fun Authenticate(onSuccess: () -> Unit, onErrorOrCancel: () -> Unit) {
        invocationCount++
        authHandlerOverride?.invoke(onSuccess, onErrorOrCancel)
    }
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [BiometricModule::class]
)
abstract class TestBiometricModule {
    @Binds
    @Singleton
    abstract fun bindBiometricAuthProvider(
        fake: FakeBiometricAuthProvider
    ): BiometricAuthProvider
}
