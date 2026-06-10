package com.example.ticket

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PurchaseViewModel : ViewModel() {

    private val db = FirebaseDatabase.getInstance().reference
    private var listener: ValueEventListener? = null

    // ── 查询参数 ──
    var queryTab by mutableStateOf(0)
    var fromStation by mutableStateOf("")
    var toStation by mutableStateOf("")
    var station by mutableStateOf("")
    var trainId by mutableStateOf("")

    var selectedDate by mutableStateOf(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Calendar.getInstance().time)
    )

    // ── 筛选状态 ──
    var sortType by mutableStateOf("默认排序")
    var ascending by mutableStateOf(true)
    var onlyAvailable by mutableStateOf(false)
    var onlyHighSpeed by mutableStateOf(false)
    var onlyDongche by mutableStateOf(false)
    var onlyNormalTrain by mutableStateOf(false)
    var onlyDirect by mutableStateOf(false)
    var showTransferOnly by mutableStateOf(false)
    var selectedDepartureRange by mutableStateOf("全部")
    var selectedArrivalRange by mutableStateOf("全部")

    // ── 数据 ──
    val tickets = mutableStateListOf<TrainTicket>()
    var filtered by mutableStateOf<List<TrainTicket>>(emptyList())
    var transferPlans by mutableStateOf<List<TransferPlan>>(emptyList())

    init {
        loadTickets()
    }

    private fun loadTickets() {
        val ref = db.child("trains")
        val l = object : ValueEventListener {
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
        ref.addValueEventListener(l)
        listener = l
    }

    override fun onCleared() {
        listener?.let { db.child("trains").removeEventListener(it) }
    }

    // ── 过滤逻辑（与 PurchaseScreen 原有逻辑保持一致） ──

    fun applyFilter() {
        transferPlans = emptyList()

        val dateFiltered = tickets.filter { it.date == selectedDate }

        filtered = when (queryTab) {
            0 -> {
                val direct = dateFiltered.filter { t ->
                    val line = TrainQueryEngine.timeline(t)
                    if (t.to.equals(fromStation, ignoreCase = true)) return@filter false
                    if (fromStation.isBlank() && toStation.isBlank()) return@filter true
                    if (fromStation.isNotBlank() && toStation.isBlank()) {
                        val fromIdx = line.indexOfFirst { it.first.equals(fromStation, ignoreCase = true) }
                        return@filter fromIdx != -1
                    }
                    if (fromStation.isNotBlank() && toStation.isNotBlank()) {
                        val fromIdx = line.indexOfFirst { it.first.equals(fromStation, ignoreCase = true) }
                        if (fromIdx == -1) return@filter false
                        val toIdx = line.indexOfFirst { it.first.equals(toStation, ignoreCase = true) }
                        if (toIdx == -1) return@filter false
                        return@filter fromIdx < toIdx
                    }
                    false
                }

                if (fromStation.isNotBlank() && toStation.isNotBlank() && !onlyDirect) {
                    val plans = mutableListOf<TransferPlan>()
                    dateFiltered.forEach { a ->
                        dateFiltered.forEach { b ->
                            if (a.id == b.id) return@forEach
                            val aLine = TrainQueryEngine.timeline(a)
                            val bLine = TrainQueryEngine.timeline(b)
                            val aIdxFrom = aLine.indexOfFirst { it.first.equals(fromStation, ignoreCase = true) }
                            if (aIdxFrom == -1) return@forEach
                            val bIdxTo = bLine.indexOfFirst { it.first.equals(toStation, ignoreCase = true) }
                            if (bIdxTo == -1) return@forEach
                            val possibleTransfers = aLine.withIndex().filter {
                                it.index > aIdxFrom &&
                                        it.value.first.isNotBlank() &&
                                        !it.value.first.equals(fromStation, ignoreCase = true) &&
                                        !it.value.first.equals(toStation, ignoreCase = true)
                            }
                            possibleTransfers.forEach { mid ->
                                val transferStation = mid.value.first
                                val bTransferIdx = bLine.indexOfFirst {
                                    it.first.equals(transferStation, ignoreCase = true)
                                }
                                if (bTransferIdx == -1 || bTransferIdx >= bIdxTo) return@forEach
                                val aArrivalTime = if (transferStation.equals(a.to, ignoreCase = true)) {
                                    a.arrival
                                } else {
                                    a.stops.firstOrNull { it.station.equals(transferStation, ignoreCase = true) }?.arrival
                                        ?: return@forEach
                                }
                                val bDepartureTime = if (transferStation.equals(b.from, ignoreCase = true)) {
                                    b.departure
                                } else {
                                    b.stops.firstOrNull { it.station.equals(transferStation, ignoreCase = true) }?.departure
                                        ?: return@forEach
                                }
                                val gap = TrainQueryEngine.toMin(bDepartureTime) - TrainQueryEngine.toMin(aArrivalTime)
                                if (gap in 10..360) {
                                    plans.add(TransferPlan(a, b, transferStation))
                                }
                            }
                        }
                    }
                    transferPlans = if (showTransferOnly) {
                        val filteredPlans = plans.filter { p ->
                            val departureMatch = if (selectedDepartureRange != "全部") {
                                TrainQueryEngine.isTimeInRange(p.first.departure, selectedDepartureRange)
                            } else true
                            if (!departureMatch) return@filter false
                            val arrivalMatch = if (selectedArrivalRange != "全部") {
                                TrainQueryEngine.isTimeInRange(p.second.arrival, selectedArrivalRange)
                            } else true
                            if (!arrivalMatch) return@filter false
                            true
                        }
                        filteredPlans.sortedBy { TrainQueryEngine.toMin(it.first.departure) }
                            .let { if (!ascending) it.reversed() else it }
                    } else {
                        plans
                    }
                }

                var result = direct.filter { ticket ->
                    if (onlyAvailable && ticket.seatPrices.none { it.remaining > 0 }) return@filter false
                    if (!TrainQueryEngine.checkTrainTypeMatch(ticket, onlyHighSpeed, onlyDongche, onlyNormalTrain)) return@filter false
                    val actualDeparture = if (fromStation.isNotBlank() && toStation.isNotBlank()) {
                        TrainQueryEngine.passengerSegment(ticket, fromStation, toStation).first
                    } else if (fromStation.isNotBlank()) {
                        TrainQueryEngine.passengerSegment(ticket, fromStation, ticket.to).first
                    } else {
                        ticket.departure
                    }
                    if (!TrainQueryEngine.isTimeInRange(actualDeparture, selectedDepartureRange)) return@filter false
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
                result = TrainQueryEngine.sortTickets(result, sortType, ascending, fromStation, toStation, queryTab)
                result
            }

            1 -> {
                var result = if (station.isBlank()) dateFiltered
                else dateFiltered.filter { t ->
                    t.from.equals(station, ignoreCase = true) ||
                            t.to.equals(station, ignoreCase = true) ||
                            t.stops.any { it.station.equals(station, ignoreCase = true) }
                }
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
                var result = if (trainId.isBlank()) dateFiltered
                else dateFiltered.filter { t ->
                    t.id.contains(trainId, ignoreCase = true)
                }
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
}
