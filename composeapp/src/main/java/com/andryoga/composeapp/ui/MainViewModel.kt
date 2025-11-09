package com.andryoga.composeapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.composeapp.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.composeapp.ui.core.TopAppBarConfig
import com.andryoga.composeapp.ui.core.TopBarState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    backupMetadataRepository: BackupMetadataRepository,
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