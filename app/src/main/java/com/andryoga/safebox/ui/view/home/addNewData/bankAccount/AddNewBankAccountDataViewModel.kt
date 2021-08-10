package com.andryoga.safebox.ui.view.home.addNewData.bankAccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.ui.common.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AddNewBankAccountDataViewModel @Inject constructor(
    private val bankAccountDataRepository: BankAccountDataRepository
) : ViewModel() {
    val addNewBankAccountScreenData = AddNewBankAccountScreenData()

    fun onSaveClick() = liveData(viewModelScope.coroutineContext) {
        emit(Resource.loading(true))
        try {
            bankAccountDataRepository.insertBankAccountData(addNewBankAccountScreenData)
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
