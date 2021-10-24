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

    override suspend fun deleteBankAccountDataByKey(key: Int) {
        bankAccountDataDao.deleteBankAccountDataByKey(key)
    }

    private fun encrypt(bankAccountDataEntity: BankAccountDataEntity): BankAccountDataEntity {
        bankAccountDataEntity.let {
            return BankAccountDataEntity(
                it.key,
                it.title,
                symmetricKeyUtils.encrypt(it.accountNumber),
                it.customerName,
                it.customerId.encryptNullableString(symmetricKeyUtils),
                it.branchCode,
                it.branchName,
                it.branchAddress,
                it.ifscCode.encryptNullableString(symmetricKeyUtils),
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
                it.title,
                symmetricKeyUtils.decrypt(it.accountNumber),
                it.customerName,
                it.customerId.decryptNullableString(symmetricKeyUtils),
                it.branchCode,
                it.branchName,
                it.branchAddress,
                it.ifscCode.decryptNullableString(symmetricKeyUtils),
                it.micrCode.decryptNullableString(symmetricKeyUtils),
                it.notes.decryptNullableString(symmetricKeyUtils),
                it.creationDate,
                it.updateDate
            )
        }
    }
}
