package com.example.expensetracker

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        private val PAYMENT_PKGS = setOf(
            "com.google.android.apps.nbu.paisa.user",   // GPay India
            "com.google.android.apps.walletnfcrel",     // Google Wallet
            "com.phonepe.app",                          // PhonePe
            "net.one97.paytm",                          // Paytm
            "in.org.npci.upiapp",                       // BHIM
            "in.amazon.mShop.android.shopping",         // Amazon Pay
            "com.csam.icici.bank.imobile",              // ICICI
            "com.sbi.lotusintouch",                     // SBI YONO
            "com.snapwork.hdfc",                        // HDFC
            "com.axis.mobile",                          // Axis
            "com.msf.kbank.mobile"                      // Kotak
        )
        private val KEYWORDS = listOf(
            "debited","credited","paid","received","sent","₹","rs.","rs ","inr","txn"
        )
        private val AMOUNT_RE = Regex(
            """(?:₹|rs\.?|inr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        @Volatile private var lastKey  = ""
        @Volatile private var lastTime = 0L
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in PAYMENT_PKGS) return

        val extras  = sbn.notification.extras
        val title   = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()  ?: ""
        val text    = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()   ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val full    = "$title $text $bigText".lowercase()

        if (KEYWORDS.none { full.contains(it) }) return

        // Basic per-notification dedup
        val key = "${sbn.packageName}|${sbn.id}|$title|$text"
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastTime < 3_000L) return
        lastKey  = key
        lastTime = now

        val amount = parseAmount(full)
        val dedup  = TransactionDeduplicator(this)
        val hash   = dedup.makeHash(amount, sbn.packageName, now / 1000L)
        if (dedup.isSeen(hash)) return
        dedup.markSeen(hash)

        val svc = Intent(this, OverlayService::class.java).apply {
            putExtra("amount",  amount)
            putExtra("source",  sbn.packageName)
            putExtra("snippet", text.ifBlank { title })
            putExtra("channel", "notification")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(svc)
        else
            startService(svc)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    private fun parseAmount(text: String): Double {
        val m = AMOUNT_RE.find(text) ?: return 0.0
        return m.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
    }
}
