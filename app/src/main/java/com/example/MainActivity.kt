package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.UserSession
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MenuScreen
import com.example.ui.theme.CullinanTheme
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.MenuViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CullinanTheme {
                KalinalApp()
            }
        }
    }
}

@Composable
fun KalinalApp() {
    var activeSession by remember { mutableStateOf<UserSession?>(null) }
    val authViewModel: AuthViewModel = viewModel()
    val menuViewModel: MenuViewModel = viewModel()

    val session = activeSession
    if (session == null) {
        LoginScreen(
            viewModel = authViewModel,
            onLoginSuccess = { userSession ->
                activeSession = userSession
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        BackHandler {
            activeSession = null
        }

        MenuScreen(
            userSession = session,
            viewModel = menuViewModel,
            onLogout = {
                activeSession = null
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
