package com.andryoga.safebox.data.repository

import androidx.room.withTransaction
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.Exceptions
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.dao.VaultMetaDao
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.data.db.entity.VaultMetaEntity
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.data.repository.interfaces.VaultState
import com.andryoga.safebox.security.envelope.KdfParams
import com.andryoga.safebox.security.interfaces.EnvelopeEncryptionManager
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import com.andryoga.safebox.test.fakes.FirebaseCrashlyticsTestHelper
import com.google.common.truth.Truth.assertThat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class VaultRepositoryImplTest {

    @MockK
    lateinit var database: SafeBoxDatabase

    @MockK(relaxUnitFun = true)
    lateinit var vaultMetaDao: VaultMetaDao

    @MockK(relaxUnitFun = true)
    lateinit var userDetailsDao: UserDetailsDao

    @MockK
    lateinit var envelopeEncryptionManager: EnvelopeEncryptionManager

    private lateinit var mockCrashlytics: FirebaseCrashlytics
    private lateinit var repository: VaultRepositoryImpl
    private var transactionCount = 0

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockCrashlytics = FirebaseCrashlyticsTestHelper.setupMockCrashlytics()

        mockkStatic(ROOM_DATABASE_EXTENSIONS)
        val transaction = slot<suspend () -> Unit>()
        coEvery { database.withTransaction(capture(transaction)) } coAnswers {
            transactionCount++
            transaction.captured.invoke()
        }

        repository = VaultRepositoryImpl(
            database = database,
            vaultMetaDao = vaultMetaDao,
            userDetailsDao = userDetailsDao,
            envelopeEncryptionManager = envelopeEncryptionManager,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(ROOM_DATABASE_EXTENSIONS)
        FirebaseCrashlyticsTestHelper.tearDownMockCrashlytics()
    }

    @Test
    fun hasVault_whenCryptoVersionIsEnvelope_shouldBeTrueWithoutReadingUserDetails() = runTest {
        coEvery { vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) } returns CryptoVersion.ENVELOPE

        assertThat(repository.hasVault()).isTrue()
        coVerify(exactly = 0) { userDetailsDao.getUserCount() }
    }

    @Test
    fun hasVault_whenCryptoVersionIsLegacy_shouldBeTrue() = runTest {
        coEvery { vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) } returns CryptoVersion.LEGACY

        assertThat(repository.hasVault()).isTrue()
    }

    @Test
    fun hasVault_whenNoCryptoVersionRowAndNoUserRow_shouldBeFalse() = runTest {
        coEvery { vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) } returns null
        coEvery { userDetailsDao.getUserCount() } returns 0

        assertThat(repository.hasVault()).isFalse()
    }

    @Test
    fun hasVault_whenAUserRowHasNoCryptoVersionRow_shouldAssertInDebug() = runTest {
        // Cannot occur after MIGRATION_5_6; release builds answer true (Login, never Signup).
        coEvery { vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) } returns null
        coEvery { userDetailsDao.getUserCount() } returns 1

        val outcome = runCatching { repository.hasVault() }

        assertThat(outcome.exceptionOrNull()).isInstanceOf(Exceptions.DebugFatalException::class.java)
    }

    @Test
    fun createVault_shouldMintDekWriteEnvelopeMetaAndPlaintextHintInOneTransaction() = runTest {
        val expectedDek = ByteArray(32) { (it + 1).toByte() }
        val kdfParams = KdfParams(
            algorithm = KdfParams.ALGORITHM_PBKDF2_SHA256,
            iterations = 99_000,
            salt = ByteArray(16) { 0x0A },
        )
        every { envelopeEncryptionManager.mintDek() } returns expectedDek
        every { envelopeEncryptionManager.deriveKdfParams() } returns kdfParams
        every {
            envelopeEncryptionManager.wrapWithPassword(any(), kdfParams, expectedDek)
        } returns "wrapped-pw-blob"
        every { envelopeEncryptionManager.makeVerifier(expectedDek) } returns "verifier-blob"
        val userSlot = slot<UserDetailsEntity>()
        coEvery { userDetailsDao.insertUserDetailsData(capture(userSlot)) } returns Unit

        val returnedDek = repository.createVault("MasterPass#1".toCharArray(), "my hint")

        assertThat(returnedDek).isEqualTo(expectedDek)
        assertThat(transactionCount).isEqualTo(1)
        coVerifyOrder {
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.CRYPTO_VERSION, CryptoVersion.ENVELOPE))
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.KDF_PARAMS, kdfParams.encode()))
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.WRAPPED_DEK_PASSWORD, "wrapped-pw-blob"))
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.DEK_VERIFIER, "verifier-blob"))
            userDetailsDao.insertUserDetailsData(any())
        }
        assertThat(userSlot.captured.hint).isEqualTo("my hint")
        assertThat(userSlot.captured.uid).isNotEmpty()
        verify { mockCrashlytics.setUserId(userSlot.captured.uid) }
        verify {
            mockCrashlytics.setCustomKey(CommonConstants.CRASHLYTICS_KEY_UID, userSlot.captured.uid)
        }
    }

    @Test
    fun createVault_withBlankHint_shouldStoreNullHint() = runTest {
        val expectedDek = ByteArray(32) { 1 }
        val kdfParams = KdfParams(
            algorithm = KdfParams.ALGORITHM_PBKDF2_SHA256,
            iterations = 99_000,
            salt = ByteArray(16) { 1 },
        )
        every { envelopeEncryptionManager.mintDek() } returns expectedDek
        every { envelopeEncryptionManager.deriveKdfParams() } returns kdfParams
        every { envelopeEncryptionManager.wrapWithPassword(any(), kdfParams, expectedDek) } returns "blob"
        every { envelopeEncryptionManager.makeVerifier(expectedDek) } returns "verifier"
        val userSlot = slot<UserDetailsEntity>()
        coEvery { userDetailsDao.insertUserDetailsData(capture(userSlot)) } returns Unit

        repository.createVault("MasterPass#1".toCharArray(), "   ")

        assertThat(userSlot.captured.hint).isNull()
    }

    @Test
    fun rewrapPassword_shouldDeriveFreshKdfParamsAndUpdatePasswordWrapInOneTransaction() = runTest {
        val dek = ByteArray(32) { 2 }
        val freshParams = KdfParams(
            algorithm = KdfParams.ALGORITHM_PBKDF2_SHA256,
            iterations = 99_000,
            salt = ByteArray(16) { 7 },
        )
        every { envelopeEncryptionManager.deriveKdfParams() } returns freshParams
        every {
            envelopeEncryptionManager.wrapWithPassword(any(), freshParams, dek)
        } returns "rewrapped-blob"

        repository.rewrapPassword("NewPass#2".toCharArray(), dek)

        assertThat(transactionCount).isEqualTo(1)
        coVerifyOrder {
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.KDF_PARAMS, freshParams.encode()))
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.WRAPPED_DEK_PASSWORD, "rewrapped-blob"))
        }
    }

    @Test
    fun storeWrap_withAliasSuffix_shouldWriteWrapAndSuffixInOneTransaction() = runTest {
        repository.storeWrap(WrapKeySpec.BIOMETRIC, "bio-blob", aliasSuffix = "2")

        assertThat(transactionCount).isEqualTo(1)
        coVerifyOrder {
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.WRAPPED_DEK_BIOMETRIC, "bio-blob"))
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.BIOMETRIC_KEY_ALIAS_SUFFIX, "2"))
        }
    }

    @Test
    fun storeWrap_withoutAliasSuffix_shouldWriteOnlyWrapKey() = runTest {
        repository.storeWrap(WrapKeySpec.DEVICE, "dev-blob", aliasSuffix = null)

        assertThat(transactionCount).isEqualTo(1)
        coVerify(exactly = 1) {
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.WRAPPED_DEK_DEVICE, "dev-blob"))
        }
        coVerify(exactly = 0) {
            vaultMetaDao.upsert(match { it.key == VaultMetaKey.DEVICE_KEY_ALIAS_SUFFIX })
        }
    }

    @Test
    fun deleteWrap_shouldDeleteWrappedDekRowForSpec() = runTest {
        repository.deleteWrap(WrapKeySpec.BIOMETRIC)

        coVerify(exactly = 1) { vaultMetaDao.delete(VaultMetaKey.WRAPPED_DEK_BIOMETRIC) }
    }

    @Test
    fun getWrapKeyAliasSuffix_shouldReturnStoredSuffixForSpec() = runTest {
        coEvery { vaultMetaDao.get(VaultMetaKey.DEVICE_KEY_ALIAS_SUFFIX) } returns "3"

        assertThat(repository.getWrapKeyAliasSuffix(WrapKeySpec.DEVICE)).isEqualTo("3")
    }

    @Test
    fun getVaultState_whenCryptoVersionIsNotEnvelope_shouldReturnLegacy() = runTest {
        coEvery { vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) } returns CryptoVersion.LEGACY

        assertThat(repository.getVaultState()).isEqualTo(VaultState.Legacy)
    }

    @Test
    fun getVaultState_whenEnvelopeMetaIsComplete_shouldReturnEnvelopeState() = runTest {
        val kdfParams = KdfParams(
            algorithm = KdfParams.ALGORITHM_PBKDF2_SHA256,
            iterations = 99_000,
            salt = ByteArray(16) { 3 },
        )
        coEvery { vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) } returns CryptoVersion.ENVELOPE
        coEvery { vaultMetaDao.get(VaultMetaKey.KDF_PARAMS) } returns kdfParams.encode()
        coEvery { vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_PASSWORD) } returns "pw-blob"
        coEvery { vaultMetaDao.get(VaultMetaKey.DEK_VERIFIER) } returns "verifier-blob"
        coEvery { vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_BIOMETRIC) } returns "bio-blob"
        coEvery { vaultMetaDao.get(VaultMetaKey.BIOMETRIC_KEY_ALIAS_SUFFIX) } returns "2"
        coEvery { vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_DEVICE) } returns null
        coEvery { vaultMetaDao.get(VaultMetaKey.DEVICE_KEY_ALIAS_SUFFIX) } returns null
        coEvery { vaultMetaDao.get(VaultMetaKey.BIOMETRIC_UNLOCK_DISABLED) } returns "1"

        val state = repository.getVaultState()

        assertThat(state).isEqualTo(
            VaultState.Envelope(
                kdfParams = kdfParams,
                wrappedDekPassword = "pw-blob",
                dekVerifier = "verifier-blob",
                wrappedDekBiometric = "bio-blob",
                biometricKeyAliasSuffix = "2",
                wrappedDekDevice = null,
                deviceKeyAliasSuffix = null,
                isBiometricUnlockDisabled = true,
            ),
        )
    }

    @Test
    fun getVaultState_whenEnvelopeMetaIsMissingMandatoryKey_shouldAssertInDebug() = runTest {
        coEvery { vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) } returns CryptoVersion.ENVELOPE
        coEvery { vaultMetaDao.get(VaultMetaKey.KDF_PARAMS) } returns null
        coEvery { vaultMetaDao.get(VaultMetaKey.WRAPPED_DEK_PASSWORD) } returns "pw-blob"
        coEvery { vaultMetaDao.get(VaultMetaKey.DEK_VERIFIER) } returns "verifier-blob"

        val outcome = runCatching { repository.getVaultState() }

        assertThat(outcome.exceptionOrNull()).isInstanceOf(Exceptions.DebugFatalException::class.java)
    }

    private companion object {
        const val ROOM_DATABASE_EXTENSIONS = "androidx.room.RoomDatabaseKt"
    }
}
