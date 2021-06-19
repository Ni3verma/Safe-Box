package com.andryoga.safebox.ui.view.home.addNewData.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.ui.common.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AddNewLoginDataViewModel @Inject constructor(
    private val loginDataRepository: LoginDataRepository
) : ViewModel() {
    val addNewLoginScreenData = AddNewLoginScreenData()

    fun onSaveClick() = liveData(viewModelScope.coroutineContext) {
        Timber.i("save clicked, adding login data in db")
        emit(Resource.loading(true))
        try {
            loginDataRepository.insertLoginData(addNewLoginScreenData)
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
