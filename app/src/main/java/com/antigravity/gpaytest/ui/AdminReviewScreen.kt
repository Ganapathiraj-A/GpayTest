package com.antigravity.gpaytest.ui

import android.graphics.BitmapFactory
import android.widget.Toast

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.antigravity.gpaytest.data.PaymentRepository
import com.antigravity.gpaytest.data.Transaction
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AdminReviewScreen(
    repository: PaymentRepository,
    onNavigateBack: () -> Unit
) {
    val transactions by repository.getTransactions().collectAsState(initial = emptyList())
    
    // Filter State
    var selectedProductFilter by remember { mutableStateOf("All") }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    
    // Dialog State
    var showCommentDialog by remember { mutableStateOf(false) }
    var showViewCommentDialog by remember { mutableStateOf(false) }
    var activeTransaction by remember { mutableStateOf<Transaction?>(null) }
    var targetStatus by remember { mutableStateOf("") }
    var commentText by remember { mutableStateOf("") }
    var viewCommentText by remember { mutableStateOf("") }

    // Tab State: 0=Pending, 1=Approved, 2=Hold, 3=BNK Verified
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabTitles = listOf("Pending", "Approved", "Hold", "BNK Verified")
    
    // 1. Filter Transactions by Product
    val filteredTransactions = if (selectedProductFilter == "All") {
        transactions
    } else {
        transactions.filter { it.itemName == selectedProductFilter }
    }
    
    // 2. Split into Categories
    val pendingTransactions = filteredTransactions.filter { it.status == "PENDING" || (it.status != "REGISTERED" && it.status != "HOLD" && it.status != "BNK_VERIFIED") }
    val approvedTransactions = filteredTransactions.filter { it.status == "REGISTERED" }
    val holdTransactions = filteredTransactions.filter { it.status == "HOLD" }
    val bnkVerifiedTransactions = filteredTransactions.filter { it.status == "BNK_VERIFIED" }

    // Counts for Badges
    val counts = listOf(
        pendingTransactions.size,
        approvedTransactions.size,
        holdTransactions.size,
        bnkVerifiedTransactions.size
    )

    // Distinct Products for Dropdown
    val distinctProducts = listOf("All") + transactions.map { it.itemName }.distinct().sorted()
    
    val context = LocalContext.current

    // Helper to handle status updates
    fun handleStatusUpdate(transaction: Transaction, newStatus: String) {
        val currentStatus = transaction.status
        // Exceptions where NO comment is needed
        if ((currentStatus == "PENDING" && newStatus == "REGISTERED") || 
            (currentStatus == "REGISTERED" && newStatus == "BNK_VERIFIED")) {
             repository.updateTransactionStatus(transaction.id, newStatus, null)
             Toast.makeText(context, "Status Updated", Toast.LENGTH_SHORT).show()
        } else {
            // All other flows require comment dialog
            activeTransaction = transaction
            targetStatus = newStatus
            commentText = "" // Reset
            showCommentDialog = true
        }
    }

    if (showCommentDialog && activeTransaction != null) {
        AlertDialog(
            onDismissRequest = { showCommentDialog = false },
            title = { Text("Add Comment (Optional)") },
            text = {
                Column {
                    Text("Moving to: $targetStatus")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        label = { Text("Reason / Comments") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val timestamp = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(java.util.Date())
                        val finalComment = if (commentText.isNotBlank()) "$timestamp - $commentText" else ""
                        
                        repository.updateTransactionStatus(activeTransaction!!.id, targetStatus, finalComment)
                        Toast.makeText(context, "Status Updated", Toast.LENGTH_SHORT).show()
                        showCommentDialog = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                Button(onClick = { showCommentDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showViewCommentDialog) {
        AlertDialog(
            onDismissRequest = { showViewCommentDialog = false },
            title = { Text("Transaction Comments") },
            text = { Text(viewCommentText.ifEmpty { "No comments recorded." }) },
            confirmButton = {
                Button(onClick = { showViewCommentDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Image Dialog State
    var showImageDialog by remember { mutableStateOf(false) }
    var currentBase64Image by remember { mutableStateOf("") }

    if (showImageDialog && currentBase64Image.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showImageDialog = false },
            title = { Text("Original Screenshot") },
            text = {
                val bitmap = remember(currentBase64Image) { 
                    com.antigravity.gpaytest.util.ImageUtils.decodeBase64ToBitmap(currentBase64Image) 
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Original Receipt"
                    )
                } else {
                    Text("Error decoding image.")
                }
            },
            confirmButton = {
                Button(onClick = { showImageDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Delete All Dialog
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Verified?") },
            text = { Text("Are you sure you want to delete ALL ${bnkVerifiedTransactions.size} verified transactions? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        repository.deleteAllVerifiedTransactions(
                            onSuccess = { count ->
                                Toast.makeText(context, "Deleted $count transactions.", Toast.LENGTH_SHORT).show()
                                showDeleteAllDialog = false
                            },
                            onError = {
                                Toast.makeText(context, "Delete Failed", Toast.LENGTH_SHORT).show()
                                showDeleteAllDialog = false
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
             Column {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Admin Review",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Button(onClick = onNavigateBack) {
                        Text("Close")
                    }
                }
                
                // Filter & Total Count Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Dropdown
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                        OutlinedButton(onClick = { isDropdownExpanded = true }) {
                            Text("Program: $selectedProductFilter")
                        }
                        DropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false }
                        ) {
                            distinctProducts.forEach { product ->
                                DropdownMenuItem(
                                    text = { Text(product) },
                                    onClick = {
                                        selectedProductFilter = product
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // Total Count
                    Text(
                        text = "Total: ${filteredTransactions.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }

                // Tabs with Badges
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { 
                                val displayText = if (counts[index] > 0) "$title (${counts[index]})" else title
                                Text(displayText)
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
             // Show Delete All button only in BNK Verified tab (Index 3) and if list is not empty
             if (selectedTabIndex == 3 && bnkVerifiedTransactions.isNotEmpty()) {
                 Button(
                     onClick = { showDeleteAllDialog = true },
                     modifier = Modifier
                         .fillMaxWidth()
                         .padding(16.dp),
                     colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                 ) {
                     Text("Delete All Verified (${bnkVerifiedTransactions.size})")
                 }
             }
        }
    ) { paddingValues ->
        val currentList = when (selectedTabIndex) {
            0 -> pendingTransactions
            1 -> approvedTransactions
            2 -> holdTransactions
            else -> bnkVerifiedTransactions
        }
        
        if (currentList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues), 
                contentAlignment = Alignment.Center
            ) {
                Text("No ${tabTitles[selectedTabIndex]} transactions.")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(paddingValues)
            ) {
                items(currentList) { transaction ->
                    AdminReviewItem(
                        transaction = transaction,
                        currentTab = selectedTabIndex,
                        onUpdateStatus = { newStatus ->
                            handleStatusUpdate(transaction, newStatus)
                        },
                        onDelete = {
                            // Treating Delete as a direct action for now, but could be wrapped if needed
                            repository.deleteTransaction(transaction.id)
                            Toast.makeText(context, "Transaction Deleted", Toast.LENGTH_SHORT).show()
                        },
                        onViewComments = {
                            viewCommentText = transaction.comments
                            showViewCommentDialog = true
                        },
                        onViewImage = {
                            // On-Demand Fetch
                            repository.getTransactionImage(transaction.id, 
                                onSuccess = { base64 ->
                                    activeTransaction = transaction // Reuse capable of holding temp state or use new state
                                    viewCommentText = base64 // Hack: Reusing viewCommentText or create new state? Better create new state.
                                    // Actually, let's trigger a specific Image Dialog state
                                    // See below implementation for showImageDialog
                                    showImageDialog = true
                                    currentBase64Image = base64
                                },
                                onError = {
                                    Toast.makeText(context, "Failed to load image: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AdminReviewItem(
    transaction: Transaction, 
    currentTab: Int,
    onUpdateStatus: (String) -> Unit,
    onDelete: () -> Unit,
    onViewComments: () -> Unit,
    onViewImage: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Item: ${transaction.itemName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Amount: ₹${transaction.amount}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                // View Comments Link
                if (transaction.comments.isNotEmpty()) {
                    TextButton(onClick = onViewComments) {
                         Text("View Comments")
                    }
                }
            }

            Text(
                text = "Date: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(transaction.timestamp)}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (transaction.ocrText.isNotEmpty()) {
                Text("Proof of Payment (OCR Scan):", style = MaterialTheme.typography.labelLarge)
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        Text(
                            text = transaction.ocrText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
                
                if (transaction.parsedAmount != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Detected Amount: ₹${transaction.parsedAmount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (transaction.parsedAmount == transaction.amount.toString()) androidx.compose.ui.graphics.Color.Green else MaterialTheme.colorScheme.primary
                    )
                }
                
                // NEW: View Original Image (On-Demand)
                if (transaction.hasImage) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onViewImage,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("View Original Screenshot")
                    }
                }

            } else {
                Text("No scan data attached.", color = MaterialTheme.colorScheme.error)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val isValid = transaction.id.isNotBlank()
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Pending Tab (0)
                if (currentTab == 0) {
                    Button(
                        onClick = { onUpdateStatus("REGISTERED") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50)),
                        enabled = isValid
                    ) {
                        Text("Approve")
                    }
                    Button(
                        onClick = { onUpdateStatus("HOLD") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFFF9800)),
                        enabled = isValid
                    ) {
                        Text("Hold")
                    }
                }
                // Approved Tab (1)
                else if (currentTab == 1) {
                    // New: BNK Approve Button
                    Button(
                        onClick = { onUpdateStatus("BNK_VERIFIED") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF673AB7)), // Deep Purple
                        enabled = isValid
                    ) {
                        Text("BNK Approve")
                    }
                    Button(
                        onClick = { onUpdateStatus("PENDING") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFE91E63)),
                        enabled = isValid
                    ) {
                        Text("To Pending")
                    }
                     Button(
                        onClick = { onUpdateStatus("HOLD") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFFF9800)),
                        enabled = isValid
                    ) {
                        Text("To Hold")
                    }
                }
                // Hold Tab (2)
                else if (currentTab == 2) {
                    Button(
                        onClick = { onUpdateStatus("REGISTERED") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50)),
                        enabled = isValid
                    ) {
                        Text("Approve")
                    }
                    Button(
                        onClick = { onUpdateStatus("PENDING") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF2196F3)),
                        enabled = isValid
                    ) {
                        Text("Pending")
                    }
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = isValid
                    ) {
                        Text("Delete")
                    }
                }
                // BNK Verified Tab (3)
                else {
                    Button(
                        onClick = { onUpdateStatus("REGISTERED") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50)),
                        enabled = isValid
                    ) {
                        Text("Revert to Approved")
                    }
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = isValid
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}
