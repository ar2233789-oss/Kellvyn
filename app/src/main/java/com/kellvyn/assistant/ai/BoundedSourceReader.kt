package com.kellvyn.assistant.ai

/**
 * Formats bounded, line-oriented excerpts for the agent. This class has no Android dependencies
 * so limits and range behavior can be verified with ordinary JVM unit tests.
 */
internal object BoundedSourceReader {
    const val MAX_CHARS = 12_000
    const val MAX_LINES = 120

    fun render(
        projectId: String,
        path: String,
        text: String,
        startLine: Int? = null,
        maxLines: Int = MAX_LINES
    ): String {
        if (startLine == null) {
            require(text.length <= MAX_CHARS) {
                "Arquivo muito grande para retornar inteiro (${text.length} caracteres). Use read_file com start_line e max_lines para ler por partes."
            }
            return "Arquivo: $projectId/$path (arquivo completo)\n\u0060\u0060\u0060\n$text\n\u0060\u0060\u0060"
        }

        require(startLine >= 1) { "start_line precisa ser 1 ou maior." }
        require(maxLines in 1..MAX_LINES) {
            "max_lines precisa ficar entre 1 e $MAX_LINES."
        }
        val lines = text.lineSequence().toList()
        require(startLine <= lines.size) {
            "A linha $startLine não existe; o arquivo contém ${lines.size} linhas."
        }
        val endExclusive = minOf(lines.size, startLine - 1 + maxLines)
        val excerpt = lines.subList(startLine - 1, endExclusive).joinToString("\n")
        require(excerpt.length <= MAX_CHARS) {
            "Este trecho ainda é grande demais. Reduza max_lines para ler uma seção menor."
        }
        return "Arquivo: $projectId/$path (linhas $startLine-${endExclusive} de ${lines.size})\n\u0060\u0060\u0060\n$excerpt\n\u0060\u0060\u0060"
    }
}
