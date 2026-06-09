package com.example.ticket

import androidx.compose.runtime.*

@Composable
fun TrainTicketApp() {

    var user by remember { mutableStateOf<User?>(null) }

    if (user == null) {
        AuthScreen { user = it }

    } else {
        MainScreen(user!!) { user = null }
    }
}
