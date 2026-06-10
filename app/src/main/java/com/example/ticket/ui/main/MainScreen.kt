package com.example.ticket

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun MainScreen(user: User, onLogout: () -> Unit) {

    val isAdmin = user.username == "admin"
    var tab by remember { mutableStateOf(0) }

    val tabs = if (isAdmin)
        listOf("购票", "订单", "管理", "我的")
    else
        listOf("购票", "订单", "我的")

    val icons = if (isAdmin)
        listOf(Icons.Default.Search, Icons.Default.Star, Icons.Default.Settings, Icons.Default.Person)
    else
        listOf(Icons.Default.Search, Icons.Default.Star, Icons.Default.Person)

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        label = { Text(t) },
                        icon = { Icon(icons[i], contentDescription = t) }
                    )
                }
            }
        }
    ) { padding ->

        Box(Modifier.padding(padding)) {

            if (isAdmin) {
                when (tab) {
                    0 -> PurchaseScreen(user)
                    1 -> OrdersScreen(user)
                    2 -> AdminScreen()
                    3 -> ProfileScreen(user, onLogout)
                }
            } else {
                when (tab) {
                    0 -> PurchaseScreen(user)
                    1 -> OrdersScreen(user)
                    2 -> ProfileScreen(user, onLogout)
                }
            }
        }
    }
}
