package com.example.getyourmoneyupnotyourfunnyup

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.getyourmoneyupnotyourfunnyup.ui.navigation.AppRoot
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The design is light-only, so system bars always use dark icons on a transparent background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            // Screens follow the light-only hi-fi; switch this on once dark variants are designed.
            GetYourMoneyUpNotYourFunnyUpTheme(darkTheme = false) {
                AppRoot()
            }
        }
    }
}
