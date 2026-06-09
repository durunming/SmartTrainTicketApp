package com.example.ticket

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
