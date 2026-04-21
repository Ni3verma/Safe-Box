package com.andryoga.safebox.ui.singleRecord

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import javax.inject.Inject

class SingleRecordRouteProvider @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) {
    fun getRoute(): SingleRecordScreenRoute {
        return savedStateHandle.toRoute()
    }
}