package com.example.ticket

import com.google.firebase.database.FirebaseDatabase

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
