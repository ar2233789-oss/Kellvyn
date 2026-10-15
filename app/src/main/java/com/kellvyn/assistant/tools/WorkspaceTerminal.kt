package com.kellvyn.assistant.tools

import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import org.eclipse.jgit.api.Git
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

/**
 * Independent terminal for workspace helpers and explicitly typed Android shell commands.
 * Workspace commands use safe file/Git APIs; other commands go to /system/bin/sh as the app UID.
 * This terminal is not exposed to, invoked by, or used by the chat agent.
 */
class WorkspaceTerminal(private val workspace: ProjectWorkspaceStore) {
    private val root = workspace.workspaceDirectory().canonicalFile
    @Volatile private var currentDirectory: File = root
    @Volatile private var lastOutput: String = ""

    fun storedOutput(): String = lastOutput

    fun saveOutput(value: String) {
        lastOutput = value.takeLast(MAX_OUTPUT_CHARS)
    }

    fun currentDirectoryFile(): File = currentDirectory.canonicalFile

    fun pathLabel(): String {
        val relative = currentDirectory.relativeTo(root).invariantSeparatorsPath
        return if (relative == ".") "/workspace" else "/workspace/$relative"
    }

    /**
     * Interactive hybrid console: custom workspace/Git helpers keep their own bounded handling;
     * ordinary shell commands use Android's /system/bin/sh in the current workspace directory.
     * This runs as the app UID, without root; Android app permissions still apply to commands and paths.
     */
        fun executeInteractive(raw: String): String {
        val command = tokenize(raw).firstOrNull()?.lowercase(Locale.ROOT) ?: return ""
        return if (shouldUseWorkspaceTerminalHandler(raw)) {
            execute(raw)
        } else {
            ManualShellExecutor(root).execute(raw, currentDirectoryFile())
        }
    }

    fun execute(raw: String): String {
        val tokens = tokenize(raw)
        if (tokens.isEmpty()) return ""
        val command = tokens.first().lowercase(Locale.ROOT)
        val args = tokens.drop(1)
        return when (command) {
            "help" -> helpText()
            "pwd" -> pathLabel()
            "cd" -> changeDirectory(args)
            "ls", "dir" -> listDirectory(args)
            "tree" -> tree(args)
            "find" -> findFiles(args)
            "cat" -> readFiles(args)
            "head" -> headTail(args, takeHead = true)
            "tail" -> headTail(args, takeHead = false)
            "grep" -> grep(args)
            "wc" -> wordCount(args)
            "mkdir" -> makeDirectory(args)
            "touch" -> touch(args)
            "write" -> writeText(args, append = false)
            "append" -> writeText(args, append = true)
            "rm" -> removeFile(args)
            "cp" -> copyFile(args)
            "mv" -> moveFile(args)
            "stat" -> stat(args)
            "status" -> workspaceStatus()
            "projects" -> listProjects()
            "git" -> gitCommand(args)
            "gradle", "./gradlew", "gradlew", "kotlinc", "java", "aapt2", "d8", "r8", "zipalign", "apksigner" ->
                "$command não é executado pelo terminal interno. O compilador Android local está integrado ao modo Build e ao agente de chat; usa ECJ → D8 → manifesto binário → APK assinado/verificado e aceita Java com APIs do Android, sem Gradle, Kotlin/Compose ou bibliotecas externas. Nenhum build foi executado por este comando."
            else -> "Comando não reconhecido: $command. Digite help. Os comandos não são executados por um shell do sistema."
        }
    }

