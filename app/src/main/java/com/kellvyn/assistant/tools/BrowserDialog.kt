package com.kellvyn.assistant.tools

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.SafeBrowsingResponse
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val BrowserNight = Color(0xFF080B12)
private val BrowserPanel = Color(0xFF121925)
private val BrowserNeon = Color(0xFF4DDBFF)
private val BrowserMuted = Color(0xFF9AA8BA)

/**
 * A real WebView browser. It permits web URLs only, blocks file/content access,
 * disables mixed content and destroys the WebView when the dialog closes.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var address by remember { mutableStateOf("https://www.google.com") }
    var pageUrl by remember { mutableStateOf("https://www.google.com") }
    var loadError by remember { mutableStateOf("") }
    var browser by remember { mutableStateOf<WebView?>(null) }

    fun navigate(raw: String) {
        val normalized = normalizeWebAddress(raw)
        if (normalized == null) {
            loadError = "Use um endereço HTTPS válido ou digite termos para pesquisar."
            return
        }
        loadError = ""
        address = normalized
        pageUrl = normalized
        browser?.loadUrl(normalized)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            color = BrowserNight,
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(BrowserNight).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("NAVEGADOR", color = BrowserNeon, fontSize = 14.sp)
                        Text("Páginas da web reais", color = BrowserMuted, fontSize = 11.sp)
                    }
                    TextButton(onClick = onDismiss) { Text("Fechar", color = Color.White) }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Endereço ou pesquisa") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigate(address) })
                    )
                    Button(
                        onClick = { navigate(address) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrowserNeon, contentColor = BrowserNight),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Ir") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        browser?.goBack()
                        loadError = ""
                    }, enabled = browser?.canGoBack() == true) { Text("← Voltar", color = Color.White) }
                    TextButton(onClick = {
                        browser?.reload()
                        loadError = ""
                    }) { Text("Atualizar", color = Color.White) }
                    TextButton(onClick = { navigate("https://www.google.com") }) {
                        Text("Início", color = Color.White)
                    }
                }

                if (loadError.isNotBlank()) {
                    Text(loadError, color = Color(0xFFFFC857), fontSize = 12.sp)
                }

                Text(pageUrl, color = BrowserMuted, fontSize = 10.sp, maxLines = 1)
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.setSupportMultipleWindows(false)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val scheme = request?.url?.scheme?.lowercase()
                                    return scheme != "https" && scheme != "http"
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (!url.isNullOrBlank() && (url.startsWith("https://") || url.startsWith("http://"))) {
                                        pageUrl = url
                                        address = url
                                        loadError = ""
                                    }
                                }
                            }
                            browser = this
                            loadUrl(pageUrl)
                        }
                    },
                    update = { view ->
                        if (browser !== view) browser = view
                    }
                )

                Text(
                    "O navegador abre sites. Não tem acesso automático a contas, senhas ou ao conteúdo privado de outros aplicativos.",
                    color = BrowserMuted,
                    fontSize = 10.sp
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            browser?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            browser = null
        }
    }
}

private fun normalizeWebAddress(raw: String): String? {
    val text = raw.trim()
    if (text.isBlank()) return null
    if (text.contains(' ') || (!text.contains("://") && !text.contains('.') )) {
        val encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.toString())
        return "https://www.google.com/search?q=$encoded"
    }
    val candidate = when {
        text.startsWith("https://", ignoreCase = true) -> text
        text.startsWith("http://", ignoreCase = true) -> "https://" + text.substringAfter("://")
        else -> "https://$text"
    }
    val parsed = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
    if (parsed.scheme != "https" || parsed.host.isNullOrBlank() || parsed.userInfo != null) return null
    return candidate
}
