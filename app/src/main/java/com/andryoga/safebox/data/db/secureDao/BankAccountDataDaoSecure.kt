package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.common.Utils.decryptNullableString
import com.andryoga.safebox.common.Utils.encryptNullableString
import com.andryoga.safebox.data.db.dao.BankAccountDataDao
import com.andryoga.safebox.data.db.docs.SearchBankAccountData
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BankAccountDataDaoSecure @Inject constructor(
    private val bankAccountDataDao: BankAccountDataDao,
    private val symmetricKeyUtils: SymmetricKeyUtils
) : BankAccountDataDao {
    override suspend fun insertBankAccountData(bankAccountDataEntity: BankAccountDataEntity) {
        bankAccountDataDao.insertBankAccountData(encrypt(bankAccountDataEntity))
    }

    override suspend fun updateBankAccountData(bankAccountDataEntity: BankAccountDataEntity) {
        bankAccountDataDao.updateBankAccountData(encrypt(bankAccountDataEntity))
    }

    override suspend fun getBankAccountDataByKey(key: Int): BankAccountDataEntity {
        return decrypt(bankAccountDataDao.getBankAccountDataByKey(key))
    }

    override fun getAllBankAccountData(): Flow<List<SearchBankAccountData>> {
        return bankAccountDataDao.getAllBankAccountData()
            .map {
                SearchBankAccountData.decrypt(it, symmetricKeyUtils)
            }
    }

    private fun encrypt(bankAccountDataEntity: BankAccountDataEntity): BankAccountDataEntity {
        bankAccountDataEntity.let {
            return BankAccountDataEntity(
                it.key,
                symmetricKeyUtils.encrypt(it.title),
                symmetricKeyUtils.encrypt(it.accountNumber),
                it.customerName,
                symmetricKeyUtils.encrypt(it.customerId),
                it.branchCode,
                it.branchName,
                it.branchAddress,
                symmetricKeyUtils.encrypt(it.ifscCode),
                it.micrCode.encryptNullableString(symmetricKeyUtils),
                it.notes.encryptNullableString(symmetricKeyUtils),
                it.creationDate,
                it.updateDate
            )
        }
    }

    private fun decrypt(bankAccountDataEntity: BankAccountDataEntity): BankAccountDataEntity {
        bankAccountDataEntity.let {
            return BankAccountDataEntity(
                it.key,
                symmetricKeyUtils.decrypt(it.title),
                symmetricKeyUtils.decrypt(it.accountNumber),
                it.customerName,
                symmetricKeyUtils.decrypt(it.customerId),
                it.branchCode,
                it.branchName,
                it.branchAddress,
                symmetricKeyUtils.decrypt(it.ifscCode),
                it.micrCode.decryptNullableString(symmetricKeyUtils),
                it.notes.decryptNullableString(symmetricKeyUtils),
                it.creationDate,
                it.updateDate
            )
        }
    }
}
