package com.platform.smartwastemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.platform.smartwastemanager.core.theme.SmartWasteManagerTheme

/**
 * The single Activity that hosts the entire app.
 *
 * In Phase 2, this will be updated to include:
 * - The NavHost with all screen routes
 * - The bottom navigation bar
 * - Auth-guarded navigation (redirect to login if not signed in)
 * - The driver/user view toggle in the top app bar
 *
 * For now (Phase 1), it just confirms the theme and app structure compile correctly.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartWasteManagerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        // Phase 2 will replace this with the full NavHost
                        Text(text = "Smart Waste Manager — Phase 1 Setup Complete ✅")
                    }
                }
            }
        }
    }
}