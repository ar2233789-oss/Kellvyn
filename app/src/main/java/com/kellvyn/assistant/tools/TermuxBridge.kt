package com.kellvyn.assistant.tools

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridge to the user's installed Termux app. Commands run inside Termux's real Linux-like
 * environment, never through /system/bin/sh. The interactive Termux app remains available too.
 *
 * Termux requires its external-command permission and allow-external-apps=true. These are
 * deliberate user-controlled gates; Kellvyn must not silently weaken Termux's security settings.
 */
class TermuxBridge(context: Context) {
    private val appContext = context.applicationContext

    fun isInstalled(): Boolean = try {
        @Suppress("DEPRECATION")
        appContext.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Exception) {
        false
    }

    /**
     * Opens the actual Termux terminal UI. If Termux is absent, opens its official release page;
     * no APK is downloaded or installed automatically.
     */
    fun openFullTerminal(): String {
        if (!isInstalled()) {
            return try {
                appContext.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(TERMUX_RELEASES_URL))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "Termux não está instalado. Abri a página oficial de versões para você instalar o aplicativo."
            } catch (e: Exception) {
                "Termux não está instalado e não consegui abrir a página oficial: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        return try {
            val launchIntent = appContext.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
                ?: return "O pacote Termux foi encontrado, mas o Android não disponibilizou a tela de terminal."
            appContext.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Terminal completo do Termux aberto."
        } catch (e: Exception) {
            "Não consegui abrir o Termux: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /**
     * Executes one user-authorized command in Termux and returns its real stdout, stderr and
     * exit code. Shell syntax is interpreted by Termux's bash, not Android's reduced system shell.
     */
    suspend fun runCommand(
        command: String,
        workingDirectory: String = TERMUX_HOME,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS
    ): TermuxCommandResult {
        val normalizedCommand = command.trim()
        require(normalizedCommand.isNotEmpty()) { "Digite um comando para o Termux." }
        require(normalizedCommand.length <= MAX_COMMAND_CHARS) {
            "O comando excede o limite de $MAX_COMMAND_CHARS caracteres."
        }
        require(normalizedCommand.none { it.code == 0 }) { "O comando contém um caractere inválido." }
        require(workingDirectory.isNotBlank() && workingDirectory.length <= MAX_PATH_CHARS) {
            "A pasta de trabalho é inválida."
        }
        require(isInstalled()) {
            "Termux não está instalado. Instale o app oficial antes de executar comandos."
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            appContext.checkSelfPermission(TERMUX_RUN_COMMAND_PERMISSION) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException(
                "O Android ainda não autorizou Kellvyn a executar comandos no Termux. " +
                    "Abra Informações do app Kellvyn > Permissões e autorize “Executar comandos no ambiente Termux”."
            )
        }

        val requestId = UUID.randomUUID().toString()
        val response = CompletableDeferred<TermuxCommandResult>()
        pendingResults[requestId] = response
        val requestCode = requestId.hashCode() and 0x7fffffff
        val resultIntent = Intent(appContext, TermuxCommandResultReceiver::class.java).apply {
            action = "${appContext.packageName}.TERMUX_RESULT.${requestId}"
            putExtra(EXTRA_REQUEST_ID, requestId)
        }
        val resultPendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            resultIntent,
            PendingIntent.FLAG_ONE_SHOT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )

        return try {
            val commandIntent = Intent(ACTION_RUN_COMMAND).apply {
                setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
                putExtra(EXTRA_COMMAND_PATH, TERMUX_BASH)
                putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", normalizedCommand))
                putExtra(EXTRA_WORKDIR, workingDirectory)
                putExtra(EXTRA_BACKGROUND, true)
                putExtra(EXTRA_COMMAND_LABEL, "Kellvyn IA")
                putExtra(
                    EXTRA_COMMAND_DESCRIPTION,
                    "Comando solicitado explicitamente no chat ou no terminal do Kellvyn."
                )
                putExtra(EXTRA_PENDING_INTENT, resultPendingIntent)
            }
            appContext.startService(commandIntent)
            withTimeoutOrNull(timeoutMillis.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)) {
                response.await()
            } ?: throw IllegalStateException(
                "O Termux não devolveu o resultado dentro do prazo. Confirme se allow-external-apps=true " +
                    "está em ~/.termux/termux.properties e execute termux-reload-settings no Termux."
            )
        } catch (e: Exception) {
            when (e) {
                is SecurityException -> throw SecurityException(
                    "O Termux bloqueou o acesso externo. Conceda a permissão RUN_COMMAND ao Kellvyn e " +
                        "defina allow-external-apps=true em ~/.termux/termux.properties; depois execute termux-reload-settings.",
                    e
                )
                else -> throw IllegalStateException(
                    "Falha ao executar no Termux: ${e.message ?: e.javaClass.simpleName}",
                    e
                )
            }
        } finally {
            pendingResults.remove(requestId)
            resultPendingIntent.cancel()
        }
    }

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_RELEASES_URL = "https://github.com/termux/termux-app/releases/latest"
        const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
        const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"
        const val TERMUX_HOME = "~/"

