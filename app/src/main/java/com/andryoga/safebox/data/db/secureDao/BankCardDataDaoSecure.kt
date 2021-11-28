package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.common.Utils.decryptNullableString
import com.andryoga.safebox.common.Utils.encryptNullableString
import com.andryoga.safebox.data.db.dao.BankCardDataDao
import com.andryoga.safebox.data.db.docs.SearchBankCardData
import com.andryoga.safebox.data.db.docs.export.ExportBankCardData
import com.andryoga.safebox.data.db.entity.BankCardDataEntity
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BankCardDataDaoSecure @Inject constructor(
    private val bankCardDataDao: BankCardDataDao,
    private val symmetricKeyUtils: SymmetricKeyUtils
) : BankCardDataDao {
    override suspend fun insertBankCardData(bankCardDataEntity: BankCardDataEntity) {
        bankCardDataDao.insertBankCardData(encrypt(bankCardDataEntity))
    }

    override fun insertMultipleBankCardData(bankCardDataEntity: List<BankCardDataEntity>) {
        bankCardDataDao.insertMultipleBankCardData(bankCardDataEntity.map { encrypt(it) })
    }

    override suspend fun updateBankCardData(bankCardDataEntity: BankCardDataEntity) {
        bankCardDataDao.updateBankCardData(encrypt(bankCardDataEntity))
    }

    override fun getAllBankCardData(): Flow<List<SearchBankCardData>> {
        return bankCardDataDao
            .getAllBankCardData()
            .map { SearchBankCardData.decrypt(it, symmetricKeyUtils) }
    }

    override suspend fun getBankCardDataByKey(key: Int): BankCardDataEntity {
        return decrypt(bankCardDataDao.getBankCardDataByKey(key))
    }

    override suspend fun deleteBankCardDataByKey(key: Int) {
        bankCardDataDao.deleteBankCardDataByKey(key)
    }

    override suspend fun exportAllData(): List<ExportBankCardData> {
        return bankCardDataDao.exportAllData().map { decrypt(it) }
    }

    override fun deleteAllData() {
        bankCardDataDao.deleteAllData()
    }

    private fun encrypt(bankCardDataEntity: BankCardDataEntity): BankCardDataEntity {
        bankCardDataEntity.let {
            return BankCardDataEntity(
                it.key,
                it.title,
                it.name.encryptNullableString(symmetricKeyUtils),
                symmetricKeyUtils.encrypt(it.number),
                it.pin.encryptNullableString(symmetricKeyUtils),
                it.cvv.encryptNullableString(symmetricKeyUtils),
                it.expiryDate.encryptNullableString(symmetricKeyUtils),
                it.notes.encryptNullableString(symmetricKeyUtils),
                it.creationDate,
                it.updateDate
            )
        }
    }

    private fun decrypt(bankCardDataEntity: BankCardDataEntity): BankCardDataEntity {
        bankCardDataEntity.let {
            return BankCardDataEntity(
                it.key,
                it.title,
                it.name.decryptNullableString(symmetricKeyUtils),
                symmetricKeyUtils.decrypt(it.number),
                it.pin.decryptNullableString(symmetricKeyUtils),
                it.cvv.decryptNullableString(symmetricKeyUtils),
                it.expiryDate.decryptNullableString(symmetricKeyUtils),
                it.notes.decryptNullableString(symmetricKeyUtils),
                it.creationDate,
                it.updateDate
            )
        }
    }

    private fun decrypt(exportBankCardData: ExportBankCardData): ExportBankCardData {
        exportBankCardData.let {
            return ExportBankCardData(
                it.title,
                it.name.decryptNullableString(symmetricKeyUtils),
                symmetricKeyUtils.decrypt(it.number),
                it.pin.decryptNullableString(symmetricKeyUtils),
                it.cvv.decryptNullableString(symmetricKeyUtils),
                it.expiryDate.decryptNullableString(symmetricKeyUtils),
                it.notes.decryptNullableString(symmetricKeyUtils),
                it.creationDate,
                it.updateDate
            )
        }
    }
}
