package com.antigravity.gpaytest.data

import android.app.Activity
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object GPayUtil {

    /**
     * Create a PaymentsClient instance.
     *
     * @param activity A client needs an activity context to be initialized
     */
    fun createPaymentsClient(activity: Activity): PaymentsClient {
        val walletOptions = Wallet.WalletOptions.Builder()
            .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
            .build()

        return Wallet.getPaymentsClient(activity, walletOptions)
    }

    private val baseRequest: JSONObject
        get() = JSONObject().put("apiVersion", 2).put("apiVersionMinor", 0)

    private val gatewayTokenizationSpecification: JSONObject
        get() {
            return JSONObject().apply {
                put("type", "PAYMENT_GATEWAY")
                put("parameters", JSONObject().apply {
                    put("gateway", "example")
                    put("gatewayMerchantId", "exampleGatewayMerchantId")
                })
            }
        }

    private val allowedCardNetworks: JSONArray
        get() = JSONArray().apply {
            put("AMEX")
            put("DISCOVER")
            put("INTERAC")
            put("JCB")
            put("MASTERCARD")
            put("VISA")
        }

    private val allowedCardAuthMethods: JSONArray
        get() = JSONArray().apply {
            put("PAN_ONLY")
            put("CRYPTOGRAM_3DS")
        }

    private fun baseCardPaymentMethod(): JSONObject {
        return JSONObject().apply {
            put("type", "CARD")
            put("parameters", JSONObject().apply {
                put("allowedAuthMethods", allowedCardAuthMethods)
                put("allowedCardNetworks", allowedCardNetworks)
                // Billing address required for some transactions
                // put("billingAddressRequired", true)
                // put("billingAddressParameters", JSONObject().put("format", "FULL"))
            })
        }
    }

    private val cardPaymentMethod: JSONObject
        get() = baseCardPaymentMethod().apply {
            put("tokenizationSpecification", gatewayTokenizationSpecification)
        }

    fun getIsReadyToPayRequest(): JSONObject? {
        return try {
            baseRequest.apply {
                put("allowedPaymentMethods", JSONArray().put(baseCardPaymentMethod()))
            }
        } catch (e: JSONException) {
            null
        }
    }

    fun getPaymentDataRequest(price: String): JSONObject? {
        return try {
            baseRequest.apply {
                put("allowedPaymentMethods", JSONArray().put(cardPaymentMethod))
                put("transactionInfo", JSONObject().apply {
                    put("totalPriceStatus", "FINAL")
                    put("totalPrice", price)
                    put("totalPriceLabel", "Total")
                    put("countryCode", "IN")
                    put("currencyCode", "INR")
                    put("checkoutOption", "COMPLETE_IMMEDIATE_PURCHASE")
                })
                put("merchantInfo", JSONObject().apply {
                    put("merchantName", "Example Merchant")
                })
            }
        } catch (e: JSONException) {
            null
        }
    }
}
