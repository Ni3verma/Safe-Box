package com.andryoga.safebox.ui.view.home.addNewData.bankCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.ui.common.Resource
import timber.log.Timber

class AddNewBankCardViewModel : ViewModel() {
    val addNewBankCardScreenData = AddNewBankCardScreenData()

    fun onSaveClick() = liveData(viewModelScope.coroutineContext) {
        Timber.i("save clicked, adding bank card data in db")
        emit(Resource.loading(true))
        try {
//            loginDataRepository.insertLoginData(addNewLoginScreenData)
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
