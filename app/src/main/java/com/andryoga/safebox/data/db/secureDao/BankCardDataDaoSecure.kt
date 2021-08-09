package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.common.Utils.decryptNullableString
import com.andryoga.safebox.common.Utils.encryptNullableString
import com.andryoga.safebox.data.db.dao.BankCardDataDao
import com.andryoga.safebox.data.db.entity.BankCardDataEntity
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BankCardDataDaoSecure @Inject constructor(
    private val bankCardDataDao: BankCardDataDao,
    private val symmetricKeyUtils: SymmetricKeyUtils
) : BankCardDataDao {
    override suspend fun insertBankCardData(bankCardDataEntity: BankCardDataEntity) {
        bankCardDataDao.insertBankCardData(encrypt(bankCardDataEntity))
    }

    override fun getAllBankCardData(): Flow<List<BankCardDataEntity>> {
        TODO("Not yet implemented")
    }

    override fun getBankCardDataByKey(key: Int): Flow<BankCardDataEntity> {
        TODO("Not yet implemented")
    }

    private fun encrypt(bankCardDataEntity: BankCardDataEntity): BankCardDataEntity {
        bankCardDataEntity.let {
            return BankCardDataEntity(
                symmetricKeyUtils.encrypt(it.title),
                it.name.encryptNullableString(symmetricKeyUtils),
                symmetricKeyUtils.encrypt(it.number),
                it.pin.encryptNullableString(symmetricKeyUtils),
                symmetricKeyUtils.encrypt(it.cvv),
                it.linkedBankAccountKey,
                symmetricKeyUtils.encrypt(it.expiryDate),
                it.notes.encryptNullableString(symmetricKeyUtils),
                it.creationDate,
                it.updateDate
            )
        }
    }

    private fun decrypt(bankCardDataEntity: BankCardDataEntity): BankCardDataEntity {
        bankCardDataEntity.let {
            return BankCardDataEntity(
                symmetricKeyUtils.decrypt(it.title),
                it.name.decryptNullableString(symmetricKeyUtils),
                symmetricKeyUtils.decrypt(it.number),
                it.pin.decryptNullableString(symmetricKeyUtils),
                symmetricKeyUtils.decrypt(it.cvv),
                it.linkedBankAccountKey,
                symmetricKeyUtils.decrypt(it.expiryDate),
                it.notes.decryptNullableString(symmetricKeyUtils),
                it.creationDate,
                it.updateDate
            )
        }
    }
}
