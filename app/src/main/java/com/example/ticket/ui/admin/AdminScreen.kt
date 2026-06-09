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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

/* ================= 管理员订单管理（修复了数据类型） ================= */

@Composable
fun AdminOrdersScreen(db: DatabaseReference) {

    // 存储 (用户名, 订单)
    val allOrders = remember { mutableStateListOf<Pair<String, Order>>() }
    var filter by remember { mutableStateOf("全部") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var orderToDelete by remember { mutableStateOf<Pair<String, Order>?>(null) }

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
                                    "待支付" -> Color(0xFFFFCDD2)
                                    "已支付" -> Color(0xFFC8E6C9)
                                    else -> Color(0xFFE0E0E0)
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
                                                        db.child("orders")
                                                            .child(userName)
                                                            .child(order.orderId)
                                                            .child("status")
                                                            .setValue("已取消")
                                                    }
                                                    override fun onCancelled(error: DatabaseError) {
                                                        db.child("orders")
                                                            .child(userName)
                                                            .child(order.orderId)
                                                            .child("status")
                                                            .setValue("已取消")
                                                    }
                                                })
                                        },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFF9800)
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
                                                        db.child("orders")
                                                            .child(userName)
                                                            .child(order.orderId)
                                                            .child("status")
                                                            .setValue("已取消")
                                                    }
                                                    override fun onCancelled(error: DatabaseError) {
                                                        db.child("orders")
                                                            .child(userName)
                                                            .child(order.orderId)
                                                            .child("status")
                                                            .setValue("已取消")
                                                    }
                                                })
                                        },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFEF5350)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
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

/* ================= 车次管理 ================= */

