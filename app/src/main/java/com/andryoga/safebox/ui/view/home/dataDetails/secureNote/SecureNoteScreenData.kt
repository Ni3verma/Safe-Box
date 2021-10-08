package com.andryoga.safebox.ui.view.home.dataDetails.secureNote

import androidx.databinding.ObservableField
import com.andryoga.safebox.data.db.entity.SecureNoteDataEntity
import com.andryoga.safebox.ui.common.Utils.getValueOrEmpty
import java.util.*

class SecureNoteScreenData(
    /*
   * It is very important to initialize key with 0
   * so that when we convert screen data to entity for db insertion at that
   * 0 will be passed. For room zero means that it can auto-increment value
   * */
    pKey: Int = 0,
    pTitle: String = "",
    pNotes: String = "",
    pCreationDate: Date = Date()
) {
    var key = pKey
    var title: ObservableField<String> = ObservableField(pTitle)
    var notes: ObservableField<String> = ObservableField(pNotes)
    var creationDate = pCreationDate

    companion object {
        fun SecureNoteScreenData.toSecureNoteDataEntity(): SecureNoteDataEntity {
            return SecureNoteDataEntity(
                key,
                title.getValueOrEmpty(),
                notes.getValueOrEmpty(),
                creationDate,
                Date()
            )
        }

        fun SecureNoteDataEntity.toSecureNoteScreenData(): SecureNoteScreenData {
            return SecureNoteScreenData(
                key,
                title,
                notes, creationDate
            )
        }
    }

    fun updateData(secureNoteScreenData: SecureNoteScreenData) {
        key = secureNoteScreenData.key
        title.set(secureNoteScreenData.title.get())
        notes.set(secureNoteScreenData.notes.get())
        creationDate = secureNoteScreenData.creationDate
    }
}
