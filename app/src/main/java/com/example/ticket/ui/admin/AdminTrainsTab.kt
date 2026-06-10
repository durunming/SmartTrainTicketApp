package com.example.ticket

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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.example.ticket.ui.theme.Danger
import com.example.ticket.ui.theme.PriceRed
import com.example.ticket.ui.theme.StatusPaidBgLight
import com.example.ticket.ui.theme.Success
import com.example.ticket.ui.theme.SurfaceGray
import com.example.ticket.ui.theme.SurfaceGrayLight
import com.example.ticket.ui.theme.TextSecondary
import com.example.ticket.ui.theme.TicketBlue
import com.example.ticket.ui.theme.TicketBlueBg
import com.example.ticket.ui.theme.TicketBlueLight
import com.example.ticket.ui.theme.TransferBg
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            colors = ButtonDefaults.buttonColors(containerColor = Success)
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
                            color = TicketBlue
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
                                        seat.remaining == 0 -> SurfaceGrayLight
                                        seat.type == "二等座" -> StatusPaidBgLight
                                        seat.type == "一等座" -> TransferBg
                                        else -> TicketBlueBg
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
                                            color = if (seat.remaining == 0) Color.Gray else TextSecondary
                                        )
                                        Text(
                                            "￥${seat.price}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PriceRed
                                        )
                                        Surface(
                                            color = if (seat.remaining > 0) Success else Danger,
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
                                color = TextSecondary
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
                                    color = SurfaceGray,
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
                            Text("编辑", fontSize = 12.sp, color = TicketBlueLight)
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
                                colors = ButtonDefaults.buttonColors(containerColor = Danger),
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
                    colors = CardDefaults.cardColors(containerColor = SurfaceGray),
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
                    colors = CardDefaults.cardColors(containerColor = SurfaceGray),
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
                    colors = CardDefaults.cardColors(containerColor = SurfaceGray),
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
                            Text("+ 添加", fontSize = 11.sp, color = Success)
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
                            colors = CardDefaults.cardColors(containerColor = SurfaceGray),
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
                                            Text("编辑", fontSize = 10.sp, color = TicketBlueLight)
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
                colors = ButtonDefaults.buttonColors(containerColor = Success)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Success)
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