@Composable
fun AdminTrainsScreen(db: DatabaseReference) {

    val trains = remember { mutableStateListOf<TrainTicket>() }
    var showDialog by remember { mutableStateOf(false) }
    var editTrain by remember { mutableStateOf<TrainTicket?>(null) }

    DisposableEffect(Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trains.clear()
                snapshot.children.forEach {
                    it.getValue(TrainTicket::class.java)?.let { train ->
                        trains.add(train)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        val ref = db.child("trains")
        ref.addValueEventListener(listener)
        onDispose {
            ref.removeEventListener(listener)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text("车次管理", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            Text("+ 添加车次", fontSize = 14.sp, color = Color.White)
        }

        Spacer(Modifier.height(12.dp))

        Text("共 ${trains.size} 个车次", fontSize = 12.sp, color = Color.Gray)

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(trains) { train ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                train.id,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1565C0)
                            )
                            Text(
                                "${train.from} → ${train.to}",
                                fontSize = 13.sp,
                                color = Color.DarkGray
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Text(
                            "${train.departure} - ${train.arrival}",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )

                        Spacer(Modifier.height(6.dp))

                        // 座位信息（紧凑横向标签）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            train.seatPrices.forEach { seat ->
                                Surface(
                                    color = when {
                                        seat.remaining == 0 -> Color(0xFFEEEEEE)
                                        seat.type == "二等座" -> Color(0xFFE8F5E9)
                                        seat.type == "一等座" -> Color(0xFFFFF3E0)
                                        else -> Color(0xFFE3F2FD)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            seat.type,
                                            fontSize = 10.sp,
                                            color = if (seat.remaining == 0) Color.Gray else Color(0xFF757575)
                                        )
                                        Text(
                                            "￥${seat.price}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD32F2F)
                                        )
                                        Surface(
                                            color = if (seat.remaining > 0) Color(0xFF4CAF50) else Color(0xFFEF5350),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                "${seat.remaining}",
                                                fontSize = 9.sp,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 经停站信息
                        if (train.stops.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Divider(modifier = Modifier.padding(vertical = 2.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "经停站",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF757575)
                                )
                                Text(
                                    "${train.stops.size}站",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }

                            Spacer(Modifier.height(4.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                train.stops.forEach { stop ->
                                    Surface(
                                        color = Color(0xFFF5F5F5),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                stop.station,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                "${stop.arrival}→${stop.departure}",
                                                fontSize = 9.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { editTrain = train },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("编辑", fontSize = 12.sp, color = Color(0xFF2196F3))
                            }

                            Button(
                                onClick = {
                                    db.child("trains").orderByChild("id").equalTo(train.id)
                                        .addListenerForSingleValueEvent(object : ValueEventListener {
                                            override fun onDataChange(snapshot: DataSnapshot) {
                                                snapshot.children.forEach { it.ref.removeValue() }
                                            }
                                            override fun onCancelled(error: DatabaseError) {}
                                        })
                                },
                                modifier = Modifier.weight(1f).height(36.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("删除", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    // 添加/编辑对话框
    if (showDialog || editTrain != null) {
        val originalId = editTrain?.id ?: ""

        var id by remember { mutableStateOf(editTrain?.id ?: "") }
        var from by remember { mutableStateOf(editTrain?.from ?: "") }
        var to by remember { mutableStateOf(editTrain?.to ?: "") }
        var departure by remember { mutableStateOf(editTrain?.departure ?: "08:00") }
        var arrival by remember { mutableStateOf(editTrain?.arrival ?: "12:00") }

        // 座位价格和余量（字符串类型，支持清空）
        var price2 by remember { mutableStateOf(editTrain?.seatPrices?.find { it.type == "二等座" }?.price?.toString() ?: "") }
        var remaining2 by remember { mutableStateOf(editTrain?.seatPrices?.find { it.type == "二等座" }?.remaining?.toString() ?: "") }
        var price1 by remember { mutableStateOf(editTrain?.seatPrices?.find { it.type == "一等座" }?.price?.toString() ?: "") }
        var remaining1 by remember { mutableStateOf(editTrain?.seatPrices?.find { it.type == "一等座" }?.remaining?.toString() ?: "") }
        var price3 by remember { mutableStateOf(editTrain?.seatPrices?.find { it.type == "无座" }?.price?.toString() ?: "") }
        var remaining3 by remember { mutableStateOf(editTrain?.seatPrices?.find { it.type == "无座" }?.remaining?.toString() ?: "") }

        // 经停站列表
        var stopsList by remember {
            mutableStateOf(
                (editTrain?.stops ?: emptyList()).map { it.copy() }.toMutableList()
            )
        }
        var showAddStopDialog by remember { mutableStateOf(false) }
        var editingStopIndex by remember { mutableStateOf<Int?>(null) }
        var tempStopStation by remember { mutableStateOf("") }
        var tempStopArrival by remember { mutableStateOf("") }
        var tempStopDeparture by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = {
                showDialog = false
                editTrain = null
            },
            title = { Text(if (editTrain == null) "添加车次" else "编辑车次", fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 车次号 - 编辑时禁用，添加时可编辑
                    OutlinedTextField(
                        id,
                        { id = it },
                        label = { Text("车次号") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = editTrain == null
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            from,
                            { from = it },
                            label = { Text("出发站") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            to,
                            { to = it },
                            label = { Text("到达站") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            departure,
                            { departure = it },
                            label = { Text("发车时间") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            arrival,
                            { arrival = it },
                            label = { Text("到达时间") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Divider()
                    Text("票价与余量设置", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    // 二等座
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("二等座", fontSize = 12.sp, modifier = Modifier.width(50.dp))
                            OutlinedTextField(
                                value = price2,
                                onValueChange = {
                                    if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                        price2 = it
                                    }
                                },
                                label = { Text("价格") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("0") }
                            )
                            OutlinedTextField(
                                value = remaining2,
                                onValueChange = {
                                    if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                        remaining2 = it
                                    }
                                },
                                label = { Text("余量") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("0") }
                            )
                        }
                    }

                    // 一等座
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("一等座", fontSize = 12.sp, modifier = Modifier.width(50.dp))
                            OutlinedTextField(
                                value = price1,
                                onValueChange = {
                                    if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                        price1 = it
                                    }
                                },
                                label = { Text("价格") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("0") }
                            )
                            OutlinedTextField(
                                value = remaining1,
                                onValueChange = {
                                    if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                        remaining1 = it
                                    }
                                },
                                label = { Text("余量") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("0") }
                            )
                        }
                    }

                    // 无座
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("无座", fontSize = 12.sp, modifier = Modifier.width(50.dp))
                            OutlinedTextField(
                                value = price3,
                                onValueChange = {
                                    if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                        price3 = it
                                    }
                                },
                                label = { Text("价格") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("0") }
                            )
                            OutlinedTextField(
                                value = remaining3,
                                onValueChange = {
                                    if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                        remaining3 = it
                                    }
                                },
                                label = { Text("余量") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("0") }
                            )
                        }
                    }

                    Divider()

                    // 经停站管理
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("经停站", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = {
                                editingStopIndex = null
                                tempStopStation = ""
                                tempStopArrival = ""
                                tempStopDeparture = ""
                                showAddStopDialog = true
                            }
                        ) {
                            Text("+ 添加", fontSize = 11.sp, color = Color(0xFF4CAF50))
                        }
                    }

                    if (stopsList.isEmpty()) {
                        Text(
                            "暂无经停站",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        stopsList.forEachIndexed { index, stop ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stop.station, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            "到达: ${stop.arrival}  出发: ${stop.departure}",
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Row {
                                        TextButton(
                                            onClick = {
                                                editingStopIndex = index
                                                tempStopStation = stop.station
                                                tempStopArrival = stop.arrival
                                                tempStopDeparture = stop.departure
                                                showAddStopDialog = true
                                            }
                                        ) {
                                            Text("编辑", fontSize = 10.sp, color = Color(0xFF2196F3))
                                        }
                                        TextButton(
                                            onClick = { stopsList.removeAt(index) }
                                        ) {
                                            Text("删除", fontSize = 10.sp, color = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newTrain = TrainTicket(
                            id = id,
                            from = from,
                            to = to,
                            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                            departure = departure,
                            arrival = arrival,
                            seatPrices = listOf(
                                SeatPrice("二等座", price2.toIntOrNull() ?: 0, remaining2.toIntOrNull() ?: 0),
                                SeatPrice("一等座", price1.toIntOrNull() ?: 0, remaining1.toIntOrNull() ?: 0),
                                SeatPrice("无座", price3.toIntOrNull() ?: 0, remaining3.toIntOrNull() ?: 0)
                            ),
                            stops = stopsList
                        )
                        if (editTrain == null) {
                            db.child("trains").push().setValue(newTrain)
                        } else {
                            db.child("trains").orderByChild("id").equalTo(originalId)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(snapshot: DataSnapshot) {
                                        snapshot.children.forEach { it.ref.setValue(newTrain) }
                                    }
                                    override fun onCancelled(error: DatabaseError) {}
                                })
                        }
                        showDialog = false
                        editTrain = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("保存", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showDialog = false
                    editTrain = null
                }) {
                    Text("取消")
                }
            }
        )

        // 添加/编辑经停站对话框
        if (showAddStopDialog) {
            AlertDialog(
                onDismissRequest = { showAddStopDialog = false },
                title = { Text(if (editingStopIndex == null) "添加经停站" else "编辑经停站", fontSize = 16.sp) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            tempStopStation,
                            { tempStopStation = it },
                            label = { Text("站名") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                tempStopArrival,
                                { tempStopArrival = it },
                                label = { Text("到达时间") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("HH:MM") }
                            )
                            OutlinedTextField(
                                tempStopDeparture,
                                { tempStopDeparture = it },
                                label = { Text("出发时间") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("HH:MM") }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (tempStopStation.isNotBlank()) {
                                val newStop = StopTime(
                                    station = tempStopStation,
                                    arrival = tempStopArrival,
                                    departure = tempStopDeparture
                                )
                                if (editingStopIndex != null) {
                                    stopsList[editingStopIndex!!] = newStop
                                } else {
                                    stopsList.add(newStop)
                                }
                            }
                            showAddStopDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("确定", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddStopDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

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
                Text("订单统计", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("总订单", fontSize = 11.sp, color = Color.Gray)
                        Text("$totalOrders", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("待支付", fontSize = 11.sp, color = Color.Gray)
                        Text("$pendingOrders", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("总收入", fontSize = 11.sp, color = Color.Gray)
                        Text("￥$totalRevenue", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
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
                Text("系统数据", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("用户数", fontSize = 11.sp, color = Color.Gray)
                        Text("$totalUsers", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("车次数", fontSize = 11.sp, color = Color.Gray)
                        Text("$totalTrains", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9C27B0))
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
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("批量生成测试车次", fontSize = 14.sp, color = Color.White)
        }
    }
}
