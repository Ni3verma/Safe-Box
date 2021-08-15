package com.andryoga.safebox.ui.view.home.child.all

import androidx.lifecycle.ViewModel
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.view.home.child.common.UserDataType
import com.andryoga.safebox.ui.view.home.child.common.UserListItemData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transform
import javax.inject.Inject

@HiltViewModel
class AllInfoViewModel @Inject constructor(
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
                it.sortBy { data -> data.title }
                emit(Resource.success(it))
            }
    }
}
