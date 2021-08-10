package com.andryoga.safebox.ui.view.home.addNewData.secureNote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.ui.common.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SecureNoteDataViewModel @Inject constructor(
    private val secureNoteDataRepository: SecureNoteDataRepository
) : ViewModel() {
    val secureNoteScreenData = SecureNoteScreenData()

    fun onSaveClick() = liveData(viewModelScope.coroutineContext) {
        Timber.i("save clicked, adding login data in db")
        emit(Resource.loading(true))
        try {
            secureNoteDataRepository.insertSecureNoteData(secureNoteScreenData)
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
