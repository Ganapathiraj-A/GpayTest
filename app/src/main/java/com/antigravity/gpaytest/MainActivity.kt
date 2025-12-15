package com.antigravity.gpaytest

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.antigravity.gpaytest.data.PaymentRepository
import com.antigravity.gpaytest.data.Transaction
import com.antigravity.gpaytest.ui.PaymentScreen
import com.antigravity.gpaytest.ui.TransactionHistoryScreen
import com.antigravity.gpaytest.ui.theme.GpayTestTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import java.util.Date

class MainActivity : ComponentActivity() {

    private lateinit var repository: PaymentRepository

    private val sharedImageUri = mutableStateOf<android.net.Uri?>(null)
    private val isShareFlow = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // DEBUG: Confirm new instance creation
        Toast.makeText(this, "onCreate: New Instance Created", Toast.LENGTH_SHORT).show()
        
        enableEdgeToEdge()

        handleIntent(intent)

        // 1. Initialize Auth
        val auth = FirebaseAuth.getInstance()
        
        // 2. Sign in Anonymously (Required for most Storage Rules)
        auth.signInAnonymously()
            .addOnSuccessListener { 
                Log.d("MainActivity", "SignedInAnonymously: ${it.user?.uid}") 
                Toast.makeText(this, "Auth Success", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "SignInAnonymously Failed", e)
                Toast.makeText(this, "Auth Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }

        // 3. Initialize Firestore
        val firestore = FirebaseFirestore.getInstance()
        
        // 4. Initialize Storage (No longer needed for OCR)
        // val storage = FirebaseStorage.getInstance()
        
        repository = PaymentRepository(firestore)

        setContent {
            GpayTestTheme {
                AppNavigation(repository, sharedImageUri.value, isShareFlow.value) {
                    sharedImageUri.value = null // Consume the URI
                    isShareFlow.value = false // Reset flag
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // DEBUG: Confirm reuse of existing instance
        Toast.makeText(this, "onNewIntent: Resuming Instance", Toast.LENGTH_SHORT).show()
        setIntent(intent) // Critical: Update the intent associated with this activity
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            (intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))?.let { uri ->
                sharedImageUri.value = uri
                Toast.makeText(this, "Image Received from Share", Toast.LENGTH_SHORT).show()
                // Flag that this is a share flow for auto-close logic
                isShareFlow.value = true
            }
        }
    }


}

@Composable
fun AppNavigation(
    repository: PaymentRepository,
    sharedImageUri: android.net.Uri?,
    isShareFlow: Boolean, // Added flag
    onConsumeSharedUri: () -> Unit
) {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "payment") {
        composable("payment") {
            PaymentScreen(
                repository = repository,
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToAdmin = { navController.navigate("admin_review") },
                sharedImageUri = sharedImageUri,
                isShareFlow = isShareFlow,
                onConsumeSharedUri = onConsumeSharedUri
            )
        }
        composable("history") {
            TransactionHistoryScreen(
                repository = repository
            )
        }
        composable("admin_review") {
            com.antigravity.gpaytest.ui.AdminReviewScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
