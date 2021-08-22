package com.andryoga.safebox.ui.view.home.addNewData.login

import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import com.andryoga.safebox.BR
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import java.util.*

class AddNewLoginScreenData(
    pTitle: String = "",
    pUrl: String? = null,
    pUserId: String = "",
    pPassword: String = "",
    pNotes: String? = null
) : BaseObservable() {
    @get:Bindable
    var title: String = pTitle
        set(value) {
            field = value
            notifyPropertyChanged(BR.title)
        }

    @get:Bindable
    var url: String? = pUrl
        set(value) {
            field = value
            notifyPropertyChanged(BR.url)
        }

    @get:Bindable
    var userId: String = pUserId
        set(value) {
            field = value
            notifyPropertyChanged(BR.userId)
        }

    @get:Bindable
    var password: String = pPassword
        set(value) {
            field = value
            notifyPropertyChanged(BR.password)
        }

    @get:Bindable
    var notes: String? = pNotes
        set(value) {
            field = value
            notifyPropertyChanged(BR.notes)
        }

    companion object {
        fun AddNewLoginScreenData.toLoginDataEntity(): LoginDataEntity {
            return LoginDataEntity(
                this.title,
                this.url,
                this.password,
                this.notes,
                this.userId,
                Date(),
                Date()
            )
        }

        fun LoginDataEntity.toAddNewLoginScreenData(): AddNewLoginScreenData {
            return AddNewLoginScreenData(
                this.title,
                this.url,
                this.userId,
                this.password,
                this.notes,
            )
        }
    }

    fun updateData(addNewLoginScreenData: AddNewLoginScreenData) {
        title = addNewLoginScreenData.title
        url = addNewLoginScreenData.url
        userId = addNewLoginScreenData.userId
        password = addNewLoginScreenData.password
        notes = addNewLoginScreenData.notes
    }
}
