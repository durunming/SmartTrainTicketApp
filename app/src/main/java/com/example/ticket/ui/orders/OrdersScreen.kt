package com.example.ticket

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OrdersScreen(user: User) {

    val db = FirebaseDatabase.getInstance().reference
    val orders = remember { mutableStateListOf<Order>() }

    var expandedOrderId by remember { mutableStateOf<String?>(null) }

    val timeFormat = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }

    DisposableEffect(Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                orders.clear()
                snapshot.children.forEach {
                    it.getValue(Order::class.java)?.let { o ->
                        orders.add(o)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        val ref = db.child("orders").child(user.username)
        ref.addValueEventListener(listener)
        onDispose {
            ref.removeEventListener(listener)
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {

        Text("我的订单", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        LazyColumn {

            items(orders) { o ->

                val expanded = expandedOrderId == o.orderId

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            expandedOrderId =
                                if (expanded) null else o.orderId
                        },

                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),

                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 2.dp
                    )
                ) {

                    Column(Modifier.padding(10.dp)) {

                        /* ================= 顶部信息 ================= */

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = o.trainId,
                                fontSize = 15.sp
                            )

                            // 状态标签
                            Surface(
                                color = when (o.status) {
                                    "待支付" -> Color(0xFFFFCDD2)
                                    "已支付" -> Color(0xFFC8E6C9)
                                    else -> Color(0xFFE0E0E0)
                                },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = o.status,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 12.sp,
                                    color = Color.Black
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        /* ================= 路线 ================= */

                        Text("${o.from} → ${o.to}", fontSize = 13.sp)

                        Text(
                            "座位：${o.seatType}  ￥${o.price}",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )

                        Text(
                            "发车：${o.departure}  到达：${o.arrival}",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )

                        /* ================= 下单时间 ================= */

                        if (o.orderTime != 0L) {
                            Text(
                                "下单时间：${timeFormat.format(Date(o.orderTime))}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        /* ================= 展开内容 ================= */

                        if (expanded) {
                            Spacer(Modifier.height(6.dp))

                            if (o.stops.isNotEmpty()) {
                                Text("经停站：", fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    o.stops.joinToString(" → ") { it.station },
                                    fontSize = 11.sp,
                                    color = Color.DarkGray
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            // 待支付订单的按钮
                            if (o.status == "待支付") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            db.child("orders")
                                                .child(user.username)
                                                .child(o.orderId)
                                                .child("status")
                                                .setValue("已支付")
                                        },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        shape = MaterialTheme.shapes.small,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF81C784)  // 柔和的绿色
                                        )
                                    ) {
                                        Text("✓ 支付", fontSize = 13.sp, color = Color.White)
                                    }

                                    Button(
                                        onClick = {
                                            // 原子恢复库存，成功后取消订单
                                            TrainRepository.updateSeatAvailability(
                                                db, o.trainId, o.seatType, 1,
                                                onSuccess = {
                                                    // 库存恢复成功 → 取消订单
                                                    db.child("orders")
                                                        .child(user.username)
                                                        .child(o.orderId)
                                                        .child("status")
                                                        .setValue("已取消")
                                                },
                                                onFailure = {
                                                    // 恢复失败时直接取消订单（不恢复库存）
                                                    db.child("orders")
                                                        .child(user.username)
                                                        .child(o.orderId)
                                                        .child("status")
                                                        .setValue("已取消")
                                                }
                                            )
                                        },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        shape = MaterialTheme.shapes.small,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFFB74D)  // 柔和的橙色
                                        )
                                    ) {
                                        Text("✗ 取消", fontSize = 13.sp, color = Color.White)
                                    }
                                }
                            }

                            // 已支付订单的删除按钮
                            if (o.status == "已支付") {
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        db.child("orders")
                                            .child(user.username)
                                            .child(o.orderId)
                                            .removeValue()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                    shape = MaterialTheme.shapes.small,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFEF9A9A)  // 柔和的红色
                                    )
                                ) {
                                    Text("🗑 删除订单", fontSize = 13.sp, color = Color.White)
                                }
                            }

                            // 已取消订单的删除按钮
                            if (o.status == "已取消") {
                                Spacer(Modifier.height(4.dp))
                                OutlinedButton(
                                    onClick = {
                                        db.child("orders")
                                            .child(user.username)
                                            .child(o.orderId)
                                            .removeValue()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                    shape = MaterialTheme.shapes.small,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF9E9E9E)  // 柔和的灰色
                                    )
                                ) {
                                    Text("🗑 删除订单", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= 管理员后台 ================= //