package com.andryoga.safebox.ui.view.home.child.all

import androidx.lifecycle.ViewModel
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.ui.view.home.child.common.UserDataType
import com.andryoga.safebox.ui.view.home.child.common.UserListItemData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AllInfoViewModel @Inject constructor(
    private val loginDataRepository: LoginDataRepository,
    private val bankAccountDataRepository: BankAccountDataRepository,
    private val bankCardDataRepository: BankCardDataRepository,
    private val secureNoteDataRepository: SecureNoteDataRepository
) : ViewModel() {

    @ExperimentalCoroutinesApi
    val allData = flow {
        combine(
            loginData,
            bankAccountData,
            bankCardData,
            secureNoteData
        ) { (login, bankAccount, bankCard, secureNote) ->
            var allList = listOf<UserListItemData>()
            allList = allList.plus(login)
            allList = allList.plus(bankAccount)
            allList = allList.plus(bankCard)
            allList = allList.plus(secureNote)
            allList
        }
            .flowOn(Dispatchers.Default)
            .collect {
                emit(Resource.success(it.sortedBy { data -> data.title.lowercase() }))
            }
    }

    private val loginData = flow<List<UserListItemData>> {
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
                emit(it)
            }
    }

    private val bankAccountData = flow<List<UserListItemData>> {
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
                emit(it)
            }
    }

    private val bankCardData = flow<List<UserListItemData>> {
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
                            UserDataType.BANK_CARD
                        )
                    )
                }
                emit(adapterEntityList)
            }
            .flowOn(Dispatchers.Default)
            .collect {
                emit(it)
            }
    }

    private val secureNoteData = flow<List<UserListItemData>> {
        secureNoteDataRepository
            .getAllSecureNoteData()
            .transform { searchData ->
                val adapterEntityList = mutableListOf<UserListItemData>()
                searchData.forEach {
                    adapterEntityList.add(
                        UserListItemData(
                            it.key,
                            it.title,
                            null,
                            UserDataType.SECURE_NOTE
                        )
                    )
                }
                emit(adapterEntityList)
            }
            .flowOn(Dispatchers.Default)
            .collect {
                emit(it)
            }
    }
}
