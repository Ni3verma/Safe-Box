package com.andryoga.safebox.ui.view.home.dataDetails.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.ui.common.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoginDataViewModel @Inject constructor(
    private val loginDataRepository: LoginDataRepository
) : ViewModel() {
    val loginScreenData = LoginScreenData()
    private var isEditMode: Boolean = false
    private var dataKey: Int = -1

    fun setRuntimeVar(args: LoginDataFragmentArgs) {
        isEditMode = args.id != -1
        dataKey = args.id
        if (isEditMode) {
            Timber.i("opened in edit mode, getting record data")
            viewModelScope.launch(Dispatchers.Default) {
                val data = loginDataRepository.getLoginDataByKey(dataKey)
                withContext(Dispatchers.Main) {
                    loginScreenData.updateData(data)
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
                loginDataRepository.updateLoginData(loginScreenData)
            else
                loginDataRepository.insertLoginData(loginScreenData)
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
