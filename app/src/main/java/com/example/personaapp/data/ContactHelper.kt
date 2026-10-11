package com.example.personaapp.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import java.util.regex.Pattern

object ContactHelper {

    private val TRANSACTIONAL_SENDER_REGEX = Pattern.compile("^[A-Za-z]{2}-[A-Za-z0-9]{4,8}$", Pattern.CASE_INSENSITIVE)

    /**
     * Resolves a phone number or sender address to a saved contact name if available.
     * Checks cachedName first, then queries ContactsContract.PhoneLookup if permission is granted.
     */
    fun resolveContactName(context: Context, rawNumber: String, cachedName: String? = null): String? {
        if (!cachedName.isNullOrBlank()) {
            return cachedName.trim()
        }
        if (rawNumber.isBlank() || isTransactionalOrBot(rawNumber)) {
            return null
        }
        if (!PermissionChecker.checkContactsAccess(context)) {
            return null
        }

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(rawNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val name = it.getString(0)
                    if (!name.isNullOrBlank()) name.trim() else null
                } else null
            }
        } catch (e: Exception) {
            AppLogger.e("CONTACT_HELPER", "Error resolving contact for $rawNumber: ${e.message}")
            null
        }
    }

    /**
     * Detects if an SMS sender or message body is automated, transactional, bank OTP, or bot spam.
     */
    fun isTransactionalOrBot(sender: String, body: String = ""): Boolean {
        val trimmedSender = sender.trim()
        // Alphanumeric sender ID like VM-HDFCBK, AX-SWIGGY, VK-GOOGLE
        if (TRANSACTIONAL_SENDER_REGEX.matcher(trimmedSender).matches()) {
            return true
        }
        // Short alphanumeric code or shortcode (< 6 digits and non-+ formatted)
        if (!trimmedSender.startsWith("+") && trimmedSender.length <= 6 && trimmedSender.all { it.isDigit() }) {
            return true
        }
        if (trimmedSender.any { it.isLetter() } && !trimmedSender.contains(" ")) {
            // E.g., "HDFCBK", "Amazon", "UBER"
            return true
        }

        // Check common transactional keywords
        val lowerBody = body.lowercase()
        val botKeywords = listOf(
            "otp", "verification code", "one time password", "debited by", "credited with",
            "account balance", "promo code", "click to verify", "order confirmed", "shipped"
        )
        if (botKeywords.any { lowerBody.contains(it) }) {
            return true
        }

        return false
    }

    /**
     * Cleans side character names extracted from notifications or prompts.
     * E.g. "Rahul (3 messages)" -> "Rahul", "[WhatsApp] Sarah" -> "Sarah"
     */
    fun cleanContactName(rawName: String): String {
        var clean = rawName.trim()
        // Strip prefixes like [WhatsApp], App:, etc.
        clean = clean.replace(Regex("^\\[.*?\\]\\s*"), "")
        // Strip trailing message count indicators like (3 messages), (2)
        clean = clean.replace(Regex("\\s*\\(\\d+\\s*(?:new\\s*)?messages?\\)", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("\\s*\\(\\d+\\)"), "")
        // Strip trailing colons or dashes
        clean = clean.trim(' ', ':', '-', '"', '\'')
        return clean
    }
}
