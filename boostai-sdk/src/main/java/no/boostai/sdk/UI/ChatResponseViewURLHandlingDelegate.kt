package no.boostai.sdk.UI

import android.net.Uri

interface ChatResponseViewURLHandlingDelegate {
    fun shouldOpenUrl(url: Uri) : Boolean
}