package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.DhilipHomeApp
import com.example.ui.theme.DhilipHomeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.network.ServerConnectionManager.initialize(applicationContext)
        com.example.data.repository.CloudDownloadManager.initialize(applicationContext)
        setContent {
            DhilipHomeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DhilipHomeApp()
                }
            }
        }
    }
}