        const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_LABEL"
        const val EXTRA_COMMAND_DESCRIPTION = "com.termux.RUN_COMMAND_DESCRIPTION"
        const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

        const val EXTRA_REQUEST_ID = "kellvyn_termux_request_id"
        const val EXTRA_RESULT_BUNDLE = "com.termux.service.EXTRA_PLUGIN_RESULT_BUNDLE"
        const val EXTRA_RESULT_STDOUT = "com.termux.service.EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT"
        const val EXTRA_RESULT_STDERR = "com.termux.service.EXTRA_PLUGIN_RESULT_BUNDLE_STDERR"
        const val EXTRA_RESULT_EXIT_CODE = "com.termux.service.EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE"
        const val EXTRA_RESULT_ERROR = "com.termux.service.EXTRA_PLUGIN_RESULT_BUNDLE_ERR"
        const val EXTRA_RESULT_ERROR_MESSAGE = "com.termux.service.EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG"

        const val MAX_COMMAND_CHARS = 16_000
        const val MAX_PATH_CHARS = 4_096
        const val MAX_OUTPUT_CHARS = 90_000
        const val DEFAULT_TIMEOUT_MS = 120_000L
        const val MIN_TIMEOUT_MS = 5_000L
        const val MAX_TIMEOUT_MS = 180_000L

        private val pendingResults = ConcurrentHashMap<String, CompletableDeferred<TermuxCommandResult>>()

        internal fun complete(requestId: String, result: TermuxCommandResult) {
            pendingResults[requestId]?.complete(result)
        }

        internal fun completeFailure(requestId: String, reason: String) {
            pendingResults[requestId]?.complete(TermuxCommandResult("", reason, -1, reason))
        }
    }
}

data class TermuxCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val internalError: String = ""
) {
    val succeeded: Boolean get() = exitCode == 0 && internalError.isBlank()

    fun formattedOutput(): String = buildString {
        if (stdout.isNotBlank()) append(stdout.take(TermuxBridge.MAX_OUTPUT_CHARS))
        if (stderr.isNotBlank()) {
            if (isNotEmpty()) append("\n")
            append(stderr.take(TermuxBridge.MAX_OUTPUT_CHARS))
        }
        if (internalError.isNotBlank()) {
            if (isNotEmpty()) append("\n")
            append("ERRO DO TERMUX: ").append(internalError.take(4_000))
        }
        if (isEmpty()) append("(sem saída)")
        append("\n\nCódigo de saída: ").append(exitCode)
    }.take(TermuxBridge.MAX_OUTPUT_CHARS)
}

/** Receives the result PendingIntent created by Termux's documented RUN_COMMAND interface. */
class TermuxCommandResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(TermuxBridge.EXTRA_REQUEST_ID) ?: return
        val resultBundle: Bundle? = intent.getBundleExtra(TermuxBridge.EXTRA_RESULT_BUNDLE)
        if (resultBundle == null) {
            TermuxBridge.completeFailure(requestId, "O Termux retornou uma resposta sem dados de resultado.")
            return
        }
        val internalErrorCode = resultBundle.getInt(TermuxBridge.EXTRA_RESULT_ERROR, Activity.RESULT_OK)
        val internalError = if (internalErrorCode == Activity.RESULT_OK) "" else
            resultBundle.getString(TermuxBridge.EXTRA_RESULT_ERROR_MESSAGE)
                ?.takeIf { it.isNotBlank() }
                ?: "O Termux não conseguiu iniciar ou concluir o comando (código interno $internalErrorCode)."
        val result = TermuxCommandResult(
            stdout = resultBundle.getString(TermuxBridge.EXTRA_RESULT_STDOUT).orEmpty(),
            stderr = resultBundle.getString(TermuxBridge.EXTRA_RESULT_STDERR).orEmpty(),
            exitCode = resultBundle.getInt(TermuxBridge.EXTRA_RESULT_EXIT_CODE, -1),
            internalError = internalError
        )
        TermuxBridge.complete(requestId, result)
    }
}