    private fun helpText() = """
        KELLVYN TERMINAL — comandos de workspace e shell Android
        pwd                         mostra a pasta atual do workspace
        ls [caminho]                lista arquivos e pastas
        cd <pasta>                  muda a pasta atual dentro de /workspace
        tree [caminho]              árvore curta de arquivos
        find [caminho]              procura/lista arquivos do workspace
        cat <arquivo>               exibe arquivos de texto
        head [-n linhas] <arquivo>  primeiras linhas
        tail [-n linhas] <arquivo>  últimas linhas
        grep <texto> <arquivo>      procura texto literal
        wc <arquivo>                conta linhas/palavras/caracteres
        mkdir, touch, write, append  cria e grava arquivos no workspace
        cp, mv, rm, stat            operações locais em arquivos
        projects                    lista projetos Kellvyn
        status                      estado do workspace e do compilador
        git status|diff|log|branch  consulta Git local
        git init|add|commit          alterações Git com confirmações quando exigidas
        clear                       limpa a tela do terminal

        Comandos padrão (ls -la, cat, grep, find, mkdir, rm, cp, mv, pipelines, &&, variáveis
        e redirecionamentos) são enviados ao /system/bin/sh do Android, executados como UID do app,
        sem root. O diretório inicial é o workspace, mas o shell tem as permissões normais deste app;
        confira comandos destrutivos antes de executá-los. Só funcionam binários realmente instalados.
        Esta aplicação não inclui o gerenciador de pacotes nem a coleção de binários do Termux. Cada
        comando tem limite de 120 s e saída de 128 KB. O shell manual é independente do agente de IA
        e não é usado pelo chat.

        O compilador do chat é separado: ECJ → D8 → manifesto binário → APK assinado/verificado.
        O perfil inicial de APK aceita Java nativo com APIs Android, sem Kotlin/Compose, bibliotecas
        externas ou recursos externos.
    """.trimIndent()

    private fun changeDirectory(args: List<String>): String {
        require(args.size <= 1) { "Uso: cd <pasta>" }
        val target = when (args.firstOrNull()) {
            null, "~", "/" -> root
            else -> resolve(args.first())
        }
        require(target.isDirectory) { "Pasta não encontrada: ${args.firstOrNull() ?: "/workspace"}" }
        currentDirectory = target
        return pathLabel()
    }

