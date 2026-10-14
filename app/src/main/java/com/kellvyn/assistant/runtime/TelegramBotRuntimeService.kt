package com.kellvyn.assistant.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kellvyn.assistant.MainActivity
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Runs compatible Telegram bots on this phone using the official Bot API long polling. */
class TelegramBotRuntimeService : Service() {
    private val clients = ConcurrentHashMap<String, TelegramBotPollingClient>()
    private val statuses = ConcurrentHashMap<String, String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var runtimeStore: GeneratedProjectRuntimeStore
    private lateinit var credentialsStore: TelegramBotCredentialsStore
    private lateinit var workspaceStore: ProjectWorkspaceStore

    override fun onCreate() {
        super.onCreate()
        runtimeStore = GeneratedProjectRuntimeStore(this)
        credentialsStore = TelegramBotCredentialsStore(this)
        workspaceStore = ProjectWorkspaceStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enterForeground("Preparando bots Telegram...")
        when (intent?.action) {
            ACTION_START_TELEGRAM -> {
                val id = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
                if (id.isNotBlank()) {
                    runtimeStore.setActive(id, true)
                    startTelegramProject(id)
                }
            }
            ACTION_STOP_PROJECT -> {
                intent.getStringExtra(EXTRA_PROJECT_ID)?.takeIf { it.isNotBlank() }?.let(::stopProject)
                stopSelfIfIdle()
            }
            ACTION_STOP_ALL -> {
                clients.keys.toList().forEach(::stopProject)
                stopSelfIfIdle()
            }
            else -> runtimeStore.activeProjectIds().filter(::isTelegramProject).forEach(::startTelegramProject)
        }
        updateNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        clients.values.forEach { runCatching { it.stop() } }
        clients.clear()
        super.onDestroy()
    }

    private fun isTelegramProject(id: String): Boolean {
        val project = workspaceStore.getProject(id) ?: return false
        return runCatching { TelegramBotDefinition.load(workspaceStore, id, project.name) }.isSuccess
    }

    private fun startTelegramProject(id: String) {
        if (clients.containsKey(id)) return
        val project = workspaceStore.getProject(id)
        val credentials = credentialsStore.get(id)
        if (project == null || credentials == null) {
            runtimeStore.setActive(id, false)
            statuses[id] = "Projeto Telegram ou token não disponível"
            updateNotification()
            stopSelfIfIdle()
            return
        }
        val definition = runCatching { TelegramBotDefinition.load(workspaceStore, id, project.name) }
            .getOrElse { error ->
                runtimeStore.setActive(id, false)
                statuses[id] = error.message ?: "Configuração do bot Telegram inválida"
                updateNotification()
                stopSelfIfIdle()
                return
            }
        statuses[id] = "Conectando: " + definition.displayName
        val client = TelegramBotPollingClient(credentials.token, definition) { message ->
            statuses[id] = message
            updateNotification()
        }
        if (clients.putIfAbsent(id, client) == null) client.start() else client.stop()
        updateNotification()
    }

    private fun stopProject(id: String) {
        runtimeStore.setActive(id, false)
        clients.remove(id)?.stop()
        statuses.remove(id)
        updateNotification()
    }

