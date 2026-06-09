package com.example.ticket

import java.util.UUID

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
