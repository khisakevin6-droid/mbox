package com.mbox.stream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mbox.stream.navigation.MboxRoot
import com.mbox.stream.ui.theme.MboxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            val prefs by vm.prefs.collectAsState()
            MboxTheme(themeMode = prefs.theme) {
                MboxRoot(vm)
            }
        }
    }
}
