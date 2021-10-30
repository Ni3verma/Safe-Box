package com.andryoga.safebox.ui.view.home.child.loginInfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.common.UserDataType
import com.andryoga.safebox.ui.view.home.child.common.UserListItemData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginInfoViewModel @Inject constructor(
    private val loginDataRepository: LoginDataRepository
) : ViewModel() {
    val listData = flow<Resource<List<UserListItemData>>> {
        loginDataRepository
            .getAllLoginData()
            .transform { searchData ->
                val adapterEntityList = mutableListOf<UserListItemData>()
                searchData.forEach {
                    adapterEntityList.add(
                        UserListItemData(
                            it.key,
                            it.title,
                            it.userId,
                            UserDataType.LOGIN_DATA
                        )
                    )
                }
                emit(adapterEntityList)
            }
            .flowOn(Dispatchers.Default)
            .collect {
                emit(Resource.success(it))
            }
    }

    fun onDeleteItemClick(itemData: UserListItemData) {
        val key = itemData.id
        viewModelScope.launch {
            loginDataRepository.deleteLoginDataByKey(key)
        }
    }
}
