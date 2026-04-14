package com.andryoga.safebox.ui.home.backupAndRestore

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.andryoga.safebox.ui.home.navigation.HomeRouteType
import javax.inject.Inject

class BackupAndRestoreRouteProvider @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) {
    fun getRoute(): HomeRouteType.BackupAndRestoreRoute {
        return savedStateHandle.toRoute()
    }
}