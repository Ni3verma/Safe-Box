package com.andryoga.safebox.ui.view.home.dataDetails.login

import androidx.databinding.ObservableField
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.ui.common.Utils.getValueOrEmpty
import java.util.*

class LoginScreenData(
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
        fun LoginScreenData.toLoginDataEntity(): LoginDataEntity {
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

        fun LoginDataEntity.toLoginScreenData(): LoginScreenData {
            return LoginScreenData(
                this.title,
                this.url,
                this.userId,
                this.password,
                this.notes,
            )
        }
    }

    fun updateData(loginScreenData: LoginScreenData) {
        title.set(loginScreenData.title.get())
        url.set(loginScreenData.url.get())
        userId.set(loginScreenData.userId.get())
        password.set(loginScreenData.password.get())
        notes.set(loginScreenData.notes.get())
    }
}
