package com.antigravity.gpaytest.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.Date

data class Transaction(
    val id: String = "",
    val itemName: String = "",
    val amount: Double = 0.0,
    val timestamp: Date = Date(),
    val status: String = "SUCCESS"
)

class PaymentRepository(private val firestore: FirebaseFirestore) {

    fun recordTransaction(transaction: Transaction) {
        firestore.collection("transactions")
            .add(transaction)
            .addOnSuccessListener { documentReference ->
                Log.d("PaymentRepository", "Transaction added with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.w("PaymentRepository", "Error adding transaction", e)
            }
    }

    fun getTransactions(): Flow<List<Transaction>> = flow {
        try {
            val snapshot = firestore.collection("transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val transactions = snapshot.toObjects(Transaction::class.java)
            emit(transactions)
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Error getting transactions", e)
            emit(emptyList())
        }
    }
}
