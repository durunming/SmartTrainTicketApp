package com.example.ticket

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.UUID
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

import android.os.Handler
import android.os.Looper

import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width

//添加的图标 import
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch

import androidx.compose.foundation.shape.CircleShape

/* ================= 数据模型 ================= */

data class TrainTicket(
    val id: String = "",
    val from: String = "",
    val to: String = "",
    val date: String = "",
    val departure: String = "",
    val arrival: String = "",
    val status: String = "待支付",
    val stops: List<StopTime> = emptyList(),
    val seatPrices: List<SeatPrice> = emptyList(),
    )

data class SeatPrice(
    val type: String = "",   // "一等座" "二等座" "无座"
    val price: Int = 0,
    val remaining: Int = 0
)
data class TransferPlan(
    val first: TrainTicket,
    val second: TrainTicket,
    val transferStation: String,
    val transferId: String = UUID.randomUUID().toString()
)

data class StopTime(
    val station: String = "",
    val arrival: String = "",
    val departure: String = ""
)

data class Order(
    val orderId: String = "",
    val userId: String = "",
    val trainId: String = "",
    val from: String = "",
    val to: String = "",
    val date: String = "",
    val departure: String = "",
    val arrival: String = "",
    val seatType: String = "",
    val price: Int = 0,
    val status: String = "待支付",
    val orderTime: Long = 0L,
    val stops: List<StopTime> = emptyList(),
)

data class User(
    val username: String = "",
    val password: String = ""
)


/* ================= Activity ================= */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrainTicketApp()
        }
    }
}

/* ================= App入口 ================= */

@Composable
fun TrainTicketApp() {

    var user by remember { mutableStateOf<User?>(null) }

    if (user == null) {
        AuthScreen { user = it }

    } else {
        MainScreen(user!!) { user = null }
    }
}

/* ================= 登录注册 ================= */

