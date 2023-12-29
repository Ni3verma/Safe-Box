package com.andryoga.safebox.ui.view.home.child.bankCardInfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.common.UserDataType
import com.andryoga.safebox.ui.view.home.child.common.UserListItemData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BankCardInfoViewModel
    @Inject
    constructor(
        private val bankCardDataRepository: BankCardDataRepository,
    ) : ViewModel() {
        private val _searchTextFilter = MutableStateFlow<String?>(null)
        val searchTextFilter: StateFlow<String?> = _searchTextFilter

        val listData =
            flow<Resource<List<UserListItemData>>> {
                bankCardDataRepository
                    .getAllBankCardData()
                    .transform { searchData ->
                        val adapterEntityList = mutableListOf<UserListItemData>()
                        searchData.forEach {
                            adapterEntityList.add(
                                UserListItemData(
                                    it.key,
                                    it.title,
                                    it.number,
                                    UserDataType.BANK_CARD,
                                ),
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
                bankCardDataRepository.deleteBankCardDataByKey(key)
            }
        }

        fun setSearchText(searchText: String?) {
            _searchTextFilter.value = searchText
        }
    }