    private fun listDirectory(args: List<String>): String {
        require(args.size <= 1) { "Uso: ls [caminho]" }
        val target = args.firstOrNull()?.let { resolve(it) } ?: currentDirectory
        require(target.isDirectory) { "Não é uma pasta: ${args.firstOrNull()}" }
        val children = target.listFiles()?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) })).orEmpty()
        if (children.isEmpty()) return "(pasta vazia)"
        return children.take(MAX_LIST_ITEMS).joinToString("\n") {
            val suffix = if (it.isDirectory) "/" else ""
            val size = if (it.isFile) "  ${formatBytes(it.length())}" else ""
            "${it.name}$suffix$size"
        } + if (children.size > MAX_LIST_ITEMS) "\n… lista limitada a $MAX_LIST_ITEMS itens" else ""
    }

    private fun tree(args: List<String>): String {
        require(args.size <= 1) { "Uso: tree [caminho]" }
        val start = args.firstOrNull()?.let { resolve(it) } ?: currentDirectory
        require(start.isDirectory) { "Não é uma pasta." }
        val output = StringBuilder(start.name.ifBlank { "workspace" })
        var count = 0
        fun visit(dir: File, prefix: String, depth: Int) {
            if (depth >= 4 || count >= MAX_TREE_ITEMS) return
            val children = dir.listFiles()
                ?.filter { it.name != ".git" && it.name != ".kellvyn-project.json" }
                ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
                .orEmpty()
            children.forEachIndexed { index, child ->
                if (count >= MAX_TREE_ITEMS) return
                count++
                val last = index == children.lastIndex
                output.append("\n").append(prefix).append(if (last) "└── " else "├── ")
                    .append(child.name).append(if (child.isDirectory) "/" else "")
                if (child.isDirectory) visit(child, prefix + if (last) "    " else "│   ", depth + 1)
            }
        }
        visit(start, "", 0)
        if (count >= MAX_TREE_ITEMS) output.append("\n… árvore limitada a $MAX_TREE_ITEMS entradas")
        return output.toString()
    }

    private fun findFiles(args: List<String>): String {
        require(args.size <= 1) { "Uso: find [caminho]" }
        val start = args.firstOrNull()?.let { resolve(it) } ?: currentDirectory
        require(start.isDirectory) { "Não é uma pasta." }
        val result = ArrayList<String>(MAX_LIST_ITEMS)
        val iterator = start.walkTopDown().onEnter { it.name != ".git" }.iterator()
        while (iterator.hasNext() && result.size < MAX_LIST_ITEMS) {
            val file = iterator.next()
            if (file != start && file.name != ".kellvyn-project.json") {
                result.add(file.relativeTo(root).invariantSeparatorsPath + if (file.isDirectory) "/" else "")
            }
        }
        if (result.isEmpty()) return "(nenhum arquivo)"
        return result.joinToString("\n") +
            if (iterator.hasNext()) "\n… lista limitada a $MAX_LIST_ITEMS entradas" else ""
    }

    private fun readFiles(args: List<String>): String {
        require(args.isNotEmpty()) { "Uso: cat <arquivo> [outro-arquivo...]" }
        require(args.size <= 5) { "cat aceita até 5 arquivos por execução." }
        return args.joinToString("\n") { name ->
            val file = resolve(name)
            require(file.isFile) { "Arquivo não encontrado: $name" }
            require(file.length() <= MAX_READ_BYTES) { "$name é muito grande para exibir (limite 100 KB)." }
            val text = file.readText(Charsets.UTF_8)
            if (args.size > 1) "== $name ==\n$text" else text
        }.take(MAX_OUTPUT_CHARS)
    }

    private fun headTail(args: List<String>, takeHead: Boolean): String {
        require(args.isNotEmpty()) { "Uso: ${if (takeHead) "head" else "tail"} [-n linhas] <arquivo>" }
        var count = 10
        var fileName: String
        if (args.size >= 3 && args[0] == "-n") {
            count = args[1].toIntOrNull() ?: throw IllegalArgumentException("O número de linhas é inválido.")
            require(count in 1..500) { "Use entre 1 e 500 linhas." }
            fileName = args[2]
        } else {
            require(args.size == 1) { "Uso: ${if (takeHead) "head" else "tail"} [-n linhas] <arquivo>" }
            fileName = args[0]
        }
        val file = resolve(fileName)
        require(file.isFile && file.length() <= MAX_READ_BYTES) { "Arquivo inexistente ou maior que 100 KB." }
        val lines = file.readLines(Charsets.UTF_8)
        return (if (takeHead) lines.take(count) else lines.takeLast(count)).joinToString("\n")
    }

    private fun grep(args: List<String>): String {
        require(args.size >= 2) { "Uso: grep <texto-literal> <arquivo>" }
        val query = args.first()
        require(query.isNotEmpty()) { "O texto de busca está vazio." }
        val file = resolve(args[1])
        require(file.isFile && file.length() <= MAX_READ_BYTES) { "Arquivo inexistente ou maior que 100 KB." }
        val matches = file.readLines(Charsets.UTF_8).mapIndexedNotNull { index, line ->
            if (line.contains(query, ignoreCase = false)) "${index + 1}: $line" else null
        }
        return matches.take(500).joinToString("\n").ifBlank { "Nenhuma ocorrência de '$query'." }
    }

    private fun wordCount(args: List<String>): String {
        require(args.size == 1) { "Uso: wc <arquivo>" }
        val file = resolve(args[0])
        require(file.isFile && file.length() <= MAX_READ_BYTES) { "Arquivo inexistente ou maior que 100 KB." }
        val text = file.readText(Charsets.UTF_8)
        return "linhas=${if (text.isEmpty()) 0 else text.count { it == '\n' } + if (text.endsWith("\n")) 0 else 1} palavras=${text.trim().split(Regex("\\s+")).count { it.isNotBlank() }} caracteres=${text.length} bytes=${file.length()}"
    }

    private fun makeDirectory(args: List<String>): String {
        require(args.size == 1) { "Uso: mkdir <pasta>" }
        val dir = resolve(args[0])
        requireSafeForMutation(dir)
        if (dir.exists()) return "Já existe: ${relative(dir)}"
        require(dir.mkdirs()) { "Não foi possível criar a pasta." }
        return "Pasta criada: ${relative(dir)}"
    }

    private fun touch(args: List<String>): String {
        require(args.size == 1) { "Uso: touch <arquivo>" }
        val file = resolve(args[0])
        requireSafeForMutation(file)
        file.parentFile?.let { require(it.isDirectory) { "A pasta pai não existe. Use mkdir antes." } }
        if (file.exists()) return "Arquivo já existe: ${relative(file)}"
        require(file.createNewFile()) { "Não foi possível criar o arquivo." }
        return "Arquivo criado: ${relative(file)}"
    }

    private fun writeText(args: List<String>, append: Boolean): String {
        require(args.size >= 2) { "Uso: ${if (append) "append" else "write"} <arquivo> <texto>" }
        val file = resolve(args.first())
        requireSafeForMutation(file)
        require(file.parentFile?.isDirectory == true) { "A pasta pai não existe. Use mkdir antes." }
        val text = args.drop(1).joinToString(" ")
        require(text.length <= MAX_WRITE_CHARS) { "Texto muito longo (limite 20.000 caracteres por comando)." }
        if (append) file.appendText(text, Charsets.UTF_8) else file.writeText(text, Charsets.UTF_8)
        return "${if (append) "Texto acrescentado em" else "Arquivo gravado:"} ${relative(file)} (${text.length} caracteres)."
    }

    private fun removeFile(args: List<String>): String {
        require(args.size == 1) { "Uso: rm <arquivo> (não apaga pastas)" }
        val file = resolve(args[0])
        requireSafeForMutation(file)
        require(file.isFile) { "Só é permitido apagar arquivos comuns; pastas e metadados são protegidos." }
        require(file.delete()) { "Não foi possível apagar o arquivo." }
        return "Arquivo removido: ${relative(file)}"
    }

    private fun copyFile(args: List<String>): String {
        require(args.size == 2) { "Uso: cp <origem> <destino>" }
        val source = resolve(args[0])
        val destination = resolve(args[1])
        require(source.isFile) { "A origem não é um arquivo." }
        requireSafeForMutation(destination)
        require(source.length() <= MAX_READ_BYTES) { "A cópia pelo terminal está limitada a arquivos de 100 KB." }
        val target = if (destination.isDirectory) File(destination, source.name) else destination
        requireSafeForMutation(target)
        require(source.canonicalFile != target.canonicalFile) { "Origem e destino são o mesmo arquivo." }
        source.copyTo(target, overwrite = true)
        return "Copiado: ${relative(source)} → ${relative(target)}"
    }

    private fun moveFile(args: List<String>): String {
        require(args.size == 2) { "Uso: mv <origem> <destino>" }
        val source = resolve(args[0])
        val destination = resolve(args[1])
        require(source.isFile) { "A origem precisa ser um arquivo comum." }
        requireSafeForMutation(source)
        requireSafeForMutation(destination)
        val target = if (destination.isDirectory) File(destination, source.name) else destination
        requireSafeForMutation(target)
        require(source.canonicalFile != target.canonicalFile) { "Origem e destino são o mesmo arquivo." }
        if (target.exists()) {
            require(target.isFile) { "O destino existe e não é um arquivo comum." }
            require(target.delete()) { "Não foi possível substituir o arquivo de destino." }
        }
        require(source.renameTo(target)) { "Não foi possível mover o arquivo." }
        return "Movido: ${relative(source)} → ${relative(target)}"
    }

    private fun stat(args: List<String>): String {
        require(args.size == 1) { "Uso: stat <caminho>" }
        val file = resolve(args[0])
        if (!file.exists()) return "Não encontrado: ${args[0]}"
        return buildString {
            appendLine("caminho: ${relative(file)}")
            appendLine("tipo: ${if (file.isDirectory) "pasta" else "arquivo"}")
            if (file.isFile) appendLine("tamanho: ${file.length()} bytes")
            appendLine("modificado: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(file.lastModified())}")
            appendLine("legível: ${file.canRead()} | gravável: ${file.canWrite()}")
        }.trimEnd()
    }

    private fun workspaceStatus(): String = buildString {
        appendLine("terminal: workspace local restrito")
        appendLine("diretório: ${pathLabel()}")
        appendLine("arquivos nesta pasta: ${currentDirectory.listFiles()?.count { it.isFile } ?: 0}")
        appendLine("projetos registrados: ${workspace.listProjects().size}")
        appendLine("compilador Android local: integrado ao chat/agente; perfil atual aceita Java Android sem bibliotecas externas")
    }.trimEnd()

    private fun listProjects(): String {
        val projects = workspace.listProjects()
        return if (projects.isEmpty()) "(nenhum projeto salvo)" else projects.joinToString("\n") {
            "${it.id} — ${it.name} (${it.fileCount} arquivos)"
        }
    }

    private fun gitCommand(args: List<String>): String {
        require(args.isNotEmpty()) { "Uso: git init|status|add|diff|log|branch|commit <mensagem>" }
        return when (args[0].lowercase(Locale.ROOT)) {
            "init" -> {
                require(args.size == 1) { "Uso: git init" }
                require(currentDirectory != root) { "Entre na pasta de um projeto primeiro: cd <id-do-projeto>" }
                val projectRoot = workspace.listProjects().any {
                    runCatching { workspace.projectDirectory(it.id).canonicalFile == currentDirectory.canonicalFile }.getOrDefault(false)
                }
                require(projectRoot) { "git init só pode iniciar um repositório na raiz de um projeto registrado." }
                require(!File(currentDirectory, ".git").exists()) { "Este projeto já possui um repositório Git local." }
                Git.init().setDirectory(currentDirectory).call().use { git ->
                    git.repository.config.setString("user", null, "name", "Kellvyn IA")
                    git.repository.config.setString("user", null, "email", "kellvyn@local")
                    git.repository.config.save()
                }
                "Repositório Git local inicializado. Nenhuma conexão remota foi criada."
            }
            "status" -> withGit { git ->
                val s = git.status().call()
                buildString {
                    appendLine("branch: ${git.repository.branch ?: "(sem branch)"}")
                    appendLine(if (s.isClean) "árvore limpa" else "alterações locais:")
                    if (s.added.isNotEmpty()) appendLine("  adicionados: ${s.added.sorted().joinToString()}")
                    if (s.changed.isNotEmpty()) appendLine("  modificados: ${s.changed.sorted().joinToString()}")
                    if (s.removed.isNotEmpty()) appendLine("  removidos: ${s.removed.sorted().joinToString()}")
                    if (s.untracked.isNotEmpty()) appendLine("  não rastreados: ${s.untracked.sorted().joinToString()}")
                    if (s.missing.isNotEmpty()) appendLine("  ausentes: ${s.missing.sorted().joinToString()}")
                }.trimEnd()
            }
            "add" -> {
                require(args.size == 2 && args[1] == ".") { "Uso: git add . (adiciona arquivos da pasta atual do repositório)" }
                withGit { git ->
                    git.add().addFilepattern(".").call()
                    git.add().setUpdate(true).addFilepattern(".").call()
                    "Arquivos locais preparados para commit."
                }
            }
            "diff" -> withGit { git ->
                val bytes = ByteArrayOutputStream()
                git.diff().setOutputStream(bytes).call()
                bytes.toString(Charsets.UTF_8.name()).take(MAX_OUTPUT_CHARS).ifBlank { "Sem diferenças no índice de trabalho." }
            }
            "log" -> withGit { git ->
                val commits = git.log().setMaxCount(20).call().toList()
                if (commits.isEmpty()) "(ainda não há commits locais)" else commits.joinToString("\n") {
                    "${it.name.take(8)}  ${it.shortMessage}"
                }
            }
            "branch" -> withGit { git ->
                git.branchList().call().joinToString("\n") {
                    (if (it.name == git.repository.fullBranch) "* " else "  ") + it.name.removePrefix("refs/heads/")
                }.ifBlank { "(nenhuma branch criada)" }
            }
            "commit" -> {
                require(args.size >= 2) { "Uso: git commit <mensagem>" }
                withGit { git ->
                    val s = git.status().call()
                    require(!s.isClean) { "Não há alterações preparadas ou arquivos modificados para salvar." }
                    git.commit().setMessage(args.drop(1).joinToString(" "))
                        .setAuthor("Kellvyn IA", "kellvyn@local")
                        .setCommitter("Kellvyn IA", "kellvyn@local").call()
                        .let { "Commit local criado: ${it.name.take(8)} — ${it.shortMessage}" }
                }
            }
            else -> "Subcomando Git indisponível. Permitidos: init, status, add ., diff, log, branch, commit."
        }
    }

    private inline fun <T> withGit(block: (Git) -> T): T {
        val gitDir = findGitRoot()
            ?: throw IllegalStateException("Nenhum repositório Git local. Use cd <projeto> e git init.")
        return Git.open(gitDir).use(block)
    }

    private fun findGitRoot(): File? {
        var directory: File? = currentDirectory
        while (directory != null && directory.canonicalPath.startsWith(root.path)) {
            if (File(directory, ".git").exists()) return directory
            if (directory == root) break
            directory = directory.parentFile
        }
        return null
    }

    private fun resolve(input: String): File {
        require(input.isNotBlank()) { "Caminho vazio." }
        require(!input.startsWith("/") && !input.startsWith("\\") && !input.contains('\u0000')) {
            "Use caminhos relativos dentro de /workspace."
        }
        val target = File(currentDirectory, input).canonicalFile
        require(target.path == root.path || target.path.startsWith(root.path + File.separator)) {
            "Acesso negado: o caminho sai do workspace privado."
        }
        return target
    }

    private fun requireSafeForMutation(file: File) {
        require(file.path != root.path) { "A raiz do workspace é protegida." }
        val relative = relative(file).split('/')
        require(relative.none {
            it.equals(".git", true) || it.equals(".kellvyn-project.json", true) || it.equals(".gradle", true)
        }) { "Metadados internos do projeto, Git e Gradle são protegidos contra edição pelo terminal." }
        require(!file.isDirectory || file == currentDirectory || file.canonicalPath.startsWith(root.path + File.separator)) {
            "Pasta fora do workspace."
        }
    }

    private fun relative(file: File): String =
        file.canonicalFile.relativeTo(root).invariantSeparatorsPath

    private fun tokenize(input: String): List<String> {
        val tokens = ArrayList<String>()
        val buffer = StringBuilder()
        var quote: Char? = null
        var escaped = false
        for (char in input.trim()) {
            if (escaped) {
                buffer.append(char)
                escaped = false
            } else if (char == '\\' && quote != null) {
                escaped = true
            } else if (quote != null && char == quote) {
                quote = null
            } else if (quote == null && (char == '\'' || char == '"')) {
                quote = char
            } else if (quote == null && char.isWhitespace()) {
                if (buffer.isNotEmpty()) {
                    tokens += buffer.toString()
                    buffer.setLength(0)
                }
            } else {
                buffer.append(char)
            }
        }
        require(quote == null) { "Aspas não fechadas no comando." }
        if (buffer.isNotEmpty()) tokens += buffer.toString()
        return tokens
    }

    private fun formatBytes(value: Long): String = when {
        value < 1024 -> "$value B"
        value < 1024 * 1024 -> "${"%.1f".format(Locale.ROOT, value / 1024.0)} KB"
        else -> "${"%.1f".format(Locale.ROOT, value / (1024.0 * 1024.0))} MB"
    }

    private companion object {
        const val MAX_LIST_ITEMS = 1_000
        const val MAX_TREE_ITEMS = 300
        const val MAX_READ_BYTES = 100L * 1024L
        const val MAX_WRITE_CHARS = 20_000
        const val MAX_OUTPUT_CHARS = 30_000
    }
}


/**
 * Custom commands that must preserve workspace navigation or use the app's JGit API.
 * All ordinary shell utilities are delegated to Android's shell for standard flags/pipelines.
 */
internal fun shouldUseWorkspaceTerminalHandler(rawCommand: String): Boolean {
    val input = rawCommand.trim()
    val command = input.substringBefore(Regex("\\s+")).lowercase(Locale.ROOT)
    if (command == "cd") {
        val arguments = input.substringAfter(Regex("\\s+"), missingDelimiterValue = "")
        // A standalone cd updates the terminal's persistent workspace directory. With shell
        // operators, leave the whole expression to /system/bin/sh (e.g. "cd app && ls -la").
        return !Regex("""(?:&&|\|\||[;|<>\x60])""").containsMatchIn(arguments) &&
            !arguments.contains("$(")
    }
    return command in setOf("help", "pwd", "tree", "write", "append", "status", "projects", "git")
}

