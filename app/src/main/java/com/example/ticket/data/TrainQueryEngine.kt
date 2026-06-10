package com.example.ticket

/**
 * 列车查询引擎 — 包含时间工具、乘客区段计算、换乘逻辑、过滤/排序等纯函数。
 * 所有方法均为纯函数，不依赖 Composable 状态。
 */
object TrainQueryEngine {

    /* ================= 时间工具 ================= */

    /** 将 "HH:MM" 字符串转换为分钟数 */
    fun toMin(t: String): Int {
        val clean = t.trim()
        val p = clean.split(":")
        if (p.size != 2) return 0
        val h = p[0].trim().toIntOrNull() ?: 0
        val m = p[1].trim().toIntOrNull() ?: 0
        return h * 60 + m
    }

    /** 判断是否跨天（到达时间小于发车时间） */
    fun isCrossDay(departure: String, arrival: String): Boolean {
        return toMin(arrival) < toMin(departure)
    }

    /** 判断时间是否在指定区间内 */
    fun isTimeInRange(time: String, range: String): Boolean {
        if (range == "全部") return true
        val timeMinutes = toMin(time)
        val (start, end) = range.split("-")
        val startMinutes = toMin(start)
        val endMinutes = toMin(end)
        return timeMinutes in startMinutes until endMinutes
    }

    /* ================= 乘客区段 ================= */

    /**
     * 获取乘客在车次中的实际上下车时间。
     * @return (上车时间, 下车时间)
     */
    fun passengerSegment(ticket: TrainTicket, start: String, end: String): Pair<String, String> {
        val startTime = if (start == ticket.from) {
            ticket.departure
        } else {
            ticket.stops.firstOrNull {
                it.station.equals(start, true)
            }?.departure ?: ticket.departure
        }

        val endTime = if (end.isBlank()) {
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

    /* ================= 换乘工具 ================= */

    /** 计算两段车次在中转站的换乘等待时间 */
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

    /* ================= 路线时间线 ================= */

    /** 构建车次的完整时间线：[(站名, 时间), ...] */
    fun timeline(ticket: TrainTicket): List<Pair<String, String>> {
        return listOf(ticket.from to ticket.departure) +
                ticket.stops.map { it.station to it.arrival } +
                listOf(ticket.to to ticket.arrival)
    }

    /* ================= 过滤逻辑 ================= */

    /** 判断车次类型是否匹配当前筛选条件 */
    fun checkTrainTypeMatch(
        ticket: TrainTicket,
        onlyHighSpeed: Boolean,
        onlyDongche: Boolean,
        onlyNormalTrain: Boolean
    ): Boolean {
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

    /* ================= 排序逻辑 ================= */

    /** 按指定维度对车次列表排序 */
    fun sortTickets(
        list: List<TrainTicket>,
        sortType: String,
        ascending: Boolean,
        fromStation: String,
        toStation: String,
        queryTab: Int
    ): List<TrainTicket> {
        val sorted = when (sortType) {
            "发车时间" -> list.sortedBy {
                toMin(
                    if (queryTab == 0 && fromStation.isNotBlank() && toStation.isNotBlank()) {
                        passengerSegment(it, fromStation, toStation).first
                    } else {
                        it.departure
                    }
                )
            }
            "到达时间" -> list.sortedBy {
                toMin(
                    if (queryTab == 0 && fromStation.isNotBlank() && toStation.isNotBlank()) {
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
}
