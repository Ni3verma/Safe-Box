package com.andryoga.safebox.ui.view.home.dataDetails.login

import androidx.databinding.ObservableField
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.ui.common.Utils.getValueOrEmpty
import java.util.*

class LoginScreenData(
    /*
    * It is very important to initialize key with 0
    * so that when we convert screen data to entity for db insertion at that
    * 0 will be passed. For room zero means that it can auto-increment value
    * */
    pKey: Int = 0,
    pTitle: String = "",
    pUrl: String? = null,
    pUserId: String = "",
    pPassword: String = "",
    pNotes: String? = null
) {
    var key = pKey
    var title: ObservableField<String> = ObservableField(pTitle)
    var url: ObservableField<String?> = ObservableField(pUrl)
    var userId: ObservableField<String> = ObservableField(pUserId)
    var password: ObservableField<String> = ObservableField(pPassword)
    var notes: ObservableField<String?> = ObservableField(pNotes)

    companion object {
        fun LoginScreenData.toLoginDataEntity(): LoginDataEntity {
            return LoginDataEntity(
                key,
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
                this.key,
                this.title,
                this.url,
                this.userId,
                this.password,
                this.notes,
            )
        }
    }

    fun updateData(loginScreenData: LoginScreenData) {
        key = loginScreenData.key
        title.set(loginScreenData.title.get())
        url.set(loginScreenData.url.get())
        userId.set(loginScreenData.userId.get())
        password.set(loginScreenData.password.get())
        notes.set(loginScreenData.notes.get())
    }

    override fun toString(): String {
        return "$key - ${title.get()} - ${url.get()} - ${userId.get()} - " +
            "${password.get()} - ${notes.get()}"
    }
}
