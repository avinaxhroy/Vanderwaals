package me.avinas.vanderwaals.data

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens an email intent to contact the developer.
 */
fun SendContactIntent(context: Context) {
    val authorEmail = "hi@avinas.me"
    val cc = ""
    val subject = "[Support] Vanderwaals"
    val bodyText = "This is regarding the Vanderwaals app for Android:\n"
    val mailto = "mailto:" + Uri.encode(authorEmail) +
            "?cc=" + Uri.encode(cc) +
            "&subject=" + Uri.encode(subject) +
            "&body=" + Uri.encode(bodyText)

    val emailIntent = Intent(Intent.ACTION_SENDTO)
    emailIntent.data = Uri.parse(mailto)
    context.startActivity(emailIntent)
}