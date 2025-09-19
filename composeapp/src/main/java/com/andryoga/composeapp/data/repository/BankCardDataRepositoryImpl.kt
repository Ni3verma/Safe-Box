package com.andryoga.composeapp.data.repository

//import com.andryoga.composeapp.ui.view.home.dataDetails.bankCard.BankCardScreenData
//import com.andryoga.composeapp.ui.view.home.dataDetails.bankCard.BankCardScreenData.Companion.toBankCardDataEntity
//import com.andryoga.composeapp.ui.view.home.dataDetails.bankCard.BankCardScreenData.Companion.toBankCardScreenData
//import com.google.firebase.analytics.ktx.analytics
//import com.google.firebase.ktx.Firebase
import com.andryoga.composeapp.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.composeapp.data.repository.interfaces.BankCardDataRepository
import com.andryoga.composeapp.domain.toDbEntity
import com.andryoga.composeapp.ui.core.models.CardData
import javax.inject.Inject

class BankCardDataRepositoryImpl @Inject constructor(
    private val bankCardDataDaoSecure: BankCardDataDaoSecure
) : BankCardDataRepository {
    override suspend fun upsertBankCardData(cardData: CardData) {
        bankCardDataDaoSecure.upsertBankCardData(cardData.toDbEntity())
    }
//    override suspend fun insertBankCardData(bankCardScreenData: BankCardScreenData) {
//        Firebase.analytics.logEvent(NEW_BANK_CARD, null)
//        bankCardDataDaoSecure.insertBankCardData(
//            bankCardScreenData.toBankCardDataEntity(
//                getCurrentDate = true
//            )
//        )
//    }
//
//    override suspend fun updateBankCardData(bankCardScreenData: BankCardScreenData) {
//        bankCardDataDaoSecure.updateBankCardData(
//            bankCardScreenData.toBankCardDataEntity(
//                getCurrentDate = false
//            )
//        )
//    }
//
//    override fun getAllBankCardData(): Flow<List<SearchBankCardData>> {
//        return bankCardDataDaoSecure.getAllBankCardData()
//    }
//
//    override suspend fun getBankCardDataByKey(key: Int): BankCardScreenData {
//        return bankCardDataDaoSecure.getBankCardDataByKey(key).toBankCardScreenData()
//    }
//
//    override suspend fun deleteBankCardDataByKey(key: Int) {
//        bankCardDataDaoSecure.deleteBankCardDataByKey(key)
//    }
//
//    override suspend fun getViewBankCardDataByKey(key: Int): ViewBankCardData {
//        return bankCardDataDaoSecure.getBankCardDataByKey(key).toViewBankCardData()
//    }
}
