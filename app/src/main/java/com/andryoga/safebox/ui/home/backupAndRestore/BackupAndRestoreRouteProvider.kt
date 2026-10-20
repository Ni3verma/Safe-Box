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

    /**
     * Whether the restore workflow should start now; true at most once per back stack entry.
     * The route argument outlives the ViewModel, so without this a ViewModel recreated after
     * process death while the file picker was open relaunched the picker over its own result.
     */
    fun consumeStartWithRestoreWorkflow(): Boolean {
        if (savedStateHandle.get<Boolean>(KEY_RESTORE_WORKFLOW_CONSUMED) == true) return false
        val start = getRoute().startWithRestoreWorkflow
        if (start) savedStateHandle[KEY_RESTORE_WORKFLOW_CONSUMED] = true
        return start
    }

    private companion object {
        const val KEY_RESTORE_WORKFLOW_CONSUMED = "restoreWorkflowConsumed"
    }
}