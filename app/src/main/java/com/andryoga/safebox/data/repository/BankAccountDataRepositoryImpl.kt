package com.andryoga.safebox.data.repository

import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.data.db.docs.SearchBankAccountData
import com.andryoga.safebox.data.db.secureDao.BankAccountDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.domain.mappers.record.toBankAccountData
import com.andryoga.safebox.domain.mappers.record.toDbEntity
import com.andryoga.safebox.domain.models.record.BankAccountData
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BankAccountDataRepositoryImpl @Inject constructor(
    private val bankAccountDataDaoSecure: BankAccountDataDaoSecure,
    private val analyticsHelper: AnalyticsHelper
) : BankAccountDataRepository {
    override suspend fun upsertBankAccountData(accountData: BankAccountData) {
        if (accountData.id == null || accountData.id == 0) {
            analyticsHelper.logEvent(AnalyticsKey.NEW_BANK_ACCOUNT)
        }
        bankAccountDataDaoSecure.upsertBankAccountData(accountData.toDbEntity())
    }

    override fun getAllBankAccountData(): Flow<List<SearchBankAccountData>> {
        return bankAccountDataDaoSecure.getAllBankAccountData()
    }

    override suspend fun getBankAccountDataByKey(key: Int): BankAccountData {
        return bankAccountDataDaoSecure.getBankAccountDataByKey(key).toBankAccountData()
    }

    override suspend fun deleteBankAccountDataByKey(key: Int) {
        bankAccountDataDaoSecure.deleteBankAccountDataByKey(key)
    }
}
