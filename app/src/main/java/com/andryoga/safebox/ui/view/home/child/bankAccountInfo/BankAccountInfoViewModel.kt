package com.andryoga.safebox.ui.view.home.child.bankAccountInfo

import androidx.lifecycle.ViewModel
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.view.home.child.common.UserDataAdapterEntity
import com.andryoga.safebox.ui.view.home.child.common.UserDataType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transform
import javax.inject.Inject

@HiltViewModel
class BankAccountInfoViewModel @Inject constructor(
    private val bankAccountDataRepository: BankAccountDataRepository
) : ViewModel() {
    val listData = flow<Resource<List<UserDataAdapterEntity>>> {
        bankAccountDataRepository
            .getAllBankAccountData()
            .transform { searchData ->
                val adapterEntityList = mutableListOf<UserDataAdapterEntity>()
                searchData.forEach {
                    adapterEntityList.add(
                        UserDataAdapterEntity(
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
                it.sortBy { data -> data.title }
                emit(Resource.success(it))
            }
    }
}
