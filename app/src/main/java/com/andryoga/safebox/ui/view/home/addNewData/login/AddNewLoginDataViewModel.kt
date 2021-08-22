package com.andryoga.safebox.ui.view.home.addNewData.login

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
class AddNewLoginDataViewModel @Inject constructor(
    private val loginDataRepository: LoginDataRepository
) : ViewModel() {
    val addNewLoginScreenData = AddNewLoginScreenData()
    private var isEditMode: Boolean = false
    private var loginDataKey: Int = -1

    fun setRuntimeVar(args: AddNewLoginDataDialogFragmentArgs) {
        isEditMode = args.id != -1
        loginDataKey = args.id
        if (isEditMode) {
            Timber.i("opened in edit mode, getting record data")
            viewModelScope.launch(Dispatchers.Default) {
                val data = loginDataRepository.getLoginDataByKey(loginDataKey)
                withContext(Dispatchers.Main) {
                    addNewLoginScreenData.updateData(data)
                    Timber.i("screen data updated with $data")
                }
            }
        }
    }

    fun onSaveClick() = liveData(viewModelScope.coroutineContext) {
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