    private fun stopSelfIfIdle() {
        if (clients.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun enterForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val text = statuses.values.toList().let {
            if (it.isEmpty()) "Nenhum bot Telegram conectado." else it.joinToString(" • ").take(180)
        }
        sendBroadcast(
            Intent(ACTION_RUNTIME_STATE_CHANGED)
                .setPackage(packageName)
                .putStringArrayListExtra(EXTRA_ACTIVE_PROJECT_IDS, ArrayList(runtimeStore.activeProjectIds()))
        )
        mainHandler.post {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (clients.isEmpty()) {
                manager.cancel(NOTIFICATION_ID)
            } else {
                manager.notify(NOTIFICATION_ID, buildNotification(text))
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 200, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAll = PendingIntent.getService(
            this, 201, Intent(this, TelegramBotRuntimeService::class.java).setAction(ACTION_STOP_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val count = clients.size
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("Kellvyn • " + count + if (count == 1) " bot Telegram ativo" else " bots Telegram ativos")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(count > 0)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Desativar bots Telegram", stopAll)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Bots Telegram ativos", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Estado dos bots Telegram mantidos ativos no aparelho."
                        setShowBadge(false)
                    }
                )
        }
    }

    companion object {
        const val ACTION_START_TELEGRAM = "com.kellvyn.assistant.runtime.START_TELEGRAM"
        const val ACTION_STOP_PROJECT = "com.kellvyn.assistant.runtime.STOP_TELEGRAM_PROJECT"
        const val ACTION_STOP_ALL = "com.kellvyn.assistant.runtime.STOP_ALL_TELEGRAM"
        const val ACTION_RUNTIME_STATE_CHANGED = "com.kellvyn.assistant.runtime.TELEGRAM_STATE_CHANGED"
        const val EXTRA_ACTIVE_PROJECT_IDS = "active_project_ids"
        const val EXTRA_PROJECT_ID = "project_id"
        private const val CHANNEL_ID = "kellvyn_active_telegram_bots"
        private const val NOTIFICATION_ID = 68121

        fun startTelegram(context: Context, projectId: String) {
            val request = Intent(context, TelegramBotRuntimeService::class.java)
                .setAction(ACTION_START_TELEGRAM)
                .putExtra(EXTRA_PROJECT_ID, projectId)
            ContextCompat.startForegroundService(context, request)
        }

        fun stopProject(context: Context, projectId: String) {
            context.startService(
                Intent(context, TelegramBotRuntimeService::class.java)
                    .setAction(ACTION_STOP_PROJECT).putExtra(EXTRA_PROJECT_ID, projectId)
            )
        }

        fun stopAll(context: Context) {
            context.startService(Intent(context, TelegramBotRuntimeService::class.java).setAction(ACTION_STOP_ALL))
        }
    }
}

private class TelegramBotPollingClient(
    private val token: String,
    private val definition: TelegramBotDefinition,
    private val onStatus: (String) -> Unit
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    @Volatile private var stopped = false
    @Volatile private var currentCall: okhttp3.Call? = null
    private var nextOffset: Long? = null

    fun start() {
        executor.execute { runPollingLoop() }
    }

    fun stop() {
        stopped = true
        currentCall?.cancel()
        executor.shutdownNow()
        http.dispatcher.cancelAll()
    }

    private fun runPollingLoop() {
        var configured = false
        while (!stopped) {
            try {
                if (!configured) {
                    val me = api("getMe", JSONObject())
                    val username = me.optJSONObject("result")?.optString("username").orEmpty()
                    api("setMyCommands", JSONObject().put("commands", JSONArray().apply {
                        definition.commands.forEach { command ->
                            put(JSONObject().put("command", command.name).put("description", command.description.take(256)))
                        }
                    }))
                    onStatus("Ativo no Telegram como " + username.ifBlank { definition.displayName })
                    configured = true
                }

                val args = JSONObject()
                    .put("timeout", 20)
                    .put("allowed_updates", JSONArray().put("message"))
                nextOffset?.let { args.put("offset", it) }
                val updates = api("getUpdates", args).optJSONArray("result") ?: JSONArray()
                for (index in 0 until updates.length()) {
                    if (stopped) break
                    val update = updates.optJSONObject(index) ?: continue
                    val updateId = update.optLong("update_id", -1L)
                    handleMessage(update.optJSONObject("message"))
                    if (updateId >= 0L) nextOffset = updateId + 1L
                }
            } catch (error: Exception) {
                if (!stopped) {
                    configured = false
                    onStatus("Telegram sem resposta: " + safeError(error) + ". Reconectando…")
                    try {
                        Thread.sleep(5_000)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }
    }

    private fun handleMessage(message: JSONObject?) {
        if (message == null) return
        val chat = message.optJSONObject("chat") ?: return
        val chatId = chat.optString("id").ifBlank { return }
        val text = message.optString("text").trim()
        if (!text.startsWith("/")) return

        val firstToken = text.takeWhile { !it.isWhitespace() }.trim()
        val commandName = firstToken.substringBefore('@').removePrefix("/").lowercase(Locale.ROOT)
        val command = definition.commands.firstOrNull { it.name == commandName } ?: return
        val from = message.optJSONObject("from")
        val userName = from?.optString("first_name").orEmpty().ifBlank {
            from?.optString("username").orEmpty().ifBlank { "pessoa" }
        }
        val now = java.time.ZonedDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
        )
        val response = when (command.action) {
            "ping" -> "Pong! Estou ativo."
            "current_time" -> "Agora são " + now + "."
            "roll_dice" -> "🎲 Resultado: " + kotlin.random.Random.nextInt(1, 21)
            "coin_flip" -> "🪙 " + if (kotlin.random.Random.nextBoolean()) "Cara!" else "Coroa!"
            "user_name" -> "Você está falando comigo, " + userName + "."
            "echo" -> {
                val afterCommand = text.substringAfter(' ', "").trim()
                afterCommand.ifBlank { "Você não enviou texto para repetir." }.take(3500)
            }
            else -> command.response.replace("{user}", userName).replace("{time}", now)
        }.take(3800)
        api("sendMessage", JSONObject().put("chat_id", chatId).put("text", response))
    }

    private fun api(method: String, args: JSONObject): JSONObject {
        if (stopped) throw IOException("runtime desativado")
        val request = Request.Builder()
            .url("https://api.telegram.org/bot" + token + "/" + method)
            .post(args.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val call = http.newCall(request)
        currentCall = call
        try {
            call.execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("HTTP " + response.code)
                val json = runCatching { JSONObject(body) }.getOrElse {
                    throw IOException("resposta inválida do Telegram")
                }
                if (!json.optBoolean("ok")) {
                    throw IOException(json.optString("description").ifBlank { "erro da Bot API" }.take(140))
                }
                return json
            }
        } finally {
            if (currentCall === call) currentCall = null
        }
    }

    private fun safeError(error: Exception): String =
        (error.message ?: error.javaClass.simpleName).replace(token, "[token ocultado]").take(140)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
