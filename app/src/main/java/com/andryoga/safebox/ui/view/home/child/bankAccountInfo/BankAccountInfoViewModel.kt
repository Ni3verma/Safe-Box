package com.andryoga.safebox.ui.view.home.child.bankAccountInfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.common.UserDataType
import com.andryoga.safebox.ui.view.home.child.common.UserListItemData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BankAccountInfoViewModel @Inject constructor(
    private val bankAccountDataRepository: BankAccountDataRepository
) : ViewModel() {

    private val _searchTextFilter = MutableStateFlow<String?>(null)
    val searchTextFilter: StateFlow<String?> = _searchTextFilter

    val listData = flow<Resource<List<UserListItemData>>> {
        bankAccountDataRepository
            .getAllBankAccountData()
            .transform { searchData ->
                val adapterEntityList = mutableListOf<UserListItemData>()
                searchData.forEach {
                    adapterEntityList.add(
                        UserListItemData(
                            it.key,
                            it.title,
                            it.accountNumber,
                            UserDataType.BANK_ACCOUNT
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
            bankAccountDataRepository.deleteBankAccountDataByKey(key)
        }
    }

    fun setSearchText(searchText: String?) {
        _searchTextFilter.value = searchText
    }
}
