package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.data.db.dao.BankAccountDataDao
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BankAccountDataDaoSecure @Inject constructor(
    private val bankAccountDataDao: BankAccountDataDao,
    private val symmetricKeyUtils: SymmetricKeyUtils
) : BankAccountDataDao {
    override suspend fun insertBankAccountData(bankAccountDataEntity: BankAccountDataEntity) {
        bankAccountDataDao.insertBankAccountData(encrypt(bankAccountDataEntity))
    }

    override fun getAllBankAcountData(): Flow<List<BankAccountDataEntity>> {
        TODO("Not yet implemented")
    }

    override fun getBankAccountDataByKey(key: Int): Flow<BankAccountDataEntity> {
        TODO("Not yet implemented")
    }

    private fun encrypt(bankAccountDataEntity: BankAccountDataEntity): BankAccountDataEntity {
        bankAccountDataEntity.let {
            return BankAccountDataEntity(
                symmetricKeyUtils.encrypt(it.title),
                symmetricKeyUtils.encrypt(it.accountNumber),
                it.customerName,
                symmetricKeyUtils.encrypt(it.customerId),
                it.branchCode,
                it.branchName,
                it.branchAddress,
                symmetricKeyUtils.encrypt(it.ifscCode),
                it.micrCode?.let { it1 -> symmetricKeyUtils.encrypt(it1) },
                it.notes?.let { it1 -> symmetricKeyUtils.encrypt(it1) }
            )
        }
    }

    private fun decrypt(bankAccountDataEntity: BankAccountDataEntity): BankAccountDataEntity {
        bankAccountDataEntity.let {
            return BankAccountDataEntity(
                symmetricKeyUtils.decrypt(it.title),
                symmetricKeyUtils.decrypt(it.accountNumber),
                it.customerName,
                symmetricKeyUtils.decrypt(it.customerId),
                it.branchCode,
                it.branchName,
                it.branchAddress,
                symmetricKeyUtils.decrypt(it.ifscCode),
                it.micrCode?.let { it1 -> symmetricKeyUtils.decrypt(it1) },
                it.notes?.let { it1 -> symmetricKeyUtils.decrypt(it1) }
            )
        }
    }
}
