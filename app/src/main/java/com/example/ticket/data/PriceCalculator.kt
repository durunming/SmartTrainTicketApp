package com.example.ticket

import kotlin.math.roundToInt

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
        val rounded = (price / 5.0).roundToInt() * 5
        return rounded
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
