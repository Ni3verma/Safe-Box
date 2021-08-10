package com.andryoga.safebox.data.repository.interfaces

import com.andryoga.safebox.ui.view.home.addNewData.secureNote.SecureNoteScreenData

interface SecureNoteDataRepository {
    suspend fun insertSecureNoteData(secureNoteScreenData: SecureNoteScreenData)
}
