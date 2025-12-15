package com.antigravity.gpaytest.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import java.util.Date
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.UUID

data class Transaction(
    val id: String = "",
    val itemName: String = "",
    val amount: Double = 0.0,
    val timestamp: Date = Date(),
    val status: String = "PENDING", // PENDING, REGISTERED (Approved), HOLD, BNK_VERIFIED
    val ocrText: String = "",
    val parsedAmount: String? = null,
    val comments: String = "",
    val hasImage: Boolean = false // New Flag for Hybrid Storage
)

class PaymentRepository(private val firestore: FirebaseFirestore) {

    // Removed FirebaseStorage dependency and uploadImage function
    // OCR Logic handles extraction, no image upload needed.

    fun recordTransaction(transaction: Transaction, base64Image: String? = null) {
        val newDoc = firestore.collection("transactions").document()
        val transactionWithId = transaction.copy(id = newDoc.id, hasImage = base64Image != null)
        
        // 1. Write the main lightweight transaction
        newDoc.set(transactionWithId)
            .addOnSuccessListener {
                Log.d("PaymentRepository", "Transaction Meta added: ${newDoc.id}")
                
                // 2. If Image exists, write to separate collection
                if (base64Image != null) {
                    val imageDoc = firestore.collection("transaction_images").document(newDoc.id)
                    val imageData = mapOf("id" to newDoc.id, "base64" to base64Image)
                    imageDoc.set(imageData)
                        .addOnSuccessListener { Log.d("PaymentRepository", "Image Data saved: ${newDoc.id}") }
                        .addOnFailureListener { Log.e("PaymentRepository", "Image Save Failed", it) }
                }
            }
            .addOnFailureListener { e ->
                Log.w("PaymentRepository", "Error adding transaction", e)
            }
    }

    fun getTransactionImage(transactionId: String, onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        firestore.collection("transaction_images").document(transactionId).get()
            .addOnSuccessListener { document ->
                val base64 = document.getString("base64")
                if (base64 != null) {
                    onSuccess(base64)
                } else {
                    onError(Exception("Image not found"))
                }
            }
            .addOnFailureListener { onError(it) }
    }

    fun updateTransactionStatus(transactionId: String, newStatus: String, comments: String? = null) {
        if (transactionId.isBlank()) {
            Log.e("PaymentRepository", "Cannot update transaction with empty ID")
            return
        }
        val updates = mutableMapOf<String, Any>("status" to newStatus)
        if (comments != null) {
            updates["comments"] = comments
        }

        firestore.collection("transactions").document(transactionId)
            .update(updates)
            .addOnSuccessListener {
                Log.d("PaymentRepository", "Transaction $transactionId status updated to $newStatus")
            }
            .addOnFailureListener { e ->
                Log.w("PaymentRepository", "Error updating transaction status", e)
            }
    }

    fun deleteTransaction(transactionId: String) {
        if (transactionId.isBlank()) return
        firestore.collection("transactions").document(transactionId)
            .delete()
            .addOnSuccessListener {
                Log.d("PaymentRepository", "Transaction $transactionId deleted")
            }
            .addOnFailureListener { e ->
                Log.w("PaymentRepository", "Error deleting transaction", e)
            }
    }

    fun deleteAllVerifiedTransactions(onSuccess: (Int) -> Unit, onError: (Exception) -> Unit) {
        firestore.collection("transactions")
            .whereEqualTo("status", "BNK_VERIFIED")
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = firestore.batch()
                val count = snapshot.size()
                
                if (count == 0) {
                    onSuccess(0)
                    return@addOnSuccessListener
                }

                for (document in snapshot.documents) {
                    batch.delete(document.reference)
                }

                batch.commit()
                    .addOnSuccessListener {
                        Log.d("PaymentRepository", "Deleted $count verified transactions")
                        onSuccess(count)
                    }
                    .addOnFailureListener { e ->
                        Log.w("PaymentRepository", "Error deleting verified transactions", e)
                        onError(e)
                    }
            }
            .addOnFailureListener { e ->
                Log.w("PaymentRepository", "Error fetching verified transactions", e)
                onError(e)
            }
    }

    fun getTransactions(): Flow<List<Transaction>> = kotlinx.coroutines.flow.callbackFlow {
        val subscription = firestore.collection("transactions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("PaymentRepository", "Listen failed.", e)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val transactions = snapshot.toObjects(Transaction::class.java)
                    trySend(transactions)
                }
            }
        
        // Wait for the flow to be closed to remove the listener
        awaitClose { subscription.remove() }
    }
}
