package com.andryoga.composeapp.ui

import androidx.lifecycle.ViewModel
import com.andryoga.composeapp.ui.core.TopAppBarConfig
import com.andryoga.composeapp.ui.core.TopBarState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {
    private val _topBarState = MutableStateFlow<TopBarState>(TopBarState.Hidden)
    val topBarState = _topBarState.asStateFlow()

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