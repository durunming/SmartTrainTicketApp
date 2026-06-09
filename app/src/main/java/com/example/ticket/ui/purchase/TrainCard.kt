package com.example.ticket

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

@Composable
fun TrainCard(
    t: TrainTicket,

    queryTab: Int,
    fromStation: String,
    toStation: String,

    expandedId: String?,
    onExpandChange: (String?) -> Unit,
    selectingSeatFor: String?,
    onSeatSelectChange: (String?) -> Unit,

    user: User,
    db: DatabaseReference,

    passengerSegment: (TrainTicket, String, String) -> Pair<String, String>
) {

    var showDetailDialog by remember { mutableStateOf(false) }

    val displayFrom = if (queryTab == 0 && fromStation.isNotBlank()) fromStation else t.from
    val displayTo = if (queryTab == 0 && toStation.isNotBlank()) toStation else t.to

    val actualToStation = if (toStation.isBlank()) t.to else toStation
    val (startTime, endTime) = if (queryTab == 0 && fromStation.isNotBlank()) {
        passengerSegment(t, fromStation, actualToStation)
    } else {
        t.departure to t.arrival
    }

    // ========== 区间票价计算 ==========
    fun calculateSegmentPrice(from: String, to: String, seatType: String): Int {
        // 构建完整站名列表
        val stations = listOf(t.from) + t.stops.map { it.station } + listOf(t.to)

        val fromIdx = stations.indexOfFirst { it.equals(from, ignoreCase = true) }
        val toIdx = stations.indexOfFirst { it.equals(to, ignoreCase = true) }

        if (fromIdx == -1 || toIdx == -1 || fromIdx >= toIdx) {
            return t.seatPrices.find { it.type == seatType }?.price ?: 0
        }

        val distance = RailNetwork.getDistance(stations, fromIdx, toIdx)
        val stopCount = toIdx - fromIdx - 1
        val trainType = t.id.take(1)
        val price = PriceCalculator.calculatePrice(distance, stopCount, trainType, seatType)

        return price.toInt()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable {
                showDetailDialog = true
            },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(8.dp)) {
            Text("${t.id} $displayFrom→$displayTo", fontSize = 14.sp)
            Text("$startTime - $endTime", fontSize = 12.sp, color = Color.Gray)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                t.seatPrices.forEach { seat ->
                    val displayPrice = if (queryTab == 0 && fromStation.isNotBlank()) {
                        // 如果到达站为空，使用车次终点站
                        val effectiveTo = if (toStation.isNotBlank()) toStation else t.to
                        calculateSegmentPrice(fromStation, effectiveTo, seat.type)
                    } else {
                        seat.price
                    }
                    Text(
                        text = "${seat.type} ￥${displayPrice} 余${seat.remaining}",
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                }
            }
        }
    }

    // 弹窗
    if (showDetailDialog) {
        AlertDialog(
            onDismissRequest = { showDetailDialog = false },
            title = { Text("车次详情", fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 基本信息
                    Text("${t.id} | $displayFrom → $displayTo", fontSize = 13.sp)
                    Text("时间: $startTime - $endTime", fontSize = 12.sp, color = Color.Gray)

                    // 经停站
                    if (t.stops.isNotEmpty()) {

                        Divider()

                        Text("经停站", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        t.stops.forEach { stop ->
                            Text("${stop.station}  ${stop.arrival} → ${stop.departure}", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    Divider()

                    // 座位选择
                    Text("选择座位", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    t.seatPrices.forEach { seat ->
                        // 显示票价（用于按钮上展示）
                        val displayPrice = if (queryTab == 0 && fromStation.isNotBlank()) {
                            // 如果到达站为空，使用车次终点站
                            val effectiveTo = if (toStation.isNotBlank()) toStation else t.to
                            calculateSegmentPrice(fromStation, effectiveTo, seat.type)
                        } else {
                            seat.price
                        }

                        // 实际票价（用于订单存储）
                        val actualPrice = if (fromStation.isNotBlank() && toStation.isNotBlank()) {
                            calculateSegmentPrice(fromStation, actualToStation, seat.type)
                        } else {
                            seat.price
                        }

                        Button(
                            onClick = {
                                if (seat.remaining <= 0) return@Button

                                // 先准备订单数据（不依赖库存快照）
                                val orderId = db.child("orders")
                                    .child(user.username)
                                    .push()
                                    .key ?: return@Button

                                val (realFromTime, realToTime) = passengerSegment(t, fromStation, actualToStation)

                                val order = Order(
                                    orderId = orderId,
                                    userId = user.username,
                                    trainId = t.id,
                                    from = fromStation.ifBlank { t.from },
                                    to = actualToStation,
                                    date = t.date,
                                    departure = realFromTime,
                                    arrival = realToTime,
                                    seatType = seat.type,
                                    price = actualPrice,
                                    status = "待支付",
                                    orderTime = System.currentTimeMillis(),
                                    stops = t.stops
                                )

                                // 先找到车次节点，再对 seatPrices 执行事务扣减
                                db.child("trains")
                                    .orderByChild("id")
                                    .equalTo(t.id)
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(snapshot: DataSnapshot) {
                                            val trainRef = snapshot.children.firstOrNull()?.ref ?: return
                                            val seatRef = trainRef.child("seatPrices")

                                            seatRef.runTransaction(object : com.google.firebase.database.Transaction.Handler {
                                                override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                                                    val seats = currentData.children.mapNotNull {
                                                        it.getValue(SeatPrice::class.java)
                                                    }.toMutableList()
                                                    val idx = seats.indexOfFirst { it.type == seat.type }
                                                    if (idx == -1 || seats[idx].remaining <= 0) {
                                                        return com.google.firebase.database.Transaction.abort()
                                                    }
                                                    seats[idx] = seats[idx].copy(remaining = seats[idx].remaining - 1)
                                                    currentData.value = seats
                                                    return com.google.firebase.database.Transaction.success(currentData)
                                                }
                                                override fun onComplete(
                                                    error: com.google.firebase.database.DatabaseError?,
                                                    committed: Boolean,
                                                    snap: com.google.firebase.database.DataSnapshot?
                                                ) {
                                                    if (committed) {
                                                        // 库存扣减成功 → 创建订单
                                                        db.child("orders")
                                                            .child(user.username)
                                                            .child(orderId)
                                                            .setValue(order)
                                                        showDetailDialog = false
                                                        onSeatSelectChange(null)
                                                    }
                                                    // 如果事务中止（库存不足），静默失败
                                                }
                                            })
                                        }
                                        override fun onCancelled(error: DatabaseError) {}
                                    })
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = seat.remaining > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (seat.remaining > 0) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(seat.type, modifier = Modifier.weight(0.33f))
                                Text("￥${displayPrice}", modifier = Modifier.weight(0.33f), textAlign = TextAlign.Center)
                                Text("余${seat.remaining}", modifier = Modifier.weight(0.33f), textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}
