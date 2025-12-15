package com.antigravity.gpaytest.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import com.antigravity.gpaytest.util.ImageUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.antigravity.gpaytest.R
import com.antigravity.gpaytest.data.PaymentRepository
import com.antigravity.gpaytest.data.Transaction

import java.util.Date
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

data class Product(val id: Int, val name: String, val price: Double)

enum class PaymentStep {
    SELECTION, QR_VIEW, INSTRUCTIONS, SUBMISSION
}

@Composable
fun PaymentScreen(
    repository: PaymentRepository,
    onNavigateToHistory: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    sharedImageUri: Uri? = null,
    isShareFlow: Boolean = false,
    onConsumeSharedUri: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(PaymentStep.SELECTION) }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }
    
    // Check for shared intent
    LaunchedEffect(sharedImageUri) {
        if (sharedImageUri != null) {
            screenshotUri = sharedImageUri
            currentStep = PaymentStep.SUBMISSION
            // Note: We do NOT consume here immediately if we need isShareFlow later.
            // But we should probably pass isShareFlow to SubmissionView.
        }
    }

    val products = listOf(
        Product(1, "Gnana Muham", 500.0),
        Product(2, "Gnana Viduthalai Muham", 1000.0),
        Product(3, "Dhyana Muham", 1500.0),
        Product(4, "Ayya Birthday", 2000.0)
    )

    // Persistence Logic
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("payment_prefs", Context.MODE_PRIVATE)
        val savedId = prefs.getInt("selected_product_id", -1)
        if (savedId != -1) {
            val savedProduct = products.find { it.id == savedId }
            if (savedProduct != null) {
                selectedProduct = savedProduct
            }
        }
    }

    Scaffold(
        topBar = {
             Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                 Text(text = "Program Registration", style = MaterialTheme.typography.headlineMedium)
                 Spacer(modifier = Modifier.height(8.dp))
                 Row(
                     modifier = Modifier.fillMaxWidth(),
                     horizontalArrangement = Arrangement.spacedBy(8.dp)
                 ) {
                     Button(
                         onClick = onNavigateToAdmin,
                         modifier = Modifier.weight(1f)
                     ) {
                         Text("Admin")
                     }
                     Button(
                         onClick = onNavigateToHistory,
                         modifier = Modifier.weight(1f)
                     ) {
                         Text("My Registrations")
                     }
                 }
             }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentStep) {
                PaymentStep.SELECTION -> {
                    ProductSelectionList(products) { product ->
                        selectedProduct = product
                        // Save to Prefs
                        val prefs = context.getSharedPreferences("payment_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putInt("selected_product_id", product.id).apply()
                        currentStep = PaymentStep.QR_VIEW
                    }
                }
                PaymentStep.QR_VIEW -> {
                    QrCodeView(
                        onImageSaved = { currentStep = PaymentStep.INSTRUCTIONS },
                        onBack = { currentStep = PaymentStep.SELECTION }
                    )
                }
                PaymentStep.INSTRUCTIONS -> {
                    InstructionsView(
                        amount = selectedProduct?.price ?: 0.0,
                        onOpenGPay = { openGPay(context) },
                        onBack = { currentStep = PaymentStep.QR_VIEW }
                    )
                }
                PaymentStep.SUBMISSION -> {
                    SubmissionView(
                        repository = repository,
                        isShareFlow = isShareFlow,
                        product = selectedProduct,
                        imageUri = screenshotUri,
                        onSubmit = { name, amount, ocrText, parsedAmount, base64Image ->
                            repository.recordTransaction(
                                Transaction(
                                    itemName = "$name",
                                    amount = amount,
                                    timestamp = Date(),
                                    status = "PENDING",
                                    ocrText = ocrText,
                                    parsedAmount = parsedAmount
                                ),
                                base64Image // Pass separate image payload
                            )
                            Toast.makeText(context, "Transaction Submitted! Pending Approval.", Toast.LENGTH_LONG).show()
                            // Clear Prefs on Success
                            val prefs = context.getSharedPreferences("payment_prefs", Context.MODE_PRIVATE)
                            prefs.edit().remove("selected_product_id").apply()
                            
                            // Reset
                            selectedProduct = null
                            screenshotUri = null
                            currentStep = PaymentStep.SELECTION
                            
                            onConsumeSharedUri() // Reset Shared State in MainActivity
                        },
                        onBack = { 
                            // If we came from Share Intent involved complexity, but essentially go back to list
                            currentStep = PaymentStep.SELECTION 
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun ProductSelectionList(products: List<Product>, onSelect: (Product) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(products) { product ->
            ProductItem(product = product, isGPayEnabled = true) {
                onSelect(product)
            }
        }
    }
}

@Composable
fun QrCodeView(onImageSaved: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Click Image to Pay", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        
        Image(
            painter = painterResource(id = R.drawable.qr_code),
            contentDescription = "QR Code",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable {
                    val uri = ImageUtils.saveDrawableToGallery(context, R.drawable.qr_code, "BagavathMission_QR")
                    if (uri != null) {
                        Toast.makeText(context, "Image Saved to Gallery", Toast.LENGTH_SHORT).show()
                        onImageSaved()
                    } else {
                        Toast.makeText(context, "Failed to Save Image", Toast.LENGTH_SHORT).show()
                    }
                },
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("(Tap the QR code to save it and proceed)", style = MaterialTheme.typography.bodySmall)
        
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
fun InstructionsView(amount: Double, onOpenGPay: () -> Unit, onBack: () -> Unit) {
    var isTamil by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            if (isTamil) "கட்டண வழிமுறைகள்" else "Payment Instructions",
            style = MaterialTheme.typography.headlineMedium
        )
        
        // Toggle Button (Moved below title)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = { isTamil = !isTamil }) {
                Text(if (isTamil) "English" else "தமிழ்")
            }
        }
        
        // Step 1
        InstructionStep(1, buildAnnotatedString {
            if (isTamil) {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Open GPay") }
                append(" பட்டனை அழுத்தவும்.")
            } else {
                append("Click the button below to ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("open GPay") }
                append(".")
            }
        })

        // Step 2
        InstructionStep(2, buildAnnotatedString {
             if (isTamil) {
                append("GPay உள்ளே சென்றதும், ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Scan any QR code") }
                append(" என்பதை கிளிக் செய்யவும்.")
            } else {
                append("Once inside GPay, click on '")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Scan any QR code") }
                append("'.")
            }
        })
        
        // Step 3
        InstructionStep(3, buildAnnotatedString {
             if (isTamil) {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Upload from Gallery") }
                append(" என்பதை கிளிக் செய்து 'BagavathMission_QR' படத்தை தேர்ந்தெடுக்கவும்.")
            } else {
                append("Click on '")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Upload from gallery") }
                append("' and select the 'BagavathMission_QR' image.")
            }
        })

        // Step 4
        InstructionStep(4, buildAnnotatedString {
             if (isTamil) {
                 withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("₹$amount") }
                append(" செலுத்தவும்.")
            } else {
                append("Pay the sum of ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("₹$amount") }
                append(".")
            }
        })

        // Step 5
        InstructionStep(5, buildAnnotatedString {
             if (isTamil) {
                append("பணம் செலுத்திய பிறகு ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Share Screenshot") }
                append(" கொடுத்து 'SBB Payment' ஆப்பை தேர்ந்தெடுக்கவும்.")
            } else {
                append("After payment, click ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Share Screenshot") }
                append(" and select 'SBB Payment' (this app).")
            }
        })
        
        // Step 6
        InstructionStep(6, buildAnnotatedString {
             if (isTamil) {
                append("வேறேதேனும் ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("UPI ஆப்") }
                append(" (PhonePe, Paytm) பயன்படுத்தினால், இதே வழிமுறைகளை அந்த ஆப்-ல் பின்பற்றவும்.")
            } else {
                append("If you are using any ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("other UPI App") }
                append(", please follow similar instructions for that app.")
            }
        })
        
        // Step 7
        InstructionStep(7, buildAnnotatedString {
             if (isTamil) {
                append("Share Screenshot கொடுத்த பிறகு நம்ம ஆப் தெரியவில்லை என்றால், கடைசியில் உள்ள ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("More") }
                append(" என்பதை கிளிக் செய்தால் தெரியும்.")
            } else {
                append("In case of ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("issues") }
                append(" seeing our app after clicking 'Share Screenshot', please select 'More' at the end to find the app.")
            }
        })
        
        Button(
            onClick = onOpenGPay,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
             Text(if (isTamil) "GPay ஐ திறக்கவும்" else "Open GPay")
        }
        
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text(if (isTamil) "பின்னால்" else "Back")
        }
    }
}

@Composable
fun InstructionStep(number: Int, text: AnnotatedString) {
    Row {
        Text("$number. ", style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SubmissionView(
    repository: PaymentRepository, 
    isShareFlow: Boolean,
    product: Product?, 
    imageUri: Uri?, 
    onSubmit: (String, Double, String, String?, String?) -> Unit, 
    onBack: () -> Unit
) {
    // Use remember with keys equal to product/imageUri to reset when a new item is selected
    // If product is null, we don't want to reset if user is typing
    var productName by remember(product) { mutableStateOf(product?.name ?: "") }
    var amountStr by remember(product) { mutableStateOf(product?.price?.toString() ?: "") }
    
    // OCR States
    var isScanning by remember { mutableStateOf(false) }
    var ocrResult by remember { mutableStateOf("") }
    var parsedAmount by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    val bitmap = if (imageUri != null) rememberBitmapFromUri(imageUri) else null
    val scope = rememberCoroutineScope() // For launching OCR

    // Auto-Scan Effect
    LaunchedEffect(imageUri) {
        if (imageUri != null) {
            isScanning = true
            kotlinx.coroutines.delay(500) // Small UI delay to show loading
            try {
                // Run OCR
                val result = com.antigravity.gpaytest.utils.OCRManager.processImage(context, imageUri)
                ocrResult = result.rawText
                
                // Auto-Populate Amount if found and not already set by product
                if (result.amount != null) {
                    amountStr = result.amount
                    parsedAmount = result.amount
                    Toast.makeText(context, "Amount Detected: ₹${result.amount}", Toast.LENGTH_SHORT).show()
                }
                
                if (result.transactionId != null) {
                    Toast.makeText(context, "Ref No Detected: ${result.transactionId}", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                ocrResult = "Error: ${e.message}"
            } finally {
                isScanning = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Complete Registration", style = MaterialTheme.typography.headlineMedium)
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = productName,
                    onValueChange = { productName = it },
                    label = { Text("Product Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount (₹)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
        
        Text("Attached Screenshot:", style = MaterialTheme.typography.labelLarge)
        
        if (bitmap != null) {
             Box(contentAlignment = Alignment.Center) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Screenshot",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentScale = ContentScale.Fit,
                    alpha = if (isScanning) 0.5f else 1f
                )
                if (isScanning) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
             }
        } else if (imageUri != null) {
             Text("Loading image...", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("No image attached.", color = MaterialTheme.colorScheme.error)
        }
        
        // Show Parsed Data (Debug/Verification)
        if (ocrResult.isNotEmpty()) {
             Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                 Column(modifier = Modifier.padding(8.dp)) {
                     Text("Scanned Data:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                     Text(
                         text = ocrResult.take(200) + "...", 
                         style = MaterialTheme.typography.bodySmall,
                         maxLines = 3
                     )
                 }
             }
        }

        Button(
            onClick = { 
                val finalAmount = amountStr.toDoubleOrNull() ?: 0.0
                
                var base64Image: String? = null
                if (imageUri != null) {
                    // Compress to Base64 (Hybrid Logic)
                    base64Image = ImageUtils.compressToBase64(context, imageUri)
                }

                // Direct Submit with Data + Optional Base64
                onSubmit(productName, finalAmount, ocrResult, parsedAmount, base64Image)
                
                // Auto-Close if Share Flow
                if (isShareFlow) {
                    (context as? android.app.Activity)?.finish()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = imageUri != null && !isScanning
        ) {
            Text(if (isScanning) "Scanning..." else "Register Transaction")
        }
        
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
             colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("Back")
        }
    }
}

@Composable
fun rememberBitmapFromUri(uri: Uri): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return bitmap
}

fun openGPay(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.nbu.paisa.user")
    if (intent != null) {
        context.startActivity(intent)
    } else {
        // Fallback or generic intent?
        Toast.makeText(context, "GPay not found, opening generic Google Play link...", Toast.LENGTH_SHORT).show()
         try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.nbu.paisa.user")))
        } catch (e: android.content.ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.nbu.paisa.user")))
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
                Text("Pay")
            }
        }
    }
}
