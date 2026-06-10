package com.example.ticket

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.example.ticket.ui.theme.TransferAccent
import com.example.ticket.ui.theme.TransferBg
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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


    fun applyFilter() {
        transferPlans = emptyList()

        val dateFiltered = tickets.filter {
            it.date == selectedDate
        }

        filtered = when (queryTab) {

            0 -> {
                // ================= 直达方案计算 ================= //
                val direct = dateFiltered.filter { t ->
                    val line = TrainQueryEngine.timeline(t)
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

                            val aLine = TrainQueryEngine.timeline(a)
                            val bLine = TrainQueryEngine.timeline(b)

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

                                val gap = TrainQueryEngine.toMin(bDepartureTime) - TrainQueryEngine.toMin(aArrivalTime)

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
                        val firstTypeMatch = TrainQueryEngine.checkTrainTypeMatch(first, onlyHighSpeed, onlyDongche, onlyNormalTrain)
                        val secondTypeMatch = TrainQueryEngine.checkTrainTypeMatch(second, onlyHighSpeed, onlyDongche, onlyNormalTrain)
                        if (!firstTypeMatch || !secondTypeMatch) return@filter false

                        // 3. 发车时间筛选：只看第一段的发车时间
                        val departureMatch = if (selectedDepartureRange != "全部") {
                            TrainQueryEngine.isTimeInRange(first.departure, selectedDepartureRange)
                        } else true
                        if (!departureMatch) return@filter false

                        // 4. 到达时间筛选：只看第二段的到达时间
                        val arrivalMatch = if (selectedArrivalRange != "全部") {
                            TrainQueryEngine.isTimeInRange(second.arrival, selectedArrivalRange)
                        } else true
                        if (!arrivalMatch) return@filter false

                        true
                    }

                    // 对换乘方案排序（根据第一段的发车时间）
                    transferPlans = filteredPlans.sortedBy {
                        TrainQueryEngine.toMin(it.first.departure)
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
                            if (!TrainQueryEngine.checkTrainTypeMatch(ticket, onlyHighSpeed, onlyDongche, onlyNormalTrain)) return@filter false
                            // 发车时间筛选
                            val actualDeparture = if (fromStation.isNotBlank() && toStation.isNotBlank()) {
                                TrainQueryEngine.passengerSegment(ticket, fromStation, toStation).first
                            } else if (fromStation.isNotBlank()) {
                                TrainQueryEngine.passengerSegment(ticket, fromStation, ticket.to).first
                            } else {
                                ticket.departure
                            }
                            if (!TrainQueryEngine.isTimeInRange(actualDeparture, selectedDepartureRange)) return@filter false
                            // 到达时间筛选
                            val actualArrival = if (fromStation.isNotBlank() && toStation.isNotBlank()) {
                                TrainQueryEngine.passengerSegment(ticket, fromStation, toStation).second
                            } else if (toStation.isNotBlank()) {
                                TrainQueryEngine.passengerSegment(ticket, ticket.from, toStation).second
                            } else {
                                ticket.arrival
                            }
                            if (!TrainQueryEngine.isTimeInRange(actualArrival, selectedArrivalRange)) return@filter false

                            true
                        }
                        // 排序
                        filteredDirect = TrainQueryEngine.sortTickets(filteredDirect, sortType, ascending, fromStation, toStation, queryTab)
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
                    if (!TrainQueryEngine.checkTrainTypeMatch(ticket, onlyHighSpeed, onlyDongche, onlyNormalTrain)) return@filter false
                    if (!TrainQueryEngine.isTimeInRange(ticket.departure, selectedDepartureRange)) return@filter false
                    if (!TrainQueryEngine.isTimeInRange(ticket.arrival, selectedArrivalRange)) return@filter false
                    true
                }
                result = TrainQueryEngine.sortTickets(result, sortType, ascending, fromStation, toStation, queryTab)
                result
            }

            2 -> {
                var result = dateFiltered.filter {
                    it.id.equals(trainId, ignoreCase = true)
                }
                // 应用筛选
                result = result.filter { ticket ->
                    if (onlyAvailable && ticket.seatPrices.none { it.remaining > 0 }) return@filter false
                    if (!TrainQueryEngine.checkTrainTypeMatch(ticket, onlyHighSpeed, onlyDongche, onlyNormalTrain)) return@filter false
                    if (!TrainQueryEngine.isTimeInRange(ticket.departure, selectedDepartureRange)) return@filter false
                    if (!TrainQueryEngine.isTimeInRange(ticket.arrival, selectedArrivalRange)) return@filter false
                    true
                }
                result = TrainQueryEngine.sortTickets(result, sortType, ascending, fromStation, toStation, queryTab)
                result
            }

            else -> {
                var result = dateFiltered
                // 应用筛选
                result = result.filter { ticket ->
                    if (onlyAvailable && ticket.seatPrices.none { it.remaining > 0 }) return@filter false
                    if (!TrainQueryEngine.checkTrainTypeMatch(ticket, onlyHighSpeed, onlyDongche, onlyNormalTrain)) return@filter false
                    if (!TrainQueryEngine.isTimeInRange(ticket.departure, selectedDepartureRange)) return@filter false
                    if (!TrainQueryEngine.isTimeInRange(ticket.arrival, selectedArrivalRange)) return@filter false
                    true
                }
                result = TrainQueryEngine.sortTickets(result, sortType, ascending, fromStation, toStation, queryTab)
                result
            }
        }
    }

    /* ================= Firebase加载 ================= */
    DisposableEffect(Unit) {
        val listener = object : ValueEventListener {
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
        }
        val ref = db.child("trains")
        ref.addValueEventListener(listener)
        onDispose {
            ref.removeEventListener(listener)
        }
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
                Text("换乘 ${transferPlans.size} 个", fontSize = 11.sp, color = TransferAccent)
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
                        passengerSegment = TrainQueryEngine::passengerSegment
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
                        color = TransferAccent,
                        thickness = 1.dp
                    )

                    // 标题：下方留一点间距
                    Text(
                        "🔄 换乘方案",
                        fontSize = 12.sp,
                        color = TransferAccent,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(transferPlans) { p ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),  // 卡片之间 4dp
                        colors = CardDefaults.cardColors(containerColor = TransferBg),
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
                                    "等待：${TrainQueryEngine.transferGap(p.first, p.second, p.transferStation)}",
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
                                passengerSegment = TrainQueryEngine::passengerSegment
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
                                passengerSegment = TrainQueryEngine::passengerSegment
                            )
                        }
                    }
                }
            }
        }
    }
}