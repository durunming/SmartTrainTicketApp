package com.example.ticket

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.database.FirebaseDatabase

@Composable
fun AdminScreen() {

    val db = FirebaseDatabase.getInstance().reference
    var adminTab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {

        // Tab切换
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { adminTab = 0 }) { Text("订单", color = if (adminTab == 0) Color.Red else Color.Gray) }
            TextButton(onClick = { adminTab = 1 }) { Text("车次", color = if (adminTab == 1) Color.Red else Color.Gray) }
            TextButton(onClick = { adminTab = 2 }) { Text("统计", color = if (adminTab == 2) Color.Red else Color.Gray) }
        }

        Divider()

        when (adminTab) {
            0 -> AdminOrdersScreen(db)
            1 -> AdminTrainsScreen(db)
            2 -> AdminStatsScreen(db)
        }
    }
}
