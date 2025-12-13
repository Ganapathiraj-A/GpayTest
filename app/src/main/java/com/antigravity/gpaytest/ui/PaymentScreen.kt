package com.antigravity.gpaytest.ui

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.antigravity.gpaytest.data.PaymentRepository
import com.antigravity.gpaytest.data.Transaction
import java.util.Date

data class Product(val id: Int, val name: String, val price: Double)

@Composable
fun PaymentScreen(
    repository: PaymentRepository,
    onNavigateToHistory: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity
    
    // User provided VPA
    val upiId = "ganapathy.angappan-1@oksbi"
    val payeeName = "Ganapathy Angappan"

    val products = listOf(
        Product(1, "Magic Potion", 1.0),
        Product(2, "Invisibility Cloak", 1.0),
        Product(3, "Time Turner", 1.0),
        Product(4, "Flying Broom", 1.0)
    )
    
    // Store the selected product temporarily to record it after payment success
    var selectedProduct by remember { mutableStateOf<Product?>(null) }

    val paymentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // UPI apps return result in onActivityResult
        // Different apps handle RESULT_OK / RESULT_CANCELED differently.
        // We often parse the response string.
        
        // For simplicity in this test app, we'll assume if the user comes back, 
        // we check the data or just assume success if resultCode is valid, 
        // BUT strictly speaking, we should parse the "response" extra.
        
        val data = result.data
        val status = data?.getStringExtra("Status") // Some apps use this
        val response = data?.getStringExtra("response") // Standard UPI spec
        
        Log.d("PaymentScreen", "Result: ${result.resultCode}, Data: $data, Response: $response")

        // In a real production app, verify the transaction with your server.
        // For this test:
        if (result.resultCode == Activity.RESULT_OK || (response != null && response.contains("Status=SUCCESS", ignoreCase = true))) {
             val product = selectedProduct
             if (product != null) {
                repository.recordTransaction(
                    Transaction(
                        itemName = "${product.name} (via UPI)",
                        amount = product.price,
                        timestamp = Date(),
                        status = "SUCCESS"
                    )
                )
                Toast.makeText(context, "Payment Successful/Recorded for ${product.name}", Toast.LENGTH_LONG).show()
             }
        } else {
             Toast.makeText(context, "Payment potentially failed or cancelled. Check History.", Toast.LENGTH_LONG).show()
             // Optional: Record failed transaction
        }
    }

    fun requestPayment(product: Product) {
        selectedProduct = product
        
        val uri = android.net.Uri.parse(
            "upi://pay?pa=$upiId&pn=$payeeName&tn=${product.name}&am=${product.price}&cu=INR"
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
        
        // Verify there is an app to handle this
        if (intent.resolveActivity(activity.packageManager) != null) {
             // Create a chooser to let user select GPay, PhonePe, Paytm etc.
             val chooser = android.content.Intent.createChooser(intent, "Pay with")
             paymentLauncher.launch(chooser)
        } else {
            Toast.makeText(context, "No UPI app found on this device", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
             Row(
                 modifier = Modifier.fillMaxWidth().padding(16.dp),
                 horizontalArrangement = Arrangement.SpaceBetween,
                 verticalAlignment = Alignment.CenterVertically
             ) {
                 Text(text = "GPay Market", style = MaterialTheme.typography.headlineMedium)
                 Button(onClick = onNavigateToHistory) {
                     Text("History")
                 }
             }
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(paddingValues)
        ) {
            item {
                Text(
                    text = "Paying to: $upiId", 
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            items(products) { product ->
                ProductItem(product = product, isGPayEnabled = true) {
                    requestPayment(product)
                }
            }
        }
    }
}

@Composable
fun ProductItem(product: Product, isGPayEnabled: Boolean, onBuy: () -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = product.name, style = MaterialTheme.typography.titleMedium)
                Text(text = "₹${product.price}", style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onBuy, enabled = isGPayEnabled) {
                Text("Buy with GPay")
            }
        }
    }
}
