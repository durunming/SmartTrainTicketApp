package com.example.ticket

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ticket.ui.theme.StatsPurple
import com.example.ticket.ui.theme.Success
import com.example.ticket.ui.theme.TicketBlue
import com.example.ticket.ui.theme.TicketBlueLight
import com.example.ticket.ui.theme.Warning
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ================= 统计页面 ================= */

@Composable
fun AdminStatsScreen(db: DatabaseReference) {

    var totalOrders by remember { mutableStateOf(0) }
    var totalRevenue by remember { mutableStateOf(0) }
    var pendingOrders by remember { mutableStateOf(0) }
    var totalUsers by remember { mutableStateOf(0) }
    var totalTrains by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        // 统计订单
        db.child("orders").get().addOnSuccessListener { snap ->
            var revenue = 0
            var pending = 0
            var count = 0
            snap.children.forEach { userNode ->
                userNode.children.forEach { orderNode ->
                    val order = orderNode.getValue(Order::class.java)
                    if (order != null) {
                        count++
                        if (order.status == "已支付") revenue += order.price
                        if (order.status == "待支付") pending++
                    }
                }
            }
            totalOrders = count
            totalRevenue = revenue
            pendingOrders = pending
        }

        db.child("users").get().addOnSuccessListener { snap ->
            totalUsers = snap.childrenCount.toInt() - 1
        }

        db.child("trains").get().addOnSuccessListener { snap ->
            totalTrains = snap.childrenCount.toInt()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text("数据统计", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(12.dp))

        // 统计卡片1：订单统计
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("订单统计", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TicketBlue)
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("总订单", fontSize = 11.sp, color = Color.Gray)
                        Text("$totalOrders", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TicketBlue)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("待支付", fontSize = 11.sp, color = Color.Gray)
                        Text("$pendingOrders", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Warning)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("总收入", fontSize = 11.sp, color = Color.Gray)
                        Text("￥$totalRevenue", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Success)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 统计卡片2：系统数据
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("系统数据", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TicketBlue)
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("用户数", fontSize = 11.sp, color = Color.Gray)
                        Text("$totalUsers", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TicketBlueLight)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("车次数", fontSize = 11.sp, color = Color.Gray)
                        Text("$totalTrains", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = StatsPurple)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 批量生成按钮
        Button(
            onClick = {
                Thread {
                    try {
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        val data = RealRailGenerator.generateBatch(date)
                        TrainFirebaseService.uploadAll(data)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Warning),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("批量生成测试车次", fontSize = 14.sp, color = Color.White)
        }
    }
}
