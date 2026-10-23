package com.andryoga.safebox.worker

import androidx.work.WorkManager
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.di.ApplicationScope
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cancels backup and restore work and drops their passwords the moment the vault locks, whoever
 * locked it: neither worker can finish without the session key, and a restore cancelled inside its
 * transaction rolls back.
 */
@Singleton
class SessionLockWorkCanceller @Inject constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val sessionKeyHolder: SessionKeyHolder,
    private val workManager: Lazy<WorkManager>,
    private val backupPasswordRegistry: BackupPasswordRegistry,
) {
    /** Subscribes to `lockEvents` for the life of the process; `MainApplication` calls it once. */
    fun start() {
        applicationScope.launch {
            sessionKeyHolder.lockEvents.collect {
                backupPasswordRegistry.clear()
                val manager = workManager.get()
                manager.cancelUniqueWork(CommonConstants.WORKER_NAME_BACKUP_DATA)
                manager.cancelUniqueWork(CommonConstants.WORKER_NAME_RESTORE_DATA)
            }
        }
    }
}
