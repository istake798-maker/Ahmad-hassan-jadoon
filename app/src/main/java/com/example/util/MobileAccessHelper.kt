package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object MobileAccessHelper {

    fun openWhatsApp(context: Context, message: String = "", phoneOrHandle: String = "") {
        val cleanDigits = phoneOrHandle.filter { it.isDigit() }
        val encodedText = Uri.encode(message)
        val uri = if (cleanDigits.length >= 7) {
            Uri.parse("https://wa.me/$cleanDigits?text=$encodedText")
        } else if (message.isNotBlank()) {
            Uri.parse("https://api.whatsapp.com/send?text=$encodedText")
        } else {
            null
        }

        if (uri != null) {
            val waIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(waIntent)
                return
            } catch (_: Exception) {
                // Fall through to generic share / WhatsApp launch
            }
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
        if (launchIntent != null && message.isBlank()) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else if (message.isNotBlank()) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(sendIntent)
            } catch (_: Exception) {
                shareText(context, message, "Send via WhatsApp / Messaging")
            }
        } else {
            Toast.makeText(context, "WhatsApp installed nahi mila, share sheet khol raha hoon.", Toast.LENGTH_SHORT).show()
            shareText(context, "Assalam-o-Alaikum!", "Open Messaging")
        }
    }

    fun openGmail(
        context: Context,
        recipientEmail: String = "",
        subject: String = "Freelance Collaboration Proposal",
        body: String = ""
    ) {
        val mailUri = Uri.parse(
            "mailto:${Uri.encode(recipientEmail)}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
        )
        val emailIntent = Intent(Intent.ACTION_SENDTO, mailUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(emailIntent, "Open Gmail / Email"))
        } catch (_: Exception) {
            Toast.makeText(context, "Email app open nahi ho saki.", Toast.LENGTH_SHORT).show()
        }
    }

    fun openDialer(context: Context, phoneNumber: String = "") {
        val cleanPhone = phoneNumber.filter { it.isDigit() || it == '+' }
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(dialIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "Phone dialer available nahi hai.", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    fun shareText(context: Context, text: String, title: String = "Share from Jarvis Ultra") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
