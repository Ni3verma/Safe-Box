package com.andryoga.safebox.ui.view.home.addNewData.bankCard

import androidx.lifecycle.*
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.ui.common.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AddNewBankCardViewModel @Inject constructor(
    private val bankCardDataRepository: BankCardDataRepository,
    bankAccountDataRepository: BankAccountDataRepository
) : ViewModel() {

    private val _showSelectBankAccountDialog = MutableLiveData<Boolean>(false)
    val showSelectBankAccountDialog: LiveData<Boolean> = _showSelectBankAccountDialog
    val bankAccounts = bankAccountDataRepository.getAllBankAccountData()

    val addNewBankCardScreenData = AddNewBankCardScreenData()

    fun onSaveClick() = liveData(viewModelScope.coroutineContext) {
        emit(Resource.loading(true))
        try {
            bankCardDataRepository.insertBankCardData(addNewBankCardScreenData)
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

    fun switchSelectBankAccountDialog() {
        _showSelectBankAccountDialog.value = !_showSelectBankAccountDialog.value!!
    }
}
