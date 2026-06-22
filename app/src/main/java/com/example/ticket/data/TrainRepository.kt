package com.example.ticket

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.Transaction
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 列车数据仓库 — 封装 Firebase 车次/库存操作，保证原子性。
 */
object TrainRepository {

    /**
     * 原子更新座位库存（基于 Firebase Transaction）。
     *
     * @param db        Firebase DatabaseReference 根节点
     * @param trainId   车次 ID（如 "G101"）
     * @param seatType  座位类型（"二等座" / "一等座" / "无座"）
     * @param delta     库存变化量：-1 扣减，+1 恢复
     * @return Result<Unit> — 成功或失败（库存不足/车次不存在/网络错误）
     */
    suspend fun updateSeatAvailability(
        db: DatabaseReference,
        trainId: String,
        seatType: String,
        delta: Int
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        db.child("trains")
            .orderByChild("id")
            .equalTo(trainId)
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val trainRef = snapshot.children.firstOrNull()?.ref
                    if (trainRef == null) {
                        continuation.resume(Result.failure(Exception("车次不存在")))
                        return
                    }
                    val seatRef = trainRef.child("seatPrices")
                    seatRef.runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: com.google.firebase.database.MutableData): Transaction.Result {
                            val seats = currentData.children.mapNotNull {
                                it.getValue(SeatPrice::class.java)
                            }.toMutableList()
                            val idx = seats.indexOfFirst { it.type == seatType }
                            if (idx == -1) {
                                return Transaction.abort()
                            }
                            // 扣减时检查库存
                            if (delta < 0 && seats[idx].remaining <= 0) {
                                return Transaction.abort()
                            }
                            seats[idx] = seats[idx].copy(remaining = seats[idx].remaining + delta)
                            currentData.value = seats
                            return Transaction.success(currentData)
                        }

                        override fun onComplete(
                            error: DatabaseError?,
                            committed: Boolean,
                            snap: DataSnapshot?
                        ) {
                            if (committed) {
                                continuation.resume(Result.success(Unit))
                            } else {
                                continuation.resume(
                                    Result.failure(
                                        Exception(error?.message ?: "库存不足或座位不存在")
                                    )
                                )
                            }
                        }
                    })
                }

                override fun onCancelled(error: DatabaseError) {
                    continuation.resume(Result.failure(Exception(error.message)))
                }
            })
    }
}
