package com.andryoga.composeapp.ui.home.backupAndRestore

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.composeapp.ui.MainViewModel

@Composable
fun BackupAndRestoreScreen(modifier: Modifier = Modifier) {
    val mainViewModel = hiltViewModel<MainViewModel>()
    LaunchedEffect(Unit) {
        mainViewModel.hideTopBar()
    }
    Text("back and restore")
}