@Composable
fun AuthScreen(onLoginSuccess: (User) -> Unit) {

    val db = FirebaseDatabase.getInstance().reference.child("users")

    var isLogin by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(if (isLogin) "登录" else "注册", fontSize = 22.sp)

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible)
                            Icons.Default.Visibility
                        else
                            Icons.Default.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = if (passwordVisible) "隐藏密码" else "显示密码")
                        }
                    }
                )

                Spacer(Modifier.height(8.dp))

                Text(msg, color = Color.Red, fontSize = 12.sp)

                Spacer(Modifier.height(16.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            msg = "用户名和密码不能为空"
                            return@Button
                        }

                        val ref = db.child(username)

                        if (isLogin) {
                            // 登录逻辑
                            ref.get().addOnSuccessListener { snapshot ->
                                val pwd = snapshot.child("password").value?.toString()
                                if (pwd == password) {
                                    onLoginSuccess(User(username, password))
                                } else {
                                    msg = "用户名或密码错误"
                                }
                            }.addOnFailureListener {
                                msg = "网络错误，请重试"
                            }
                        } else {
                            // 注册逻辑：先检查用户名是否存在
                            ref.get().addOnSuccessListener { snapshot ->
                                if (snapshot.exists()) {
                                    msg = "用户名已存在，请更换用户名"
                                } else {
                                    ref.setValue(
                                        mapOf(
                                            "username" to username,
                                            "password" to password
                                        )
                                    ).addOnSuccessListener {
                                        msg = "注册成功"
                                        isLogin = true
                                        username = ""
                                        password = ""
                                    }.addOnFailureListener {
                                        msg = "注册失败，请重试"
                                    }
                                }
                            }.addOnFailureListener {
                                msg = "网络错误，请重试"
                            }
                        }
                    }
                ) {
                    Text(if (isLogin) "登录" else "注册", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = {
                        isLogin = !isLogin
                        msg = ""
                        username = ""
                        password = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(if (isLogin) "注册账号" else "返回登录")
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/* ================= 主界面 ================= */

@Composable
fun MainScreen(user: User, onLogout: () -> Unit) {

    val isAdmin = user.username == "admin"
    var tab by remember { mutableStateOf(0) }

    val tabs = if (isAdmin)
        listOf("购票", "订单", "管理", "我的")
    else
        listOf("购票", "订单", "我的")

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        label = { Text(t) },
                        icon = {}
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

/* ================= 购票 ================= */
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

                                val updatedSeats = t.seatPrices.map {
                                    if (it.type == seat.type) it.copy(remaining = it.remaining - 1)
                                    else it
                                }

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
                                    price = actualPrice,  // 使用实际票价
                                    status = "待支付",
                                    orderTime = System.currentTimeMillis(),
                                    stops = t.stops
                                )

                                db.child("orders")
                                    .child(user.username)
                                    .child(orderId)
                                    .setValue(order)

                                db.child("trains")
                                    .orderByChild("id")
                                    .equalTo(t.id)
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(snapshot: DataSnapshot) {
                                            snapshot.children.forEach {
                                                it.ref.child("seatPrices").setValue(updatedSeats)
                                            }
                                        }
                                        override fun onCancelled(error: DatabaseError) {}
                                    })

                                showDetailDialog = false
                                onSeatSelectChange(null)
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
@Composable
fun PurchaseScreen(user: User) {

    val db = FirebaseDatabase.getInstance().reference
    val ctx = LocalContext.current

    val calendar = Calendar.getInstance()

    var selectedDate by remember {
        mutableStateOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(calendar.time)
        )
    }

    val datePickerDialog = DatePickerDialog(
        ctx,
        { _, year, month, day ->
            selectedDate = String.format("%04d-%02d-%02d", year, month + 1, day)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    var queryTab by remember { mutableStateOf(0) }
    val queryTabs = listOf("站站查询", "站点查询", "车次查询")

    var fromStation by remember { mutableStateOf("") }
    var toStation by remember { mutableStateOf("") }
    var station by remember { mutableStateOf("") }
    var trainId by remember { mutableStateOf("") }

    var expandedId by remember { mutableStateOf<String?>(null) }

    val tickets = remember { mutableStateListOf<TrainTicket>() }
    var filtered by remember { mutableStateOf<List<TrainTicket>>(emptyList()) }

    var transferPlans by remember { mutableStateOf<List<TransferPlan>>(emptyList()) }

    var selectingSeatFor by remember { mutableStateOf<String?>(null) }

    var sortType by remember { mutableStateOf("默认排序") }
    var ascending by remember { mutableStateOf(true) }

    var onlyAvailable by remember { mutableStateOf(false) }
    var onlyHighSpeed by remember { mutableStateOf(false) }
    var onlyDongche by remember { mutableStateOf(false) }
    var onlyNormalTrain by remember { mutableStateOf(false) }
    var onlyDirect by remember { mutableStateOf(false) }
    var showTransferOnly by remember { mutableStateOf(false) }

    var showFilterDialog by remember { mutableStateOf(false) }
    var selectedDepartureRange by remember { mutableStateOf("全部") }
    var selectedArrivalRange by remember { mutableStateOf("全部") }  // 添加到达时间区间变量
    val departureRanges = listOf("全部", "00:00-06:00", "06:00-12:00", "12:00-18:00", "18:00-24:00")
    val arrivalRanges = listOf("全部", "00:00-06:00", "06:00-12:00", "12:00-18:00", "18:00-24:00")


    /* ================= 时间工具 ================= */

    fun toMin(t: String): Int {
        val clean = t.trim()
        val p = clean.split(":")
        if (p.size != 2) return 0
        val h = p[0].trim().toIntOrNull() ?: 0
        val m = p[1].trim().toIntOrNull() ?: 0
        return h * 60 + m
    }

    fun passengerSegment(ticket: TrainTicket, start: String, end: String): Pair<String, String> {

        val startTime = if (start == ticket.from) {
            ticket.departure
        } else {
            ticket.stops.firstOrNull {
                it.station.equals(start, true)
            }?.departure ?: ticket.departure
        }

        //如果到达站为空，则使用列车的终点站
        val endTime = if (end.isBlank()) {
            // 到达站为空时，使用列车终点站
            ticket.arrival
        } else if (end == ticket.to) {
            ticket.arrival
        } else {
            ticket.stops.firstOrNull {
                it.station.contains(end, true)
            }?.arrival ?: ticket.arrival
        }

        return startTime to endTime
    }

    fun transferGap(first: TrainTicket, second: TrainTicket, transferStation: String): String {

        val firstArrivalTime = first.stops
            .firstOrNull { it.station.equals(transferStation, true) }
            ?.arrival ?: first.arrival

        val secondDepartureTime = second.stops
            .firstOrNull { it.station.equals(transferStation, true) }
            ?.departure ?: second.departure

        val gap = toMin(secondDepartureTime) - toMin(firstArrivalTime)

        return if (gap >= 10) "${gap}分钟" else "时间冲突"
    }

    // 判断是否跨天（到达时间小于发车时间）
    fun isCrossDay(departure: String, arrival: String): Boolean {
        return toMin(arrival) < toMin(departure)
    }

    //辅助函数判断时间区间
    fun isTimeInRange(time: String, range: String): Boolean {
        if (range == "全部") return true
        val timeMinutes = toMin(time)
        val (start, end) = range.split("-")
        val startMinutes = toMin(start)
        val endMinutes = toMin(end)
        return timeMinutes in startMinutes until endMinutes
    }

    fun sortTickets(list: List<TrainTicket>): List<TrainTicket> {

        val sorted = when (sortType) {

            "发车时间" -> list.sortedBy {
                toMin(
                    if (
                        queryTab == 0 &&
                        fromStation.isNotBlank() &&
                        toStation.isNotBlank()
                    ) {
                        passengerSegment(it, fromStation, toStation).first
                    } else {
                        it.departure
                    }
                )
            }

            "到达时间" -> list.sortedBy {
                toMin(
                    if (
                        queryTab == 0 &&
                        fromStation.isNotBlank() &&
                        toStation.isNotBlank()
                    ) {
                        passengerSegment(it, fromStation, toStation).second
                    } else {
                        it.arrival
                    }
                )
            }

            "最低票价" -> list.sortedBy {
                it.seatPrices.minOfOrNull { s -> s.price } ?: 0
            }

            "余票最多" -> list.sortedByDescending {
                it.seatPrices.sumOf { s -> s.remaining }
            }

            else -> list
        }

        return if (ascending) sorted else sorted.reversed()
    }

    /* ================= 过滤逻辑 ================= */
    fun checkTrainTypeMatch(ticket: TrainTicket): Boolean {
        val isHighSpeed = ticket.id.startsWith("G")
        val isDongche = ticket.id.startsWith("D")
        val isNormalTrain = !isHighSpeed && !isDongche

        return when {
            onlyHighSpeed -> isHighSpeed
            onlyDongche -> isDongche
            onlyNormalTrain -> isNormalTrain
            else -> true
        }
    }

    fun applyFilter() {
        transferPlans = emptyList()

        val dateFiltered = tickets.filter {
            it.date == selectedDate
        }

        filtered = when (queryTab) {

            0 -> {
                fun timeline(t: TrainTicket): List<Pair<String, String>> {
                    return listOf(t.from to t.departure) +
                            t.stops.map { it.station to it.arrival } +
                            listOf(t.to to t.arrival)
                }

                // ================= 直达方案计算 ================= //
                val direct = dateFiltered.filter { t ->
                    val line = timeline(t)
                    // 排除终点站就是出发站的车次
                    if (t.to.equals(fromStation, ignoreCase = true)) return@filter false
                    // 情况1：出发站和到达站都为空 → 显示所有车次
                    if (fromStation.isBlank() && toStation.isBlank()) {
                        return@filter true
                    }
                    // 情况2：只有出发站不为空
                    if (fromStation.isNotBlank() && toStation.isBlank()) {
                        val fromIdx = line.indexOfFirst { it.first.equals(fromStation, ignoreCase = true) }
                        return@filter fromIdx != -1
                    }
                    // 情况3：出发站和到达站都不为空
                    if (fromStation.isNotBlank() && toStation.isNotBlank()) {
                        val fromIdx = line.indexOfFirst { it.first.equals(fromStation, ignoreCase = true) }
                        if (fromIdx == -1) return@filter false
                        val toIdx = line.indexOfFirst { it.first.equals(toStation, ignoreCase = true) }
                        if (toIdx == -1) return@filter false
                        return@filter fromIdx < toIdx
                    }
                    return@filter false
                }

                // ================= 换乘方案计算 ================= //
                if (fromStation.isNotBlank() && toStation.isNotBlank() && !onlyDirect) {
                    val plans = mutableListOf<TransferPlan>()

                    dateFiltered.forEach { a ->
                        dateFiltered.forEach { b ->
                            if (a.id == b.id) return@forEach

                            val aLine = timeline(a)
                            val bLine = timeline(b)

                            // 检查第一段是否经过出发站
                            val aIdxFrom = aLine.indexOfFirst { it.first.equals(fromStation, ignoreCase = true) }
                            if (aIdxFrom == -1) return@forEach

                            // 检查第二段是否经过目的站
                            val bIdxTo = bLine.indexOfFirst { it.first.equals(toStation, ignoreCase = true) }
                            if (bIdxTo == -1) return@forEach

                            // 获取所有可能的中转站
                            val possibleTransfers = aLine.withIndex().filter {
                                it.index > aIdxFrom &&
                                        it.value.first.isNotBlank() &&
                                        !it.value.first.equals(fromStation, ignoreCase = true) &&
                                        !it.value.first.equals(toStation, ignoreCase = true)
                            }

                            possibleTransfers.forEach { mid ->
                                val transferStation = mid.value.first

                                // 检查第二段是否经过中转站
                                val bTransferIdx = bLine.indexOfFirst {
                                    it.first.equals(transferStation, ignoreCase = true)
                                }
                                if (bTransferIdx == -1) return@forEach

                                // 中转站必须在目的站之前
                                if (bTransferIdx >= bIdxTo) return@forEach

                                // 获取第一段到达中转站的时间
                                val aArrivalTime = if (transferStation.equals(a.to, ignoreCase = true)) {
                                    a.arrival
                                } else {
                                    a.stops.firstOrNull {
                                        it.station.equals(transferStation, ignoreCase = true)
                                    }?.arrival ?: return@forEach
                                }

                                // 获取第二段从中转站出发的时间
                                val bDepartureTime = if (transferStation.equals(b.from, ignoreCase = true)) {
                                    b.departure
                                } else {
                                    b.stops.firstOrNull {
                                        it.station.equals(transferStation, ignoreCase = true)
                                    }?.departure ?: return@forEach
                                }

                                val gap = toMin(bDepartureTime) - toMin(aArrivalTime)

                                // 换乘时间合理范围：10分钟到6小时
                                if (gap in 10..360) {
                                    plans.add(TransferPlan(a, b, transferStation))
                                }
                            }
                        }
                    }

                    // ================= 对换乘方案应用筛选 ================= //
                    val filteredPlans = plans.filter { plan ->
                        val first = plan.first
                        val second = plan.second

                        // 1. 有票筛选
                        val firstHasTicket = first.seatPrices.any { it.remaining > 0 }
                        val secondHasTicket = second.seatPrices.any { it.remaining > 0 }
                        if (onlyAvailable && (!firstHasTicket || !secondHasTicket)) return@filter false

                        // 2. 车型筛选（两段都要匹配）
                        val firstTypeMatch = checkTrainTypeMatch(first)
                        val secondTypeMatch = checkTrainTypeMatch(second)
                        if (!firstTypeMatch || !secondTypeMatch) return@filter false

                        // 3. 发车时间筛选：只看第一段的发车时间
                        val departureMatch = if (selectedDepartureRange != "全部") {
                            isTimeInRange(first.departure, selectedDepartureRange)
                        } else true
                        if (!departureMatch) return@filter false

                        // 4. 到达时间筛选：只看第二段的到达时间
                        val arrivalMatch = if (selectedArrivalRange != "全部") {
                            isTimeInRange(second.arrival, selectedArrivalRange)
                        } else true
                        if (!arrivalMatch) return@filter false

                        true
                    }

                    // 对换乘方案排序（根据第一段的发车时间）
                    transferPlans = filteredPlans.sortedBy {
                        toMin(it.first.departure)
                    }
                    if (!ascending) transferPlans = transferPlans.reversed()
                }

                // 返回结果
                when {
                    showTransferOnly -> emptyList()
                    else -> {
                        // 对直达车次应用完整筛选
                        var filteredDirect = direct.filter { ticket ->
                            // 有票筛选
                            if (onlyAvailable && ticket.seatPrices.none { it.remaining > 0 }) return@filter false
                            // 车型筛选
                            if (!checkTrainTypeMatch(ticket)) return@filter false
                            // 发车时间筛选
                            val actualDeparture = if (fromStation.isNotBlank() && toStation.isNotBlank()) {
                                passengerSegment(ticket, fromStation, toStation).first
                            } else if (fromStation.isNotBlank()) {
                                passengerSegment(ticket, fromStation, ticket.to).first
                            } else {
                                ticket.departure
                            }
                            if (!isTimeInRange(actualDeparture, selectedDepartureRange)) return@filter false
                            // 到达时间筛选
                            val actualArrival = if (fromStation.isNotBlank() && toStation.isNotBlank()) {
                                passengerSegment(ticket, fromStation, toStation).second
                            } else if (toStation.isNotBlank()) {
                                passengerSegment(ticket, ticket.from, toStation).second
                            } else {
                                ticket.arrival
                            }
                            if (!isTimeInRange(actualArrival, selectedArrivalRange)) return@filter false

                            true
                        }
                        // 排序
                        filteredDirect = sortTickets(filteredDirect)
                        filteredDirect
                    }
                }
            }

            1 -> {
                var result = dateFiltered.filter {
                    it.from.equals(station, ignoreCase = true) ||
                            it.to.equals(station, ignoreCase = true) ||
                            it.stops.any { s -> s.station.equals(station, ignoreCase = true) }
                }
                // 应用筛选
                result = result.filter { ticket ->
                    if (onlyAvailable && ticket.seatPrices.none { it.remaining > 0 }) return@filter false
                    if (!checkTrainTypeMatch(ticket)) return@filter false
                    if (!isTimeInRange(ticket.departure, selectedDepartureRange)) return@filter false
                    if (!isTimeInRange(ticket.arrival, selectedArrivalRange)) return@filter false
                    true
                }
                result = sortTickets(result)
                result
            }

            2 -> {
                var result = dateFiltered.filter {
                    it.id.equals(trainId, ignoreCase = true)
                }
                // 应用筛选
                result = result.filter { ticket ->
                    if (onlyAvailable && ticket.seatPrices.none { it.remaining > 0 }) return@filter false
                    if (!checkTrainTypeMatch(ticket)) return@filter false
                    if (!isTimeInRange(ticket.departure, selectedDepartureRange)) return@filter false
                    if (!isTimeInRange(ticket.arrival, selectedArrivalRange)) return@filter false
                    true
                }
                result = sortTickets(result)
                result
            }

            else -> {
                var result = dateFiltered
                // 应用筛选
                result = result.filter { ticket ->
                    if (onlyAvailable && ticket.seatPrices.none { it.remaining > 0 }) return@filter false
                    if (!checkTrainTypeMatch(ticket)) return@filter false
                    if (!isTimeInRange(ticket.departure, selectedDepartureRange)) return@filter false
                    if (!isTimeInRange(ticket.arrival, selectedArrivalRange)) return@filter false
                    true
                }
                result = sortTickets(result)
                result
            }
        }
    }

    /* ================= Firebase加载 ================= */
    LaunchedEffect(Unit) {
        db.child("trains").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                tickets.clear()
                snapshot.children.forEach {
                    it.getValue(TrainTicket::class.java)?.let { t ->
                        tickets.add(t)
                    }
                }
                applyFilter()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    /* ================= UI(优化版本）================= */

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // 第一行：标题 + 日期
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("购票系统", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = { datePickerDialog.show() },
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text("📅 $selectedDate", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(6.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            shape = RoundedCornerShape(8.dp)
        ) {

            // 查询模式Tab
            NavigationBar(
                modifier = Modifier.height(40.dp),
                containerColor = Color.Transparent
            ) {
                queryTabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = queryTab == i,
                        onClick = { queryTab = i },
                        label = { Text(t, fontSize = 12.sp) },
                        icon = {}
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {

                    // 查询区域
                    when (queryTab) {
                        0 -> {
                            var tempToStation by remember { mutableStateOf("") }
                            var tempfromStation by remember { mutableStateOf("") }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = tempfromStation,
                                    onValueChange = { tempfromStation = it },
                                    label = { Text("出发") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = tempToStation,
                                    onValueChange = { tempToStation = it },
                                    label = { Text("到达") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )

                                Button(
                                    onClick = {
                                        toStation = tempToStation
                                        fromStation = tempfromStation
                                        applyFilter()
                                    },
                                    modifier = Modifier.height(56.dp).width(60.dp).align(Alignment.Bottom),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text("查询", fontSize = 14.sp)
                                }
                            }
                        }

                        1 -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = station,
                                    onValueChange = { station = it },
                                    label = { Text("站点") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Button(
                                    onClick = { applyFilter() },
                                    modifier = Modifier.height(56.dp).width(60.dp).align(Alignment.Bottom),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("查询", fontSize = 14.sp)
                                }
                            }
                        }

                        2 -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = trainId,
                                    onValueChange = { trainId = it },
                                    label = { Text("车次号") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Button(
                                    onClick = { applyFilter() },
                                    modifier = Modifier.height(56.dp).width(60.dp).align(Alignment.Bottom),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("查询", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 排序和筛选栏（横向滚动）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 排序下拉
            var expanded by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = false,
                    onClick = { expanded = true },
                    label = { Text(sortType, fontSize = 11.sp) }
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    listOf("默认排序", "发车时间", "到达时间", "最低票价", "余票最多").forEach {
                        DropdownMenuItem(
                            text = { Text(it, fontSize = 12.sp) },
                            onClick = {
                                sortType = it
                                expanded = false
                                applyFilter()
                            }
                        )
                    }
                }
            }

            // 升序/降序
            FilterChip(
                selected = false,
                onClick = {
                    ascending = !ascending
                    applyFilter()
                },
                label = { Text(if (ascending) "↑升" else "↓降", fontSize = 11.sp) }
            )

            // 筛选按钮（点击弹出对话框）
            FilterChip(
                selected = showFilterDialog,
                onClick = { showFilterDialog = true },
                label = { Text("🔍 筛选", fontSize = 11.sp) }
            )
        }

        // 统计信息
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("共 ${filtered.size} 趟", fontSize = 11.sp, color = Color.Gray)
            if (transferPlans.isNotEmpty()) {
                Text("换乘 ${transferPlans.size} 个", fontSize = 11.sp, color = Color(0xFFFF9800))
            }
        }
        // 筛选对话框
        if (showFilterDialog) {
            AlertDialog(
                onDismissRequest = { showFilterDialog = false },
                title = { Text("筛选条件", fontSize = 16.sp) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ========== 1. 有票筛选 ==========
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("仅显示有票车次", fontSize = 13.sp)
                            Switch(
                                checked = onlyAvailable,
                                onCheckedChange = { onlyAvailable = it }
                            )
                        }
                        Divider()

                        // ========== 2. 列车类型筛选 ==========
                        Text("列车类型", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 高铁 (G字头)
                            FilterChip(
                                selected = onlyHighSpeed,
                                onClick = {
                                    onlyHighSpeed = !onlyHighSpeed
                                    if (onlyHighSpeed)
                                    {
                                        onlyDongche = false
                                        onlyNormalTrain = false
                                    }
                                },
                                label = { Text("高铁 (G)", fontSize = 11.sp) }
                            )

                            // 动车 (D字头)
                            FilterChip(
                                selected = onlyDongche,
                                onClick = {
                                    onlyDongche = !onlyDongche
                                    if (onlyDongche)
                                    {
                                        onlyHighSpeed = false
                                        onlyNormalTrain = false
                                    }
                                },
                                label = { Text("动车 (D)", fontSize = 11.sp) }
                            )

                            // 普通车 (K/T/Z等)
                            FilterChip(
                                selected = onlyNormalTrain,
                                onClick = {
                                    onlyNormalTrain = !onlyNormalTrain
                                    if (onlyNormalTrain) {
                                        onlyHighSpeed = false
                                        onlyDongche = false
                                    }
                                },
                                label = { Text("普通车", fontSize = 11.sp) }
                            )
                        }
                        Divider()

                        // ========== 3. 行程类型筛选（直达/换乘） ==========
                        Text("行程类型", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = onlyDirect,
                                onClick = {
                                    onlyDirect = !onlyDirect
                                    if (onlyDirect) {
                                        showTransferOnly = false  // 互斥：取消仅换乘
                                    }
                                },
                                label = { Text("仅直达", fontSize = 11.sp) }
                            )

                            FilterChip(
                                selected = showTransferOnly,
                                onClick = {
                                    showTransferOnly = !showTransferOnly
                                    if (showTransferOnly) {
                                        onlyDirect = false  // 互斥：取消仅直达
                                    }
                                },
                                label = { Text("仅换乘", fontSize = 11.sp) }
                            )
                        }
                        Divider()

                        // ========== 4. 发车时间区间 ==========
                        Text("发车时间区间", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            departureRanges.forEach { range ->
                                FilterChip(
                                    selected = selectedDepartureRange == range,
                                    onClick = { selectedDepartureRange = range },
                                    label = { Text(range, fontSize = 11.sp) }
                                )
                            }
                        }
                        Divider()

                        // ========== 5. 到达时间区间 ==========
                        Text("到达时间区间", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            arrivalRanges.forEach { range ->
                                FilterChip(
                                    selected = selectedArrivalRange == range,
                                    onClick = { selectedArrivalRange = range },
                                    label = { Text(range, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showFilterDialog = false
                            applyFilter()
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFilterDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        // 车次列表
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (!showTransferOnly) {
                items(filtered) { t ->
                    TrainCard(
                        t = t,
                        queryTab = queryTab,
                        fromStation = fromStation,
                        toStation = toStation,
                        expandedId = expandedId,
                        onExpandChange = { expandedId = it },
                        selectingSeatFor = selectingSeatFor,
                        onSeatSelectChange = { selectingSeatFor = it },
                        user = user,
                        db = db,
                        passengerSegment = ::passengerSegment
                    )
                }
            }

            // 换乘推荐（只在有方案时显示）
            if (queryTab == 0 && transferPlans.isNotEmpty()&& !onlyDirect) {
                item {
                    // 分割线：上下间距都小
                    Divider(
                        modifier = Modifier.padding(
                            top = 6.dp,     // 与上方直达卡片距离
                            bottom = 4.dp   // 与标题距离
                        ),
                        color = Color(0xFFFF9800),
                        thickness = 1.dp
                    )

                    // 标题：下方留一点间距
                    Text(
                        "🔄 换乘方案",
                        fontSize = 12.sp,
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(transferPlans) { p ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),  // 卡片之间 4dp
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {  // 内边距 8dp
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("中转：${p.transferStation}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "等待：${transferGap(p.first, p.second, p.transferStation)}",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }

                            Spacer(Modifier.height(4.dp))  // 信息与车次间距 4dp
                            TrainCard(
                                t = p.first,
                                queryTab = 0,
                                fromStation = fromStation,
                                toStation = p.transferStation,
                                expandedId = expandedId,
                                onExpandChange = { expandedId = it },
                                selectingSeatFor = selectingSeatFor,
                                onSeatSelectChange = { selectingSeatFor = it },
                                user = user,
                                db = db,
                                passengerSegment = ::passengerSegment
                            )

                            TrainCard(
                                t = p.second,
                                queryTab = 0,
                                fromStation = p.transferStation,
                                toStation = toStation,
                                expandedId = expandedId,
                                onExpandChange = { expandedId = it },
                                selectingSeatFor = selectingSeatFor,
                                onSeatSelectChange = { selectingSeatFor = it },
                                user = user,
                                db = db,
                                passengerSegment = ::passengerSegment
                            )
                        }
                    }
                }
            }
        }
    }
}
/* ================= 订单（用户） ================= */

@Composable
fun OrdersScreen(user: User) {

    val db = FirebaseDatabase.getInstance().reference
    val orders = remember { mutableStateListOf<Order>() }

    var expandedOrderId by remember { mutableStateOf<String?>(null) }

    val timeFormat = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }

    LaunchedEffect(Unit) {
        db.child("orders").child(user.username)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    orders.clear()
                    snapshot.children.forEach {
                        it.getValue(Order::class.java)?.let { o ->
                            orders.add(o)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
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
                                            db.child("orders")
                                                .child(user.username)
                                                .child(o.orderId)
                                                .child("status")
                                                .setValue("已取消")

                                            db.child("trains")
                                                .orderByChild("id")
                                                .equalTo(o.trainId)
                                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                                    override fun onDataChange(snapshot: DataSnapshot) {
                                                        snapshot.children.forEach { trainNode ->
                                                            val seats = trainNode.child("seatPrices").children.mapNotNull {
                                                                it.getValue(SeatPrice::class.java)
                                                            }
                                                            val updated = seats.map { seat ->
                                                                if (seat.type == o.seatType)
                                                                    seat.copy(remaining = seat.remaining + 1)
                                                                else seat
                                                            }
                                                            trainNode.ref.child("seatPrices").setValue(updated)
                                                        }
                                                    }
                                                    override fun onCancelled(error: DatabaseError) {}
                                                })
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

    LaunchedEffect(Unit) {
        db.child("orders").addValueEventListener(object : ValueEventListener {
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
        })
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
                        .padding(vertical = 4.dp)
                        .clickable { },
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

    LaunchedEffect(Unit) {
        db.child("trains").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trains.clear()
                snapshot.children.forEach {
                    it.getValue(TrainTicket::class.java)?.let { train ->
                        trains.add(train)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
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
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val data = RealRailGenerator.generateBatch(date)
                    TrainFirebaseService.uploadAll(data)
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

// ================= 个人中心 ================= //
// ================= 铁路网络 ================= //

object RailNetwork {
    // 真实铁路线路
    val lines = mapOf(
        // === 国家主干线（8条）===
        "京沪高铁" to listOf("北京", "天津", "济南", "南京", "上海"),
        "京沪高铁慢线" to listOf("北京", "天津", "济南", "徐州", "南京", "苏州", "上海"),
        "京广高铁" to listOf("北京", "石家庄", "郑州", "武汉", "长沙", "广州"),
        "京广高铁慢线" to listOf("北京", "石家庄", "郑州", "武汉", "长沙", "衡阳", "广州"),
        "沿海高铁" to listOf("上海", "杭州", "温州", "福州", "厦门", "深圳", "广州", "香港"),
        "沪昆高铁" to listOf("上海", "杭州", "南昌", "长沙", "贵阳", "昆明"),
        "徐兰高铁" to listOf("徐州", "郑州", "洛阳", "西安", "兰州", "西宁"),
        "沪汉蓉高铁" to listOf("上海", "南京", "合肥", "武汉", "重庆", "成都"),

        // === 区域干线（12条）===
        "哈大高铁" to listOf("哈尔滨", "长春", "沈阳", "大连"),
        "济青高铁" to listOf("济南", "青岛"),
        "成渝高铁" to listOf("成都", "重庆"),
        "西成高铁" to listOf("西安", "成都"),
        "贵广高铁" to listOf("贵阳", "桂林", "广州"),
        "南广高铁" to listOf("南宁", "广州"),
        "兰新高铁" to listOf("兰州", "西宁", "乌鲁木齐"),
        "京九高铁" to listOf("北京", "南昌", "赣州", "深圳", "香港"),
        "京哈高铁" to listOf("北京", "沈阳", "长春", "哈尔滨"),
        "青盐铁路" to listOf("青岛", "连云港", "盐城", "南通", "上海"),
        "京昆高铁" to listOf("北京", "石家庄", "太原", "西安", "成都", "昆明"),
        "兰广高铁" to listOf("兰州", "成都", "贵阳", "桂林", "广州"),

        // === 华北城际（6条）===
        "京津城际" to listOf("北京", "天津"),
        "京张高铁" to listOf("北京", "张家口"),
        "京包高铁" to listOf("北京", "呼和浩特", "包头"),
        "石太高铁" to listOf("石家庄", "太原"),
        "大西高铁" to listOf("大同", "太原", "西安"),
        "呼张高铁" to listOf("呼和浩特", "张家口", "北京"),

        // === 东北城际（5条）===
        "哈齐高铁" to listOf("哈尔滨", "齐齐哈尔"),
        "哈牡高铁" to listOf("哈尔滨", "牡丹江"),
        "长珲城际" to listOf("长春", "吉林", "延吉"),
        "沈丹高铁" to listOf("沈阳", "丹东"),
        "京通高铁" to listOf("北京", "赤峰", "通辽"),

        // === 华东城际（8条）===
        "沪宁城际" to listOf("上海", "苏州", "无锡", "常州", "南京"),
        "宁杭高铁" to listOf("南京", "杭州"),
        "杭甬高铁" to listOf("杭州", "宁波"),
        "合宁高铁" to listOf("合肥", "南京"),
        "合武高铁" to listOf("合肥", "武汉"),
        "合福高铁" to listOf("合肥", "黄山", "福州"),
        "甬台温铁路" to listOf("宁波", "台州", "温州"),
        "龙厦铁路" to listOf("龙岩", "漳州", "厦门"),

        // === 华中城际（6条）===
        "郑西高铁" to listOf("郑州", "洛阳", "西安"),
        "郑万高铁" to listOf("郑州", "襄阳", "万州", "重庆"),
        "武九高铁" to listOf("武汉", "九江", "南昌"),
        "长益常铁路" to listOf("长沙", "益阳", "常德"),
        "怀邵衡铁路" to listOf("怀化", "邵阳", "衡阳"),
        "郑阜高铁" to listOf("郑州", "周口", "阜阳", "合肥"),

        // === 华南城际（6条）===
        "广深港高铁" to listOf("广州", "深圳", "香港"),
        "广珠城际" to listOf("广州", "佛山", "中山", "珠海", "澳门"),
        "深茂铁路" to listOf("深圳", "中山", "江门", "茂名"),
        "柳南城际" to listOf("柳州", "南宁"),
        "钦北高铁" to listOf("钦州", "北海"),
        "海南环岛高铁" to listOf("海口", "三亚"),

        // === 西南城际（6条）===
        "成贵高铁" to listOf("成都", "眉山", "乐山", "宜宾", "毕节", "贵阳"),
        "渝昆高铁" to listOf("重庆", "泸州", "宜宾", "昭通", "昆明"),
        "成雅铁路" to listOf("成都", "雅安"),
        "大丽铁路" to listOf("大理", "丽江"),
        "滇藏铁路" to listOf("昆明", "丽江", "拉萨"),
        "成昆铁路" to listOf("成都", "攀枝花", "昆明"),

        // === 西北城际（4条）===
        "银西高铁" to listOf("银川", "西安"),
        "兰渝铁路" to listOf("兰州", "陇南", "广元", "南充", "重庆"),
        "宝成铁路" to listOf("宝鸡", "汉中", "广元", "成都"),
        "青藏铁路" to listOf("西宁", "拉萨")
    )


    // 站点间距离
    val stationDistances = mutableMapOf<Pair<String, String>, Int>().apply {

        put("北京" to "天津", 120)
        put("北京" to "石家庄", 280)
        put("北京" to "济南", 410)
        put("北京" to "沈阳", 700)
        put("北京" to "呼和浩特", 460)
        put("北京" to "太原", 500)
        put("北京" to "郑州", 700)
        put("北京" to "张家口", 180)

        put("天津" to "济南", 310)
        put("天津" to "沈阳", 660)
        put("天津" to "石家庄", 320)

        put("石家庄" to "郑州", 410)
        put("石家庄" to "太原", 220)
        put("石家庄" to "济南", 320)
        put("石家庄" to "天津", 320)

        put("太原" to "西安", 600)
        put("太原" to "石家庄", 220)
        put("太原" to "郑州", 430)
        put("太原" to "呼和浩特", 500)

        put("呼和浩特" to "银川", 720)
        put("呼和浩特" to "包头", 180)
        put("呼和浩特" to "太原", 500)
        put("呼和浩特" to "北京", 460)

        put("包头" to "呼和浩特", 180)
        put("包头" to "银川", 550)

        put("张家口" to "北京", 180)
        put("张家口" to "呼和浩特", 240)

        // 东北
        put("沈阳" to "长春", 300)
        put("沈阳" to "大连", 380)
        put("沈阳" to "哈尔滨", 540)
        put("沈阳" to "天津", 660)
        put("沈阳" to "北京", 700)

        put("长春" to "哈尔滨", 240)
        put("长春" to "沈阳", 300)
        put("长春" to "吉林", 110)

        put("哈尔滨" to "长春", 240)
        put("哈尔滨" to "沈阳", 540)
        put("哈尔滨" to "齐齐哈尔", 280)
        put("哈尔滨" to "牡丹江", 290)

        put("大连" to "沈阳", 380)
        put("大连" to "烟台", 160)

        put("吉林" to "长春", 110)
        put("吉林" to "延吉", 320)

        put("齐齐哈尔" to "哈尔滨", 280)
        put("齐齐哈尔" to "黑河", 550)

        put("牡丹江" to "哈尔滨", 290)

        put("延吉" to "吉林", 320)

        // 华东
        put("济南" to "南京", 620)
        put("济南" to "青岛", 350)
        put("济南" to "徐州", 270)
        put("济南" to "天津", 310)
        put("济南" to "石家庄", 320)
        put("济南" to "郑州", 430)
        put("济南" to "合肥", 580)

        put("青岛" to "济南", 350)
        put("青岛" to "烟台", 200)
        put("青岛" to "威海", 250)
        put("青岛" to "大连", 160)

        put("南京" to "上海", 280)
        put("南京" to "合肥", 150)
        put("南京" to "杭州", 260)
        put("南京" to "苏州", 210)
        put("南京" to "徐州", 340)
        put("南京" to "武汉", 540)

        put("上海" to "南京", 280)
        put("上海" to "苏州", 90)
        put("上海" to "杭州", 160)
        put("上海" to "宁波", 320)
        put("上海" to "合肥", 450)

        put("杭州" to "上海", 160)
        put("杭州" to "南京", 260)
        put("杭州" to "宁波", 160)
        put("杭州" to "温州", 380)
        put("杭州" to "南昌", 580)
        put("杭州" to "合肥", 400)

        put("苏州" to "上海", 90)
        put("苏州" to "南京", 210)
        put("苏州" to "无锡", 50)

        put("无锡" to "苏州", 50)
        put("无锡" to "南京", 180)

        put("宁波" to "杭州", 160)
        put("宁波" to "温州", 220)

        put("温州" to "杭州", 380)
        put("温州" to "宁波", 220)
        put("温州" to "福州", 300)

        put("合肥" to "南京", 150)
        put("合肥" to "武汉", 360)
        put("合肥" to "郑州", 570)
        put("合肥" to "南昌", 450)
        put("合肥" to "杭州", 400)
        put("合肥" to "上海", 450)

        put("南昌" to "杭州", 580)
        put("南昌" to "长沙", 340)
        put("南昌" to "武汉", 360)
        put("南昌" to "福州", 540)
        put("南昌" to "合肥", 450)
        put("南昌" to "广州", 800)
        put("南昌" to "深圳", 850)

        put("福州" to "温州", 300)
        put("福州" to "厦门", 250)
        put("福州" to "南昌", 540)
        put("福州" to "台北", 300)

        put("厦门" to "福州", 250)
        put("厦门" to "深圳", 530)

        put("徐州" to "济南", 270)
        put("徐州" to "南京", 340)
        put("徐州" to "郑州", 360)
        put("徐州" to "合肥", 300)

        put("烟台" to "青岛", 200)
        put("烟台" to "大连", 160)

        put("威海" to "青岛", 250)

        put("无锡" to "苏州", 50)
        put("无锡" to "南京", 180)
        put("无锡" to "常州", 70)

        put("常州" to "无锡", 70)
        put("常州" to "南京", 120)

        // 华中
        put("郑州" to "武汉", 540)
        put("郑州" to "西安", 510)
        put("郑州" to "石家庄", 410)
        put("郑州" to "徐州", 360)
        put("郑州" to "济南", 430)
        put("郑州" to "合肥", 570)
        put("郑州" to "洛阳", 120)
        put("郑州" to "长沙", 800)

        put("武汉" to "长沙", 360)
        put("武汉" to "郑州", 540)
        put("武汉" to "合肥", 360)
        put("武汉" to "南昌", 360)
        put("武汉" to "重庆", 1000)
        put("武汉" to "西安", 800)
        put("武汉" to "南京", 540)

        put("长沙" to "广州", 690)
        put("长沙" to "南昌", 340)
        put("长沙" to "武汉", 360)
        put("长沙" to "贵阳", 880)
        put("长沙" to "郑州", 800)
        put("长沙" to "衡阳", 150)

        put("洛阳" to "郑州", 120)
        put("洛阳" to "西安", 330)

        put("衡阳" to "长沙", 150)
        put("衡阳" to "广州", 460)
        put("衡阳" to "南宁", 550)

        // 华南
        put("广州" to "深圳", 100)
        put("广州" to "长沙", 690)
        put("广州" to "南宁", 560)
        put("广州" to "桂林", 480)
        put("广州" to "海口", 600)
        put("广州" to "香港", 130)
        put("广州" to "南昌", 800)
        put("广州" to "厦门", 650)

        put("深圳" to "广州", 100)
        put("深圳" to "厦门", 530)
        put("深圳" to "香港", 30)
        put("深圳" to "南昌", 850)

        put("南宁" to "广州", 560)
        put("南宁" to "桂林", 380)
        put("南宁" to "昆明", 800)
        put("南宁" to "贵阳", 560)
        put("南宁" to "衡阳", 550)

        put("桂林" to "南宁", 380)
        put("桂林" to "贵阳", 380)
        put("桂林" to "广州", 480)
        put("桂林" to "长沙", 550)

        put("海口" to "广州", 600)
        put("海口" to "三亚", 290)

        put("三亚" to "海口", 290)

        put("香港" to "深圳", 30)
        put("香港" to "广州", 130)

        // 西南
        put("成都" to "重庆", 300)
        put("成都" to "贵阳", 650)
        put("成都" to "西安", 650)
        put("成都" to "昆明", 1100)
        put("成都" to "拉萨", 1700)

        put("重庆" to "成都", 300)
        put("重庆" to "贵阳", 380)
        put("重庆" to "武汉", 1000)
        put("重庆" to "西安", 680)
        put("重庆" to "昆明", 700)
        put("重庆" to "长沙", 850)

        put("贵阳" to "成都", 650)
        put("贵阳" to "重庆", 380)
        put("贵阳" to "长沙", 880)
        put("贵阳" to "昆明", 460)
        put("贵阳" to "南宁", 560)
        put("贵阳" to "桂林", 380)

        put("昆明" to "贵阳", 460)
        put("昆明" to "成都", 1100)
        put("昆明" to "南宁", 800)
        put("昆明" to "重庆", 700)
        put("昆明" to "丽江", 520)

        put("拉萨" to "成都", 1700)
        put("拉萨" to "西宁", 1600)
        put("拉萨" to "昆明", 2100)

        put("丽江" to "昆明", 520)

        // 西北
        put("西安" to "郑州", 510)
        put("西安" to "兰州", 560)
        put("西安" to "成都", 650)
        put("西安" to "太原", 600)
        put("西安" to "银川", 630)
        put("西安" to "重庆", 680)
        put("西安" to "武汉", 800)

        put("兰州" to "西安", 560)
        put("兰州" to "西宁", 220)
        put("兰州" to "乌鲁木齐", 1800)
        put("兰州" to "银川", 440)

        put("西宁" to "兰州", 220)
        put("西宁" to "拉萨", 1600)
        put("西宁" to "乌鲁木齐", 1400)

        put("乌鲁木齐" to "兰州", 1800)
        put("乌鲁木齐" to "西宁", 1400)

        put("银川" to "西安", 630)
        put("银川" to "兰州", 440)
        put("银川" to "呼和浩特", 720)

        // 补充距离
        put("台北" to "福州", 300)
        put("澳门" to "珠海", 10)
        put("珠海" to "广州", 130)
        put("珠海" to "深圳", 120)

        put("佛山" to "广州", 30)
        put("东莞" to "广州", 80)
        put("东莞" to "深圳", 70)
        put("惠州" to "深圳", 100)
        put("中山" to "广州", 90)
        put("江门" to "广州", 100)
        put("肇庆" to "广州", 110)
        put("汕头" to "厦门", 250)
        put("湛江" to "海口", 150)
        put("茂名" to "湛江", 100)

        put("绵阳" to "成都", 110)
        put("宜宾" to "成都", 240)
        put("泸州" to "重庆", 170)
        put("南充" to "成都", 220)
        put("达州" to "重庆", 220)
        put("乐山" to "成都", 140)

        put("宝鸡" to "西安", 170)
        put("天水" to "兰州", 270)
        put("汉中" to "西安", 260)
        put("延安" to "西安", 300)

        put("遵义" to "贵阳", 140)
        put("六盘水" to "贵阳", 240)
        put("安顺" to "贵阳", 90)
        put("毕节" to "贵阳", 160)

        put("芜湖" to "南京", 100)
        put("安庆" to "合肥", 180)
        put("黄山" to "杭州", 210)
        put("黄山" to "合肥", 280)

        put("九江" to "南昌", 140)
        put("赣州" to "南昌", 410)
        put("赣州" to "深圳", 530)
        put("上饶" to "南昌", 260)
        put("宜春" to "南昌", 230)

        put("岳阳" to "长沙", 160)
        put("常德" to "长沙", 180)
        put("张家界" to "长沙", 320)
        put("怀化" to "长沙", 490)
        put("怀化" to "贵阳", 280)
        put("邵阳" to "长沙", 230)

        put("柳州" to "南宁", 220)
        put("柳州" to "桂林", 160)
        put("北海" to "南宁", 200)
        put("玉林" to "南宁", 260)
        put("梧州" to "广州", 250)

        put("万州" to "重庆", 230)
        put("万州" to "襄阳", 500)
        put("恩施" to "重庆", 340)
        put("恩施" to "武汉", 520)

        put("襄阳" to "武汉", 320)
        put("襄阳" to "郑州", 400)
        put("宜昌" to "武汉", 320)
        put("宜昌" to "重庆", 550)

        put("烟台" to "青岛", 200)
        put("潍坊" to "济南", 200)
        put("临沂" to "济南", 250)
        put("菏泽" to "郑州", 230)
        put("聊城" to "济南", 130)

        put("邯郸" to "石家庄", 180)
        put("邯郸" to "郑州", 240)
        put("邢台" to "石家庄", 120)
        put("沧州" to "天津", 110)
        put("廊坊" to "北京", 60)

        put("秦皇岛" to "天津", 270)
        put("承德" to "北京", 230)
        put("唐山" to "天津", 120)

        put("大同" to "太原", 290)
        put("大同" to "张家口", 180)
        put("长治" to "太原", 220)
        put("晋城" to "郑州", 140)

        put("赤峰" to "北京", 400)
        put("通辽" to "沈阳", 280)
        put("鄂尔多斯" to "呼和浩特", 260)
        put("呼伦贝尔" to "哈尔滨", 680)

        put("西双版纳" to "昆明", 540)
        put("大理" to "昆明", 330)
        put("攀枝花" to "成都", 650)
        put("凉山" to "成都", 450)

        put("酒泉" to "兰州", 700)
        put("嘉峪关" to "兰州", 720)
        put("张掖" to "兰州", 500)
        put("武威" to "兰州", 280)
        put("金昌" to "兰州", 380)

        put("克拉玛依" to "乌鲁木齐", 320)
        put("哈密" to "乌鲁木齐", 600)
        put("吐鲁番" to "乌鲁木齐", 200)
        put("喀什" to "乌鲁木齐", 1500)
        put("伊宁" to "乌鲁木齐", 700)

        put("宁德" to "福州", 100)
        put("莆田" to "福州", 100)
        put("泉州" to "厦门", 80)
        put("漳州" to "厦门", 60)
        put("龙岩" to "厦门", 140)
        put("三明" to "福州", 190)
        put("南平" to "福州", 170)

        put("咸宁" to "武汉", 100)
        put("黄石" to "武汉", 100)
        put("孝感" to "武汉", 70)
        put("荆州" to "武汉", 220)
        put("荆门" to "武汉", 230)
        put("黄冈" to "武汉", 80)

        put("株洲" to "长沙", 50)
        put("湘潭" to "长沙", 60)
        put("娄底" to "长沙", 130)
        put("郴州" to "长沙", 320)
        put("永州" to "长沙", 330)
        put("益阳" to "长沙", 80)
    }

    // 两站间距离（计算累积距离）
    fun getDistance(stations: List<String>, fromIndex: Int, toIndex: Int): Int {
        var total = 0
        for (i in fromIndex until toIndex) {
            val pair = stations[i] to stations[i + 1]
            total += stationDistances[pair] ?: stationDistances[pair.second to pair.first] ?: 80
        }
        return total
    }
}

// ================= 票价计算 ================= //

object PriceCalculator {

    // 基准价格（元/公里）
    private val basePricePerKm = 0.55

    // 车型系数
    private val trainTypeMultiplier = mapOf(
        "G" to 1.0,   // 高铁
        "D" to 0.65,   // 动车
        "C" to 0.55,   // 城际
        "K" to 0.35,   // 快速
        "T" to 0.40,   // 特快
        "Z" to 0.45    // 直达
    )

    // 座位系数
    private val seatMultiplier = mapOf(
        "二等座" to 1.0,
        "一等座" to 1.6,
        "无座" to 0.8
    )

    fun calculatePrice(distance: Int, stationCount: Int, trainType: String, seatType: String): Int {
        // 基础票价 = 距离 × 基准价格
        var price = distance * basePricePerKm

        // 停站附加费：每站增加3公里等效距离
        price += stationCount * 3 * basePricePerKm

        // 车型系数
        val typeKey = trainType.take(1)
        price *= trainTypeMultiplier[typeKey] ?: 1.0

        // 座位系数
        price *= seatMultiplier[seatType] ?: 1.0

        // 四舍五入到最近的5元
        return price.toInt()
    }

    fun generateSeatPrices(distance: Int, stationCount: Int, trainType: String): List<SeatPrice> {
        val seatTypes = listOf("二等座", "一等座", "无座")
        return seatTypes.map { seatType ->
            val price = calculatePrice(distance, stationCount, trainType, seatType)
            SeatPrice(
                type = seatType,
                price = price,
                remaining = when (seatType) {
                    "二等座" -> (30..120).random()
                    "一等座" -> (5..40).random()
                    else -> (10..60).random()
                }
            )
        }
    }
}

// ================= 车次号生成 ================= //

object RealRailGenerator {

    private var idCounter = 1

    // 真实车次号段
    private val trainPrefixes = listOf(
        "G" to listOf(1..99, 100..199, 200..299, 300..399),      // 高铁
        "D" to listOf(700..799, 2000..2999, 3000..3999),         // 动车
        "C" to listOf(1000..1999, 2000..2999),                   // 城际
        "K" to listOf(100..999, 1000..1999),                     // 快速
        "T" to listOf(1..99, 100..299),                          // 特快
        "Z" to listOf(1..99, 100..299)                           // 直达
    )

    private fun generateTrainId(lineName: String): String {
        val prefix = when {
            lineName.contains("高铁") -> "G"
            lineName.contains("动车") || lineName.contains("沿海") -> "D"
            lineName.contains("城际") -> "C"
            else -> "G"
        }

        val ranges = trainPrefixes.find { it.first == prefix }?.second ?: listOf(1000..1999)
        val range = ranges[idCounter % ranges.size]
        val number = range.start + (idCounter % (range.endInclusive - range.start + 1))
        idCounter++
        // 随机数 0-9
        val randomDigit = (0..9).random()

        return "$prefix$number$randomDigit"
    }

    private fun formatTime(minutes: Int): String {
        val hour = (minutes / 60) % 24
        val minute = minutes % 60
        return String.format("%02d:%02d", hour, minute)
    }

    private fun parseTime(timeStr: String): Int {
        val parts = timeStr.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    private fun generateDepartureTime(tripIndex: Int): Int {
        // 发车时间范围：6:00 到 22:00 之间（6*60 = 360, 22*60 = 1320）
        val startHour = 6  // 最早6点发车
        val endHour = 22   // 最晚22点发车

        // 完全随机生成发车时间（分钟）
        val randomMinutes = (startHour * 60) + (0..(endHour - startHour) * 60).random()

        // 每半小时到一小时一班车，增加变化
        val variance = (-15..15).random()  // 加减15分钟的变化

        return randomMinutes + variance
    }

    private fun calculateTravelTime(distance: Int, trainType: String): Int {
        val speed = when (trainType.take(1)) {
            "G" -> 350  // km/h
            "D" -> 250
            "C" -> 160
            else -> 120
        }
        // 时间（分钟）= 距离 / 速度 * 60 + 停站附加时间
        return (distance.toDouble() / speed * 60).toInt() + 5
    }

    // 多天批次生成
    fun generateBatchForDays(startDate: String, days: Int): List<TrainTicket> {
        val result = mutableListOf<TrainTicket>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()

        // 解析传入的起始日期
        calendar.time = dateFormat.parse(startDate) ?: Date()

        // 往前推7天
        calendar.add(Calendar.DAY_OF_YEAR, -(days/2))

        // 总共天数
        for (i in 0 until days) {
            val currentDate = dateFormat.format(calendar.time)
            result.addAll(generateBatch(currentDate))
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return result
    }

    fun generateTrain(lineName: String, stations: List<String>, date: String, tripIndex: Int): TrainTicket {
        val trainId = generateTrainId(lineName)
        val trainType = trainId.take(1)

        val fromStation = stations.first()
        val toStation = stations.last()

        // 计算总距离
        val totalDistance = RailNetwork.getDistance(stations, 0, stations.size - 1)

        // 生成发车时间
        val departureMinutes = generateDepartureTime(tripIndex)
        val departureTime = formatTime(departureMinutes)

        // 计算到达时间
        val travelMinutes = calculateTravelTime(totalDistance, trainType)
        var arrivalMinutes = departureMinutes + travelMinutes
        if (arrivalMinutes >= 24 * 60) arrivalMinutes -= 24 * 60  // 跨天

        // 生成经停站（计算每站时间）
        val stops = mutableListOf<StopTime>()
        var currentTime = departureMinutes

        for (i in 1 until stations.size - 1) {
            val station = stations[i]

            // 计算上一站到当前站的距离
            val segmentDistance = RailNetwork.getDistance(stations, i - 1, i)
            val segmentTravelTime = calculateTravelTime(segmentDistance, trainType)

            val arrival = currentTime + segmentTravelTime
            val depart = arrival + 3

            stops.add(
                StopTime(
                    station = station,
                    arrival = formatTime(arrival),
                    departure = formatTime(depart)
                )
            )

            currentTime = depart
        }

        // 重新计算最终到达时间
        val finalArrival = if (stops.isNotEmpty()) {
            val lastStop = stops.last()
            val lastDepart = parseTime(lastStop.departure)
            val lastSegmentDistance = RailNetwork.getDistance(stations, stations.size - 2, stations.size - 1)
            val lastSegmentTime = calculateTravelTime(lastSegmentDistance, trainType)
            lastDepart + lastSegmentTime
        } else {
            arrivalMinutes
        }

        val finalArrivalTime = formatTime(if (finalArrival >= 1440) finalArrival - 1440 else finalArrival)

        // 生成座位价格
        val stationCount = stops.size
        val seatPrices = PriceCalculator.generateSeatPrices(totalDistance, stationCount, trainType)

        return TrainTicket(
            id = trainId,
            from = fromStation,
            to = toStation,
            date = date,
            departure = departureTime,
            arrival = finalArrivalTime,
            seatPrices = seatPrices,
            stops = stops,
            status = "待支付"
        )
    }

    fun generateBatch(date: String): List<TrainTicket> {
        val result = mutableListOf<TrainTicket>()

        RailNetwork.lines.forEach { (lineName, stations) ->
            // 每条线路生成3-6个车次
            val tripCount = (1..2).random()
            for (trip in 0 until tripCount) {
                result.add(generateTrain(lineName, stations, date, trip))
            }
        }

        // 添加反向车次
        val reverseResult = mutableListOf<TrainTicket>()
        result.forEach { ticket ->
            val reverseStations = RailNetwork.lines.values
                .find { it.contains(ticket.from) && it.contains(ticket.to) }
                ?.reversed()

            if (reverseStations != null) {
                val reverseTicket = generateTrain(
                    "反向-${ticket.id}",
                    reverseStations,
                    date,
                    (0..4).random()
                )
                reverseResult.add(reverseTicket)
            }
        }

        result.addAll(reverseResult)
        return result
    }
}

// ================= 车次上传 ================= //

object TrainFirebaseService {

    private val db = FirebaseDatabase.getInstance().reference.child("trains")

    fun uploadAll(data: List<TrainTicket>) {
        // 将列表转为 Map，key 用索引
        val dataMap = data.mapIndexed { index, ticket ->
            index.toString() to ticket
        }.toMap()

        db.setValue(dataMap)
    }

    fun loadAll(onResult: (List<TrainTicket>) -> Unit) {
        db.get().addOnSuccessListener { snap ->
            val list = snap.children.mapNotNull { it.getValue(TrainTicket::class.java) }
            onResult(list)
        }
    }
}

// 个人中心
@Composable
fun ProfileScreen(user: User, onLogout: () -> Unit) {
    var showChangePwd by remember { mutableStateOf(false) }
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("空闲") }

    // 用户订单统计
    var totalOrders by remember { mutableStateOf(0) }
    var pendingOrders by remember { mutableStateOf(0) }
    var completedOrders by remember { mutableStateOf(0) }

    var showLogoutDialog by remember { mutableStateOf(false) }

    val db = FirebaseDatabase.getInstance().reference

    // 加载用户订单统计
    LaunchedEffect(Unit) {
        db.child("orders").child(user.username).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var total = 0
                var pending = 0
                var completed = 0
                snapshot.children.forEach { orderNode ->
                    val order = orderNode.getValue(Order::class.java)
                    if (order != null) {
                        total++
                        if (order.status == "待支付") pending++
                        if (order.status == "已支付") completed++
                    }
                }
                totalOrders = total
                pendingOrders = pending
                completedOrders = completed
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // 标题
        Text("个人中心", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(16.dp))

        // 用户信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                // 头像区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(60.dp),
                        shape = CircleShape,
                        color = Color(0xFFE3F2FD)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                user.username.take(1).uppercase(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1565C0)
                            )
                        }
                    }
                }

                // 用户名
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("用户名", fontSize = 13.sp, color = Color.Gray)
                    Text(user.username, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(8.dp))

                // 系统状态
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("系统状态", fontSize = 13.sp, color = Color.Gray)
                    Surface(
                        color = when {
                            status.contains("完成") -> Color(0xFFE8F5E9)
                            status.contains("中") -> Color(0xFFFFF3E0)
                            else -> Color(0xFFF5F5F5)
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            status,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = when {
                                status.contains("完成") -> Color(0xFF4CAF50)
                                status.contains("中") -> Color(0xFFFF9800)
                                else -> Color.Gray
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(12.dp))

                // 订单统计
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("总订单", fontSize = 11.sp, color = Color.Gray)
                        Text("$totalOrders", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("待支付", fontSize = 11.sp, color = Color.Gray)
                        Text("$pendingOrders", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("已完成", fontSize = 11.sp, color = Color.Gray)
                        Text("$completedOrders", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 系统操作卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("系统操作", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                Spacer(Modifier.height(12.dp))

                // 更新数据按钮
                Button(
                    onClick = {
                        loading = true
                        status = "更新铁路数据中..."

                        Thread {
                            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            val data = RealRailGenerator.generateBatchForDays(date, 3)
                            TrainFirebaseService.uploadAll(data)

                            // 使用 Handler 切回主线程
                            Handler(Looper.getMainLooper()).post {
                                status = "更新完成：${data.size}条"
                                loading = false
                            }
                        }.start()
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (loading) "更新中..." else "更新全国铁路数据", fontSize = 14.sp, color = Color.White)
                }

                Spacer(Modifier.height(10.dp))

                // 修改密码按钮
                OutlinedButton(
                    onClick = { showChangePwd = !showChangePwd },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF2196F3)
                    )
                ) {
                    Text("修改密码", fontSize = 14.sp)
                }

                Spacer(Modifier.height(10.dp))

                // 退出登录按钮
                Button(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("退出登录", fontSize = 14.sp, color = Color.White)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 说明卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Text("说明", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF757575))
                Spacer(Modifier.height(6.dp))
                Text(
                    "• 点击可更新全量车次数据\n• 该操作会覆盖现有数据库内容",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp
                )
            }
        }

        // 版本信息
        Spacer(Modifier.height(16.dp))
        Text(
            "版本 1.0.0",
            fontSize = 10.sp,
            color = Color.Gray,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }

    // 修改密码对话框
    if (showChangePwd) {
        AlertDialog(
            onDismissRequest = {
                showChangePwd = false
                oldPwd = ""
                newPwd = ""
                msg = ""
            },
            title = { Text("修改密码", fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = oldPwd,
                        onValueChange = { oldPwd = it },
                        label = { Text("原密码") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPwd,
                        onValueChange = { newPwd = it },
                        label = { Text("新密码") },
                        singleLine = true
                    )
                    if (msg.isNotBlank()) {
                        Text(msg, color = Color.Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val db = FirebaseDatabase.getInstance()
                            .reference.child("users").child(user.username)

                        db.get().addOnSuccessListener { snap ->
                            val currentPwd = snap.child("password").value?.toString()
                            if (currentPwd != oldPwd) {
                                msg = "原密码错误"
                                return@addOnSuccessListener
                            }
                            if (newPwd.isBlank()) {
                                msg = "请输入新密码"
                                return@addOnSuccessListener
                            }
                            db.child("password").setValue(newPwd)
                                .addOnSuccessListener {
                                    msg = "修改成功"
                                    oldPwd = ""
                                    newPwd = ""
                                    showChangePwd = false
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showChangePwd = false
                    oldPwd = ""
                    newPwd = ""
                    msg = ""
                }) {
                    Text("取消")
                }
            }
        )
    }
    // 退出登录确认对话框
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("确认退出", fontSize = 16.sp) },
            text = { Text("确定要退出登录吗？", fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) {
                    Text("退出", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}