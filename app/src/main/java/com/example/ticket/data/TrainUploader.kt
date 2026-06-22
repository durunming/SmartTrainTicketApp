package com.example.ticket

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

object TrainFirebaseService {

    private val db = FirebaseDatabase.getInstance().reference.child("trains")

    suspend fun uploadAll(data: List<TrainTicket>) {
        val dataMap = data.mapIndexed { index, ticket ->
            index.toString() to ticket
        }.toMap()
        db.setValue(dataMap).await()
    }

    suspend fun loadAll(): List<TrainTicket> {
        val snap = db.get().await()
        return snap.children.mapNotNull { it.getValue(TrainTicket::class.java) }
    }
}
