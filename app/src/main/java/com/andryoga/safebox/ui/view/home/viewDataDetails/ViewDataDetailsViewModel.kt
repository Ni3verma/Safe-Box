package com.andryoga.safebox.ui.view.home.viewDataDetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.common.UserDataType
import com.andryoga.safebox.ui.common.UserDataType.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ViewDataDetailsViewModel @Inject constructor(
    private val loginDataRepository: LoginDataRepository,
    private val bankAccountDataRepository: BankAccountDataRepository,
    private val bankCardDataRepository: BankCardDataRepository,
    private val secureNoteDataRepository: SecureNoteDataRepository
) : ViewModel() {
    fun getBankAccountData(key: Int) =
        liveData(viewModelScope.coroutineContext) {
            try {
                val data = bankAccountDataRepository.getViewBankAccountDataByKey(key)
                Timber.d("data in view mode = $data")
                emit(Resource.success(data))
            } catch (ex: Exception) {
                emit(Resource.error(null, ex.message))
                Timber.e(ex)
            }
        }

    fun deleteData(key: Int, dataType: UserDataType) {
        viewModelScope.launch {
            when (dataType) {
                LOGIN_DATA -> loginDataRepository.deleteLoginDataByKey(key)
                BANK_ACCOUNT -> bankAccountDataRepository.deleteBankAccountDataByKey(key)
                BANK_CARD -> bankCardDataRepository.deleteBankCardDataByKey(key)
                SECURE_NOTE -> secureNoteDataRepository.deleteSecureNoteDataByKey(key)
            }
        }
    }
}
