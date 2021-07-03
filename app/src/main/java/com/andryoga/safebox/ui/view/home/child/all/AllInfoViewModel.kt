package com.andryoga.safebox.ui.view.home.child.all

import androidx.lifecycle.ViewModel
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.ui.view.home.child.common.UserDataAdapterEntity
import com.andryoga.safebox.ui.view.home.child.common.UserDataType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import javax.inject.Inject

@HiltViewModel
class AllInfoViewModel @Inject constructor(
    private val loginDataRepository: LoginDataRepository
) : ViewModel() {
    val listData = flow<List<UserDataAdapterEntity>> {
        loginDataRepository
            .getAllLoginData()
            .transform { searchData ->
                val adapterEntityList = mutableListOf<UserDataAdapterEntity>()
                searchData.forEach {
                    adapterEntityList.add(
                        UserDataAdapterEntity(
                            it.key,
                            it.title,
                            it.userId,
                            UserDataType.LOGIN_DATA
                        )
                    )
                }
                emit(adapterEntityList)
            }.collect {
                emit(it)
            }
    }
}
