package com.andryoga.safebox.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.andryoga.safebox.ui.core.TopAppBarConfig
import com.andryoga.safebox.ui.core.TopBarState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    backupMetadataRepository: BackupMetadataRepository,
    private val activeSessionManager: ActiveSessionManager,
) : ViewModel() {
    private val _topBarState = MutableStateFlow<TopBarState>(TopBarState.Hidden)
    val topBarState = _topBarState.asStateFlow()

    val isBackupPathSet: StateFlow<Boolean> = backupMetadataRepository.getBackupMetadata().map {
        it != null
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = true
    )

    private val _logoutEvent = Channel<Unit>(Channel.CONFLATED)
    val logoutEvent = _logoutEvent.receiveAsFlow()

    init {
        viewModelScope.launch {
            activeSessionManager.logoutEvent.collect {
                Timber.i("received logout event from active session manager")
                _logoutEvent.send(Unit)
            }
        }
    }

    /**
     * A screen calls this to configure and show the top bar.
     */
    fun updateTopBar(config: TopAppBarConfig) {
        _topBarState.value = TopBarState.Visible(config)
    }

    /**
     * The helper method for screens to opt-out of showing a top bar.
     */
    fun hideTopBar() {
        _topBarState.value = TopBarState.Hidden
    }
}