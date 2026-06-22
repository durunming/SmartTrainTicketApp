package com.example.ticket

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ticket.ui.theme.Danger
import com.example.ticket.ui.theme.StatusCancelledChip
import com.example.ticket.ui.theme.StatusPaidBg
import com.example.ticket.ui.theme.StatusPendingChip
import com.example.ticket.ui.theme.Warning
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ================= 管理员订单管理（修复了数据类型） ================= */

@Composable
fun AdminOrdersScreen(db: DatabaseReference) {

    // 存储 (用户名, 订单)
    val allOrders = remember { mutableStateListOf<Pair<String, Order>>() }
    var filter by remember { mutableStateOf("全部") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var orderToDelete by remember { mutableStateOf<Pair<String, Order>?>(null) }

    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allOrders.clear()
                snapshot.children.forEach { userNode ->
                    val userName = userNode.key ?: return@forEach
                    userNode.children.forEach { orderNode ->
                        val order = orderNode.getValue(Order::class.java)
                        if (order != null) {
                            allOrders.add(userName to order)
                        }
                    }
                }
                allOrders.sortByDescending { it.second.orderTime }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        val ref = db.child("orders")
        ref.addValueEventListener(listener)
        onDispose {
            ref.removeEventListener(listener)
        }
    }

    val filtered = allOrders.filter { (_, order) ->
        filter == "全部" || order.status == filter
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text("订单管理", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(8.dp))

        // 筛选按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("全部", "待支付", "已支付", "已取消").forEach { status ->
                FilterChip(
                    selected = filter == status,
                    onClick = { filter = status },
                    label = { Text(status, fontSize = 12.sp) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text("共 ${filtered.size} 个订单", fontSize = 12.sp, color = Color.Gray)

        Spacer(Modifier.height(8.dp))

        // 订单列表
        LazyColumn {
            items(filtered) { (userName, order) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        // 顶部信息：车次 + 状态
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = order.trainId, fontSize = 15.sp)

                            // 状态标签（与用户订单页面一致）
                            Surface(
                                color = when (order.status) {
                                    "待支付" -> StatusPendingChip
                                    "已支付" -> StatusPaidBg
                                    else -> StatusCancelledChip
                                },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = order.status,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 12.sp,
                                    color = Color.Black
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // 用户和路线
                        Text("用户：$userName", fontSize = 12.sp, color = Color.DarkGray)
                        Text("${order.from} → ${order.to}", fontSize = 13.sp)
                        // 座位信息 + 实时余量
                        var remaining by remember { mutableStateOf<Int?>(null) }
                        LaunchedEffect(order.trainId, order.seatType) {
                            db.child("trains").orderByChild("id").equalTo(order.trainId)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(snapshot: DataSnapshot) {
                                        snapshot.children.forEach { train ->
                                            val seats = train.child("seatPrices").children.mapNotNull {
                                                it.getValue(SeatPrice::class.java)
                                            }
                                            remaining = seats.find { it.type == order.seatType }?.remaining
                                        }
                                    }
                                    override fun onCancelled(error: DatabaseError) {}
                                })
                        }
                        Text(
                            "座位：${order.seatType}  ￥${order.price}  余量：${remaining ?: "加载中..."}",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )
                        Text(
                            "发车：${order.departure}  到达：${order.arrival}",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )

                        // 下单时间
                        if (order.orderTime != 0L) {
                            Text(
                                "下单时间：${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(order.orderTime))}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // 操作按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (order.status) {
                                "待支付" -> {
                                    // 取消按钮（回补库存）
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val result = TrainRepository.updateSeatAvailability(
                                                    db, order.trainId, order.seatType, 1
                                                )
                                                if (result.isSuccess) {
                                                    db.child("orders")
                                                        .child(userName)
                                                        .child(order.orderId)
                                                        .child("status")
                                                        .setValue("已取消")
                                                } else {
                                                    db.child("orders")
                                                        .child(userName)
                                                        .child(order.orderId)
                                                        .child("status")
                                                        .setValue("已取消")
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Warning
                                        ),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("取消", fontSize = 12.sp, color = Color.White)
                                    }

                                    // 删除按钮
                                    OutlinedButton(
                                        onClick = {
                                            orderToDelete = userName to order
                                            showDeleteDialog = true
                                        },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("删除", fontSize = 12.sp, color = Color.Red)
                                    }
                                }

                                "已支付" -> {
                                    // 取消并退款按钮
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val result = TrainRepository.updateSeatAvailability(
                                                    db, order.trainId, order.seatType, 1
                                                )
                                                if (result.isSuccess) {
                                                    db.child("orders")
                                                        .child(userName)
                                                        .child(order.orderId)
                                                        .child("status")
                                                        .setValue("已取消")
                                                } else {
                                                    db.child("orders")
                                                        .child(userName)
                                                        .child(order.orderId)
                                                        .child("status")
                                                        .setValue("已取消")
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Danger
                                        ),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("取消并退款", fontSize = 12.sp, color = Color.White)
                                    }

                                    // 删除按钮
                                    OutlinedButton(
                                        onClick = {
                                            orderToDelete = userName to order
                                            showDeleteDialog = true
                                        },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("删除", fontSize = 12.sp, color = Color.Red)
                                    }
                                }

                                else -> {
                                    // 已取消订单：只显示删除按钮
                                    OutlinedButton(
                                        onClick = {
                                            orderToDelete = userName to order
                                            showDeleteDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("删除订单", fontSize = 12.sp, color = Color.Red)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteDialog && orderToDelete != null) {
        val (userName, order) = orderToDelete!!
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                orderToDelete = null
            },
            title = { Text("确认删除", fontSize = 16.sp) },
            text = {
                Text(
                    "确定要删除订单 ${order.trainId} (${order.from}→${order.to}) 吗？\n" +
                            if (order.status == "待支付") "该订单未支付，删除后将恢复座位余量。\n" else "" +
                                    "此操作不可恢复。",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        // 如果是待支付订单，先回补库存再删除
                        if (order.status == "待支付") {
                            db.child("trains")
                                .orderByChild("id")
                                .equalTo(order.trainId)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(snapshot: DataSnapshot) {
                                        if (snapshot.childrenCount > 0) {
                                            snapshot.children.forEach { train ->
                                                val seats = train.child("seatPrices").children.mapNotNull {
                                                    it.getValue(SeatPrice::class.java)
                                                }
                                                val updated = seats.map { seat ->
                                                    if (seat.type == order.seatType) {
                                                        seat.copy(remaining = seat.remaining + 1)
                                                    } else {
                                                        seat
                                                    }
                                                }
                                                train.ref.child("seatPrices").setValue(updated)
                                            }
                                        }
                                        // 库存更新后删除订单
                                        db.child("orders")
                                            .child(userName)
                                            .child(order.orderId)
                                            .removeValue()
                                    }
                                    override fun onCancelled(error: DatabaseError) {
                                        db.child("orders")
                                            .child(userName)
                                            .child(order.orderId)
                                            .removeValue()
                                    }
                                })
                        } else {
                            // 已支付或已取消订单直接删除
                            db.child("orders")
                                .child(userName)
                                .child(order.orderId)
                                .removeValue()
                        }
                        showDeleteDialog = false
                        orderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) {
                    Text("确认删除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    orderToDelete = null
                }) {
                    Text("取消")
                }
            }
        )
    }
}
