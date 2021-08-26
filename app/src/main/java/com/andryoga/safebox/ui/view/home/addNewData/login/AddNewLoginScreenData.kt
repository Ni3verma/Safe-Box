package com.andryoga.safebox.ui.view.home.addNewData.login

import androidx.databinding.ObservableField
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.ui.common.Utils.getValueOrEmpty
import java.util.*

class AddNewLoginScreenData(
    pTitle: String = "",
    pUrl: String? = null,
    pUserId: String = "",
    pPassword: String = "",
    pNotes: String? = null
) {
    var title: ObservableField<String> = ObservableField(pTitle)

    var url: ObservableField<String?> = ObservableField(pUrl)
    var userId: ObservableField<String> = ObservableField(pUserId)
    var password: ObservableField<String> = ObservableField(pPassword)
    var notes: ObservableField<String?> = ObservableField(pNotes)

    companion object {
        fun AddNewLoginScreenData.toLoginDataEntity(): LoginDataEntity {
            return LoginDataEntity(
                this.title.getValueOrEmpty(),
                this.url.get(),
                this.password.getValueOrEmpty(),
                this.notes.get(),
                this.userId.getValueOrEmpty(),
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
        title.set(addNewLoginScreenData.title.get())
        url.set(addNewLoginScreenData.url.get())
        userId.set(addNewLoginScreenData.userId.get())
        password.set(addNewLoginScreenData.password.get())
        notes.set(addNewLoginScreenData.notes.get())
    }
}
