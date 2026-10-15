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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/** Native Discord Gateway runtime; generated JavaScript is retained as portable source code. */
class DiscordBotRuntimeService : Service() {
    private val startupExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val clients = ConcurrentHashMap<String, DiscordBotGatewayClient>()
    private val statuses = ConcurrentHashMap<String, String>()
    private lateinit var runtimeStore: GeneratedProjectRuntimeStore
    private lateinit var credentialsStore: DiscordBotCredentialsStore
    private lateinit var workspaceStore: ProjectWorkspaceStore

    override fun onCreate() {
        super.onCreate()
        runtimeStore = GeneratedProjectRuntimeStore(this)
        credentialsStore = DiscordBotCredentialsStore(this)
        workspaceStore = ProjectWorkspaceStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enterForeground("Preparando bots...")
        when (intent?.action) {
            ACTION_START_DISCORD -> {
                val id = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
                if (id.isNotBlank()) {
                    runtimeStore.setActive(id, true)
                    startDiscordProject(id)
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
            else -> runtimeStore.activeProjectIds().filter(::isDiscordProject).forEach(::startDiscordProject)
        }
        updateNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        clients.values.forEach { runCatching { it.stop() } }
        clients.clear()
        startupExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun isDiscordProject(id: String): Boolean {
        val project = workspaceStore.getProject(id) ?: return false
        return runCatching { DiscordBotDefinition.load(workspaceStore, id, project.name) }.isSuccess
    }

    private fun startDiscordProject(id: String) {
        if (clients.containsKey(id)) return
        val project = workspaceStore.getProject(id)
        val credentials = credentialsStore.get(id)
        if (project == null || credentials == null) {
            runtimeStore.setActive(id, false)
            statuses[id] = "Projeto ou credenciais não disponíveis"
            updateNotification()
            stopSelfIfIdle()
            return
        }
        val definition = runCatching { DiscordBotDefinition.load(workspaceStore, id, project.name) }
            .getOrElse { error ->
                runtimeStore.setActive(id, false)
                statuses[id] = error.message ?: "Configuração do bot inválida"
                updateNotification()
                stopSelfIfIdle()
                return
            }
        statuses[id] = "Conectando: " + definition.displayName
        val client = DiscordBotGatewayClient(credentials, definition) { message ->
            statuses[id] = message
            updateNotification()
        }
        if (clients.putIfAbsent(id, client) == null) startupExecutor.execute { client.start() } else client.stop()
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
            if (it.isEmpty()) "Nenhum bot conectado. Preparando o runtime." else it.joinToString(" • ").take(180)
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
            this, 100, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAll = PendingIntent.getService(
            this, 101, Intent(this, DiscordBotRuntimeService::class.java).setAction(ACTION_STOP_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val count = clients.size
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("Kellvyn • " + count + if (count == 1) " bot ativo" else " bots ativos")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(clients.isNotEmpty())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Desativar todos", stopAll)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Bots mantidos ativos", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Estado dos bots que você escolheu manter ativos."
                        setShowBadge(false)
                    }
                )
        }
    }

    companion object {
        const val ACTION_START_DISCORD = "com.kellvyn.assistant.runtime.START_DISCORD"
        const val ACTION_STOP_PROJECT = "com.kellvyn.assistant.runtime.STOP_PROJECT"
        const val ACTION_STOP_ALL = "com.kellvyn.assistant.runtime.STOP_ALL"
        const val ACTION_RUNTIME_STATE_CHANGED = "com.kellvyn.assistant.runtime.STATE_CHANGED"
        const val EXTRA_ACTIVE_PROJECT_IDS = "active_project_ids"
        const val EXTRA_PROJECT_ID = "project_id"
        private const val CHANNEL_ID = "kellvyn_active_bots"
        private const val NOTIFICATION_ID = 68120

        fun startDiscord(context: Context, projectId: String) {
            val request = Intent(context, DiscordBotRuntimeService::class.java)
                .setAction(ACTION_START_DISCORD)
                .putExtra(EXTRA_PROJECT_ID, projectId)
            ContextCompat.startForegroundService(context, request)
        }

        fun stopProject(context: Context, projectId: String) {
            context.startService(
                Intent(context, DiscordBotRuntimeService::class.java)
                    .setAction(ACTION_STOP_PROJECT).putExtra(EXTRA_PROJECT_ID, projectId)
            )
        }

        fun stopAll(context: Context) {
            context.startService(Intent(context, DiscordBotRuntimeService::class.java).setAction(ACTION_STOP_ALL))
        }
    }
}

private class DiscordBotGatewayClient(
    private val credentials: DiscordBotCredentials,
    private val definition: DiscordBotDefinition,
    private val onStatus: (String) -> Unit
) {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val retryQueued = AtomicBoolean(false)
    @Volatile private var socket: WebSocket? = null
    @Volatile private var stopped = false
    @Volatile private var commandsRegistered = false
    @Volatile private var sequence: Int? = null
    @Volatile private var heartbeatTask: java.util.concurrent.ScheduledFuture<*>? = null

    fun start() = scheduler.execute { registerThenConnect() }

    fun stop() {
        stopped = true
        heartbeatTask?.cancel(true)
        runCatching { socket?.close(1000, "Disabled by user") }
        socket = null
        scheduler.shutdownNow()
        http.dispatcher.cancelAll()
        onStatus("Desativado pelo usuário")
    }

    private fun registerThenConnect() {
        if (stopped) return
        try {
            if (!commandsRegistered) registerCommands()
            connectGateway()
        } catch (error: Exception) {
            onStatus("Falha ao iniciar " + definition.displayName + ": " + safeError(error) + ". Tentarei novamente.")
            scheduleRetry(30)
        }
    }

    private fun registerCommands() {
        onStatus("Registrando " + definition.commands.size + " comandos slash...")
        val body = JSONArray()
        definition.commands.forEach { command ->
            val item = JSONObject().put("type", 1).put("name", command.name).put("description", command.description)
            if (command.action == "echo") {
                item.put("options", JSONArray().put(
                    JSONObject().put("type", 3).put("name", "texto")
                        .put("description", "Texto que o bot repetirá").put("required", true).put("max_length", 500)
                ))
            }
            body.put(item)
        }
        val request = Request.Builder()
            .url("https://discord.com/api/v10/applications/" + credentials.applicationId + "/commands")
            .header("Authorization", "Bot " + credentials.token)
            .header("User-Agent", "Kellvyn-IA (android, 1.0)")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Discord recusou o registro (HTTP " + response.code + ")")
        }
        commandsRegistered = true
    }

    private fun connectGateway() {
        if (stopped) return
        onStatus("Conectando ao Gateway do Discord...")
        val request = Request.Builder().url("wss://gateway.discord.gg/?v=10&encoding=json")
            .header("User-Agent", "Kellvyn-IA (android, 1.0)").build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!stopped) onStatus("Gateway conectado; autenticando...")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleGatewayMessage(webSocket, text)
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!stopped) scheduleRetry(5)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!stopped) {
                    onStatus("Conexão Discord interrompida; reconectando...")
                    scheduleRetry(5)
                }
            }
        })
    }

    private fun handleGatewayMessage(webSocket: WebSocket, raw: String) {
        val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val op = payload.optInt("op", -1)
        val data = payload.opt("d")
        if (op == 0) {
            val sequenceValue = payload.opt("s")
            if (sequenceValue is Number) sequence = sequenceValue.toInt()
            when (payload.optString("t")) {
                "READY" -> {
                    val user = (data as? JSONObject)?.optJSONObject("user")
                    onStatus("Ativo no Discord como " + user?.optString("username").orEmpty().ifBlank { definition.displayName })
                }
                "INTERACTION_CREATE" -> if (data is JSONObject) scheduler.execute { handleInteraction(data) }
            }
            return
        }
        if (op == 10) {
            val interval = (data as? JSONObject)?.optLong("heartbeat_interval", 45_000L)
                ?.coerceIn(5_000L, 180_000L) ?: 45_000L
            heartbeatTask?.cancel(true)
            heartbeatTask = scheduler.scheduleAtFixedRate({
                val activeSocket = socket
                if (!stopped && activeSocket != null) {
                    activeSocket.send(JSONObject().put("op", 1).put("d", sequence ?: JSONObject.NULL).toString())
                }
            }, interval, interval, TimeUnit.MILLISECONDS)
            webSocket.send(
                JSONObject().put("op", 2).put("d", JSONObject()
                    .put("token", credentials.token)
                    .put("intents", 1)
                    .put("properties", JSONObject().put("\$os", "android").put("\$browser", "Kellvyn IA").put("\$device", "Kellvyn IA"))
                ).toString()
            )
        } else if (op == 7 || op == 9) {
            runCatching { webSocket.close(4000, "Gateway requested a fresh connection") }
            scheduleRetry(5)
        }
    }

    private fun handleInteraction(interaction: JSONObject) {
        if (stopped) return
        val data = interaction.optJSONObject("data") ?: return
        val name = data.optString("name").lowercase()
        val command = definition.commands.firstOrNull { it.name == name } ?: return
        val interactionId = interaction.optString("id")
        val interactionToken = interaction.optString("token")
        if (interactionId.isBlank() || interactionToken.isBlank()) return
        val user = interaction.optJSONObject("member")?.optJSONObject("user") ?: interaction.optJSONObject("user")
        val userName = user?.optString("global_name").orEmpty().ifBlank {
            user?.optString("username").orEmpty().ifBlank { "pessoa" }
        }
        val now = java.time.ZonedDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", java.util.Locale("pt", "BR")))
        val dynamicResponse = when (command.action) {
            "ping" -> "Pong! Estou ativo."
            "current_time" -> "Agora são " + now + "."
            "roll_dice" -> "🎲 Resultado: " + kotlin.random.Random.nextInt(1, 21)
            "coin_flip" -> "🪙 " + if (kotlin.random.Random.nextBoolean()) "Cara!" else "Coroa!"
            "user_name" -> "Você está falando comigo, " + userName + "."
            "server_id" -> "ID deste servidor: " + interaction.optString("guild_id").ifBlank { "indisponível" }
            "echo" -> {
                val options = interaction.optJSONObject("data")?.optJSONArray("options")
                var value = ""
                if (options != null) {
                    for (index in 0 until options.length()) {
                        val option = options.optJSONObject(index) ?: continue
                        if (option.optString("name") == "texto") value = option.optString("value")
                    }
                }
                value.ifBlank { "Você não enviou texto." }.take(500)
            }
            else -> command.response.replace("{user}", userName)
                .replace("{serverId}", interaction.optString("guild_id").ifBlank { "servidor" })
                .replace("{time}", now)
        }
        val content = dynamicResponse.take(1900)
        val request = Request.Builder()
            .url("https://discord.com/api/v10/interactions/" + interactionId + "/" + interactionToken + "/callback")
            .post(JSONObject().put("type", 4).put("data", JSONObject().put("content", content))
                .toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) onStatus("Discord recusou /" + name + " (HTTP " + response.code + ")")
            }
        } catch (_: Exception) {
            if (!stopped) onStatus("Falha de rede ao responder /" + name)
        }
    }

    private fun scheduleRetry(seconds: Long) {
        if (stopped || scheduler.isShutdown || !retryQueued.compareAndSet(false, true)) return
        scheduler.schedule({
            retryQueued.set(false)
            if (!stopped) registerThenConnect()
        }, seconds, TimeUnit.SECONDS)
    }

    private fun safeError(error: Exception): String =
        (error.message ?: error.javaClass.simpleName).replace(credentials.token, "[token ocultado]").take(120)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
