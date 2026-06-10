package com.example.ticket

import androidx.compose.runtime.*
import com.example.ticket.ui.theme.TicketTheme

@Composable
fun TrainTicketApp() {

    var user by remember { mutableStateOf<User?>(null) }

    TicketTheme {
        if (user == null) {
            AuthScreen { user = it }

        } else {
            MainScreen(user!!) { user = null }
        }
    }
}
