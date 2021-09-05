package com.andryoga.safebox.ui.view.home.dataDetails.bankCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.ui.common.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class BankCardDataViewModel @Inject constructor(
    private val bankCardDataRepository: BankCardDataRepository
) : ViewModel() {
    val bankCardScreenData = BankCardScreenData()
    private var isEditMode: Boolean = false
    private var dataKey: Int = -1

    fun setRuntimeVar(args: BankCardDataFragmentArgs) {
        isEditMode = args.id != -1
        dataKey = args.id
        if (isEditMode) {
            Timber.i("opened in edit mode, getting record data")
            viewModelScope.launch(Dispatchers.Default) {
                val data = bankCardDataRepository.getBankCardDataByKey(dataKey)
                withContext(Dispatchers.Main) {
                    bankCardScreenData.updateData(data)
                    Timber.i("screen data updated with new data")
                }
            }
        }
    }

    fun onSaveClick() = liveData(viewModelScope.coroutineContext) {
        emit(Resource.loading(true))
        try {
            Timber.i("save clicked, edit mode = $isEditMode")
            if (isEditMode)
                bankCardDataRepository.updateBankCardData(bankCardScreenData)
            else
                bankCardDataRepository.insertBankCardData(bankCardScreenData)
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
