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
    pPassword: String? = null,
    pNotes: String? = null,
    pCreationDate: Date = Date(),
) {
    var key = pKey
    var title: ObservableField<String> = ObservableField(pTitle)
    var url: ObservableField<String?> = ObservableField(pUrl)
    var userId: ObservableField<String> = ObservableField(pUserId)
    var password: ObservableField<String> = ObservableField(pPassword)
    var notes: ObservableField<String?> = ObservableField(pNotes)
    var creationDate = pCreationDate

    companion object {
        /*
         * converts screen data to db entity data
         * while inserting new data in db, we want current date for creation date
         * while updating data in db, we don't want to update creation date
         * */
        fun LoginScreenData.toLoginDataEntity(getCurrentDate: Boolean): LoginDataEntity {
            return LoginDataEntity(
                key,
                title.getValueOrEmpty(),
                url.get(),
                password.get(),
                notes.get(),
                userId.getValueOrEmpty(),
                if (getCurrentDate) Date() else creationDate,
                Date(),
            )
        }

        fun LoginDataEntity.toLoginScreenData(): LoginScreenData {
            return LoginScreenData(
                key,
                title,
                url,
                userId,
                password,
                notes,
                creationDate,
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
        creationDate = loginScreenData.creationDate
    }
}
