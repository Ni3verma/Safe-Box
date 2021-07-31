package com.andryoga.safebox.ui.view.home.addNewData.bankCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.ui.common.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AddNewBankCardViewModel @Inject constructor(
    private val bankCardRepository: BankCardDataRepository
) : ViewModel() {
    val addNewBankCardScreenData = AddNewBankCardScreenData()

    fun onSaveClick() = liveData(viewModelScope.coroutineContext) {
        Timber.i("save clicked, adding bank card data in db")
        emit(Resource.loading(true))
        try {
            bankCardRepository.insertBankCardData(addNewBankCardScreenData)
            emit(Resource.success(true))
        } catch (ex: Exception) {
            emit(
                Resource.error(
                    data = false,
                    message = ex.message
                )
            )
            Timber.e(ex)
        }
    }
}
