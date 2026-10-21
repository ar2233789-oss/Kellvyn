package com.kellvyn.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Keeps configured Telegram and Discord bots connected while the user has explicitly enabled them.
 * All bot replies use the same local Kellvyn model. Tokens are read from Android Keystore-backed storage.
 */
class BotRuntimeService : Service() {
    companion object {
        private const val TAG = "KellvynBotRuntime"
        private const val PREFS = "kellvyn_native"
        private const val HISTORY_PREFS = "kellvyn_bot_history"
        private const val CHANNEL_ID = "kellvyn_bot_runtime"
        private const val NOTIFICATION_ID = 8417
        private const val ACTION_REFRESH = "com.kellvyn.assistant.BOTS_REFRESH"
        private const val ACTION_STOP = "com.kellvyn.assistant.BOTS_STOP"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 32_000
        private const val MAX_RESPONSE_CHARS = 3500
        private const val MAX_CHAT_TURNS = 10

        @JvmStatic
        fun refresh(context: Context) {
            val intent = Intent(context, BotRuntimeService::class.java).setAction(ACTION_REFRESH)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (error: Exception) {
                Log.e(TAG, "Não foi possível atualizar o serviço de bots", error)
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            val intent = Intent(context, BotRuntimeService::class.java).setAction(ACTION_STOP)
            try { context.startService(intent) } catch (_: Exception) {}
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val replyMutex = Mutex()
    private lateinit var brain: LocalBrain
    private var telegramJob: Job? = null
    private var discordJob: Job? = null
    private var telegramTokenInUse: String? = null
    private var discordTokenInUse: String? = null
    @Volatile private var discordSocket: WebSocketClient? = null
    @Volatile private var discordBotUserId: String = ""
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        brain = LocalBrain(applicationContext)
        ensureNotificationChannel()
        enterForeground("Verificando bots habilitados…")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("bot_enabled_telegram", false)
                .putBoolean("bot_enabled_discord", false)
                .apply()
            stopRuntimeJobs()
            writeStatus("telegram", "Desativado")
            writeStatus("discord", "Desativado")
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        synchronizeRuntimes()
        return START_STICKY
    }

    private fun synchronizeRuntimes() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val telegramEnabled = prefs.getBoolean("bot_enabled_telegram", false)
        val discordEnabled = prefs.getBoolean("bot_enabled_discord", false)
        val telegramToken = if (telegramEnabled) BotTokenStore.read(this, "telegram") else null
        val discordToken = if (discordEnabled) BotTokenStore.read(this, "discord") else null

        if (telegramEnabled && telegramToken.isNullOrBlank()) {
            writeStatus("telegram", "Token não configurado")
        }
        if (discordEnabled && discordToken.isNullOrBlank()) {
            writeStatus("discord", "Token não configurado")
        }

        if (telegramToken.isNullOrBlank()) {
            telegramJob?.cancel()
            telegramJob = null
            telegramTokenInUse = null
            if (!telegramEnabled) writeStatus("telegram", "Desativado")
        } else if (telegramJob?.isActive != true || telegramTokenInUse != telegramToken) {
            telegramJob?.cancel()
            telegramTokenInUse = telegramToken
            telegramJob = serviceScope.launch { runTelegramLoop(telegramToken) }
        }

        if (discordToken.isNullOrBlank()) {
            discordJob?.cancel()
            discordSocket?.close()
            discordSocket = null
            discordJob = null
            discordTokenInUse = null
            if (!discordEnabled) writeStatus("discord", "Desativado")
        } else if (discordJob?.isActive != true || discordTokenInUse != discordToken) {
            discordJob?.cancel()
            discordSocket?.close()
            discordTokenInUse = discordToken
            discordJob = serviceScope.launch { runDiscordLoop(discordToken) }
        }

        val anyConfiguredEnabled =
            (!telegramToken.isNullOrBlank()) || (!discordToken.isNullOrBlank())
        if (anyConfiguredEnabled) {
            enterForeground("Mantendo os bots habilitados conectados")
        } else {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun stopRuntimeJobs() {
        telegramJob?.cancel()
        telegramJob = null
        telegramTokenInUse = null
        discordJob?.cancel()
        discordJob = null
        discordTokenInUse = null
        discordSocket?.close()
        discordSocket = null
    }

    private fun enabled(platform: String): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("bot_enabled_" + platform, false)

    private fun writeStatus(platform: String, value: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("bot_status_" + platform, value.take(160)).apply()
        val active = listOf("telegram", "discord").any { enabled(it) && !BotTokenStore.read(this, it).isNullOrBlank() }
        if (active && foregroundStarted) enterForeground("Bots: " + value.take(70))
    }

    private fun enterForeground(text: String) {
        ensureNotificationChannel()
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Kellvyn IA")
            .setContentText(text.take(110))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, notification)
            foregroundStarted = true
        } else {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Bots ativos da Kellvyn", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Informa quando os bots configurados estão sendo mantidos conectados."
                }
            )
        }
    }

    private data class TelegramUpdate(val id: Long, val chatId: String, val senderIsBot: Boolean, val text: String)

    private suspend fun runTelegramLoop(token: String) {
        writeStatus("telegram", "Conectando…")
        var offset = getSharedPreferences(PREFS, MODE_PRIVATE).getLong("telegram_offset", 0L)
        while (currentCoroutineContext().isActive && enabled("telegram")) {
            try {
                val updates = withContext(Dispatchers.IO) { pollTelegram(token, offset) }
                writeStatus("telegram", "Conectado")
                for (update in updates) {
                    offset = maxOf(offset, update.id + 1L)
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong("telegram_offset", offset).apply()
                    if (update.senderIsBot || update.text.isBlank()) continue
                    try {
                        val answer = buildLocalBotReply("telegram", update.chatId, update.text)
                        withContext(Dispatchers.IO) { sendTelegramMessage(token, update.chatId, answer) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.e(TAG, "Falha ao responder mensagem do Telegram", error)
                        withContext(Dispatchers.IO) {
                            sendTelegramMessage(token, update.chatId, userVisibleBotError(error))
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Do not log the exception object: some HTTP exceptions include the Telegram bot token URL.
                Log.w(TAG, "Conexão do Telegram falhou; verificando token e rede")
                writeStatus("telegram", "Erro de conexão/token; tentando novamente")
                delay(5000L)
            }
        }
        writeStatus("telegram", "Desativado")
    }

    private fun pollTelegram(token: String, offset: Long): List<TelegramUpdate> {
        val url = "https://api.telegram.org/bot" + token +
            "/getUpdates?timeout=20&offset=" + offset + "&allowed_updates=%5B%22message%22%5D"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val body = readLimitedResponse(connection, code)
            if (code !in 200..299) throw IOException("Telegram retornou HTTP " + code)
            val root = JSONObject(body)
            if (!root.optBoolean("ok", false)) {
                throw IOException("Telegram recusou a solicitação; confira o token salvo.")
            }
            val arr = root.optJSONArray("result") ?: JSONArray()
            val updates = ArrayList<TelegramUpdate>()
            for (i in 0 until arr.length()) {
                val update = arr.optJSONObject(i) ?: continue
                val message = update.optJSONObject("message") ?: continue
                val chat = message.optJSONObject("chat") ?: continue
                val from = message.optJSONObject("from")
                updates.add(
                    TelegramUpdate(
                        update.optLong("update_id", 0L),
                        chat.optLong("id", 0L).toString(),
                        from?.optBoolean("is_bot", false) ?: false,
                        message.optString("text", "")
                    )
                )
            }
            return updates
        } finally {
            connection.disconnect()
        }
    }

    private fun sendTelegramMessage(token: String, chatId: String, text: String) {
        val chunks = splitForPlatform(text, 3500)
        for (chunk in chunks) {
            val payload = JSONObject().put("chat_id", chatId).put("text", chunk)
            val connection = (URL("https://api.telegram.org/bot" + token + "/sendMessage").openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = CONNECT_TIMEOUT_MS
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            try {
                connection.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
                val code = connection.responseCode
                val body = readLimitedResponse(connection, code)
                if (code !in 200..299 || !JSONObject(body).optBoolean("ok", false)) {
                    throw IOException("Telegram não aceitou a resposta (HTTP " + code + ").")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private suspend fun runDiscordLoop(token: String) {
        writeStatus("discord", "Conectando…")
        while (currentCoroutineContext().isActive && enabled("discord")) {
            var client: WebSocketClient? = null
            try {
                val gateway = withContext(Dispatchers.IO) { fetchDiscordGateway(token) }
                val closed = CompletableDeferred<Unit>()
                val sequence = AtomicLong(-1L)
                val heartbeatJob = AtomicReference<Job?>(null)
                val uri = URI(gateway.trimEnd('/') + "/?v=10&encoding=json")
                val socketHolder = AtomicReference<WebSocketClient?>(null)
                val discordClient = object : WebSocketClient(uri) {
                    override fun onOpen(handshake: ServerHandshake?) {
                        socketHolder.set(this)
                        Log.i(TAG, "Discord Gateway connected")
                    }

                    override fun onMessage(message: String) {
                        try {
                            val packet = JSONObject(message)
                            if (packet.has("s") && !packet.isNull("s")) sequence.set(packet.optLong("s", -1L))
                            when (packet.optInt("op", -1)) {
                                10 -> {
                                    val interval = packet.optJSONObject("d")?.optLong("heartbeat_interval", 41250L) ?: 41250L
                                    val identify = JSONObject().put("op", 2).put(
                                        "d",
                                        JSONObject()
                                            .put("token", token)
                                            .put("intents", 37377)
                                            .put("properties", JSONObject().put("os", "android").put("browser", "Kellvyn IA").put("device", "android"))
                                    )
                                    send(identify.toString())
                                    heartbeatJob.set(serviceScope.launch {
                                        while (isActive && socketHolder.get()?.isOpen == true) {
                                            delay(interval.coerceAtLeast(10000L))
                                            val seq = sequence.get()
                                            val heartbeat = JSONObject().put("op", 1)
                                            heartbeat.put("d", if (seq >= 0L) seq else JSONObject.NULL)
                                            try { socketHolder.get()?.send(heartbeat.toString()) } catch (_: Exception) { break }
                                        }
                                    })
                                }
                                7, 9 -> close()
                                0 -> {
                                    val eventName = packet.optString("t", "")
                                    val data = packet.optJSONObject("d")
                                    if (eventName == "READY" && data != null) {
                                        discordBotUserId = data.optJSONObject("user")?.optString("id", "").orEmpty()
                                    } else if (eventName == "MESSAGE_CREATE" && data != null) {
                                        val author = data.optJSONObject("author")
                                        val content = data.optString("content", "").trim()
                                        val channelId = data.optString("channel_id", "")
                                        val isDirectMessage = data.isNull("guild_id") || data.optString("guild_id", "").isBlank()
                                        val mentions = data.optJSONArray("mentions") ?: JSONArray()
                                        val mentioned = discordBotUserId.isNotBlank() &&
                                            (content.contains("<@" + discordBotUserId + ">") ||
                                                content.contains("<@!" + discordBotUserId + ">") ||
                                                (0 until mentions.length()).any { mentions.optJSONObject(it)?.optString("id") == discordBotUserId })
                                        val commandText = if (discordBotUserId.isBlank()) content else content
                                            .replace("<@" + discordBotUserId + ">", "")
                                            .replace("<@!" + discordBotUserId + ">", "").trim()
                                        val explicitCommand = commandText.startsWith("/kellvyn", ignoreCase = true)
                                        val shouldRespond = isDirectMessage || mentioned || explicitCommand
                                        val cleanText = if (explicitCommand) commandText.removePrefix("/kellvyn").trim() else commandText
                                        if (shouldRespond && cleanText.isNotBlank() && channelId.isNotBlank() && author?.optBoolean("bot", false) != true) {
                                            serviceScope.launch {
                                                try {
                                                    val answer = buildLocalBotReply("discord", channelId, cleanText)
                                                    withContext(Dispatchers.IO) { sendDiscordMessage(token, channelId, answer) }
                                                } catch (error: CancellationException) {
                                                    throw error
                                                } catch (error: Exception) {
                                                    Log.w(TAG, "Falha ao responder mensagem do Discord")
                                                    writeStatus("discord", "Erro ao gerar resposta")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (error: Exception) {
                            Log.w(TAG, "Mensagem Discord inválida")
                        }
                    }

                    override fun onClose(code: Int, reason: String?, remote: Boolean) {
                        heartbeatJob.getAndSet(null)?.cancel()
                        closed.complete(Unit)
                    }

                    override fun onError(ex: Exception) {
                        heartbeatJob.getAndSet(null)?.cancel()
                        if (!closed.isCompleted) closed.completeExceptionally(ex)
                    }
                }
                client = discordClient
                socketHolder.set(discordClient)
                discordSocket = discordClient
                val connected = withContext(Dispatchers.IO) { discordClient.connectBlocking(20, TimeUnit.SECONDS) }
                if (!connected) throw IOException("Não foi possível conectar ao Discord Gateway.")
                writeStatus("discord", "Conectado; aguardando mensagens")
                closed.await()
                heartbeatJob.getAndSet(null)?.cancel()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Conexão do Discord falhou", error)
                writeStatus("discord", "Erro de conexão/token; tentando novamente")
                delay(5000L)
            } finally {
                try { client?.close() } catch (_: Exception) {}
                if (discordSocket === client) discordSocket = null
            }
        }
        writeStatus("discord", "Desativado")
    }

    private fun fetchDiscordGateway(token: String): String {
        val connection = (URL("https://discord.com/api/v10/gateway/bot").openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = CONNECT_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bot " + token)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val body = readLimitedResponse(connection, code)
            if (code !in 200..299) throw IOException("Discord retornou HTTP " + code + "; confira o token e as permissões.")
            val gateway = JSONObject(body).optString("url", "")
            if (!gateway.startsWith("wss://")) throw IOException("Discord não retornou um endereço Gateway seguro.")
            return gateway
        } finally {
            connection.disconnect()
        }
    }

    private fun sendDiscordMessage(token: String, channelId: String, text: String) {
        val payload = JSONObject().put("content", splitForPlatform(text, 1800).firstOrNull().orEmpty())
        val connection = (URL("https://discord.com/api/v10/channels/" + channelId + "/messages").openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = CONNECT_TIMEOUT_MS
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bot " + token)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val message = readLimitedResponse(connection, code)
                throw IOException("Discord não aceitou a resposta (HTTP " + code + "): " + message.take(120))
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun buildLocalBotReply(platform: String, chatId: String, incoming: String): String =
        replyMutex.withLock {
            if (!brain.hasModel()) {
                throw IllegalStateException(
                    "O modelo local ainda não está preparado. Abra o chat principal e envie uma mensagem para autorizar o download antes de iniciar o bot."
                )
            }
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            val behavior = prefs.getString("bot_behavior_" + platform, "").orEmpty().trim()
            val history = readBotHistory(platform, chatId)
            val prompt = buildString {
                append("Você é a mesma Kellvyn IA respondendo através de um bot de ")
                    .append(platform).append(". Use português brasileiro por padrão. ")
                append("Não revele tokens, credenciais ou dados de outras conversas. ")
                if (behavior.isNotBlank()) append("\nInstruções específicas definidas pelo dono do bot:\n")
                    .append(behavior.take(3000)).append('\n')
                if (history.isNotBlank()) append("\nHistórico recente deste chat:\n")
                    .append(history).append('\n')
                append("\nMensagem recebida:\n").append(incoming.take(3000))
            }
            brain.resetConversationAndWait()
            val answer = askLocalBrain(prompt).take(MAX_RESPONSE_CHARS)
            writeBotHistory(platform, chatId, incoming, answer)
            answer
        }

    private suspend fun askLocalBrain(prompt: String): String =
        suspendCancellableCoroutine { continuation ->
            brain.reply(prompt, null, null, object : LocalBrain.Listener {
                override fun onStatus(message: String) { writeStatus("runtime", message) }
                override fun onPartial(text: String) {}
                override fun onComplete(text: String, backend: String) {
                    if (continuation.isActive) continuation.resume(text)
                }
                override fun onError(message: String) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message))
                }
            })
            continuation.invokeOnCancellation { /* Local inference is finite; next request waits on replyMutex. */ }
        }

    private fun readBotHistory(platform: String, chatId: String): String {
        val prefs = getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE)
        val key = platform + ":" + chatId
        return try {
            val array = JSONArray(prefs.getString(key, "[]"))
            val lines = ArrayList<String>()
            for (i in maxOf(0, array.length() - MAX_CHAT_TURNS) until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val role = item.optString("role", "")
                val text = item.optString("text", "")
                if ((role == "Usuário" || role == "Kellvyn IA") && text.isNotBlank()) {
                    lines.add(role + ": " + text.take(1000))
                }
            }
            lines.joinToString("\n")
        } catch (_: Exception) {
            ""
        }
    }

    private fun writeBotHistory(platform: String, chatId: String, userText: String, answer: String) {
        val prefs = getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE)
        val key = platform + ":" + chatId
        val array = try { JSONArray(prefs.getString(key, "[]")) } catch (_: Exception) { JSONArray() }
        array.put(JSONObject().put("role", "Usuário").put("text", userText.take(1000)))
        array.put(JSONObject().put("role", "Kellvyn IA").put("text", answer.take(1000)))
        val trimmed = JSONArray()
        for (i in maxOf(0, array.length() - MAX_CHAT_TURNS) until array.length()) trimmed.put(array.optJSONObject(i))
        prefs.edit().putString(key, trimmed.toString()).apply()
    }

    private fun splitForPlatform(text: String, limit: Int): List<String> {
        val remaining = text.trim()
        if (remaining.isEmpty()) return listOf("Não consegui gerar uma resposta.")
        val parts = ArrayList<String>()
        var rest = remaining
        while (rest.length > limit) {
            var cut = rest.lastIndexOf('\n', limit)
            if (cut < limit / 2) cut = limit
            parts.add(rest.substring(0, cut).trim())
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) parts.add(rest)
        return parts
    }

    private fun readLimitedResponse(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        if (stream == null) return ""
        stream.use { input ->
            val output = StringBuilder()
            val buffer = ByteArray(4096)
            var total = 0
            while (total < 250_000) {
                val read = input.read(buffer)
                if (read < 0) break
                output.append(String(buffer, 0, read, StandardCharsets.UTF_8))
                total += read
            }
            return output.toString()
        }
    }

    private fun userVisibleBotError(error: Exception): String {
        val message = error.message.orEmpty()
        if (message.contains("modelo local ainda não está preparado", true)) return message
        return "A Kellvyn não conseguiu concluir esta resposta agora. Verifique a conexão do bot e tente novamente."
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // The explicit foreground service continues; Android controls final stopping and battery policy.
    }

    override fun onDestroy() {
        stopRuntimeJobs()
        serviceScope.cancel()
        if (::brain.isInitialized) brain.close()
        if (foregroundStarted) stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
