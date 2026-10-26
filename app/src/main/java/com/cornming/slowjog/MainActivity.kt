package com.cornming.slowjog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cornming.slowjog.ui.HomeScreen
import com.cornming.slowjog.ui.SlowJogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SlowJogTheme {
                HomeScreen()
            }
        }
    }
}
