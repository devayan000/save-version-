package com.jarvis.assistant.terminal

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Termux-style shell runner. Executes commands in the background and keeps an output history. */
object TerminalManager {

    data class TerminalResult(val id: String, val command: String, val output: String, val exitCode: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val history = ArrayDeque<TerminalResult>()
    private val lock = Any()

    @Volatile
    private var workingDir: String = ""

    fun currentDir(): String = workingDir.ifBlank { System.getProperty("user.dir") ?: "/" }

    /** Fire-and-forget background execution so the UI and assistant stay responsive. */
    fun run(command: String, timeoutMs: Long = 30000, onDone: (TerminalResult) -> Unit = {}) {
        scope.launch { onDone(runSuspend(command, timeoutMs)) }
    }

    /** Suspend version for agent tools and services. */
    suspend fun runSuspend(command: String, timeoutMs: Long = 30000): TerminalResult = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        // cd persists across commands, like a real shell session.
        if (trimmed.startsWith("cd ") || trimmed == "cd") {
            val target = trimmed.removePrefix("cd").trim()
            val candidate = when {
                target.isBlank() -> File(System.getProperty("user.home") ?: currentDir())
                target.startsWith("/") -> File(target)
                else -> File(currentDir(), target)
            }
            val result = if (candidate.isDirectory) {
                workingDir = candidate.canonicalPath
                TerminalResult(newId(), command, "(cwd: " + candidate.canonicalPath + ")", 0)
            } else {
                TerminalResult(newId(), command, "cd: no such directory: " + target, 1)
            }
            addToHistory(result)
            return@withContext result
        }
        try {
            val pb = ProcessBuilder("sh", "-c", trimmed).redirectErrorStream(true)
            pb.directory(File(currentDir()))
            val proc = pb.start()
            val out = StringBuilder()
            val reader = Thread { proc.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }
            reader.start()
            val exited = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!exited) proc.destroyForcibly()
            reader.join(2000)
            val result = TerminalResult(
                newId(), command,
                out.toString().trim().ifBlank { "(no output)" },
                if (exited) proc.exitValue() else -1
            )
            addToHistory(result)
            result
        } catch (e: Exception) {
            val result = TerminalResult(newId(), command, "Error: " + (e.message ?: e.javaClass.simpleName), -1)
            addToHistory(result)
            result
        }
    }

    fun historyList(): List<TerminalResult> = synchronized(lock) { history.toList() }
    fun clearHistory() = synchronized(lock) { history.clear() }

    private fun addToHistory(r: TerminalResult) = synchronized(lock) {
        history.addLast(r)
        while (history.size > 200) history.removeFirst()
    }

    private fun newId() = UUID.randomUUID().toString()
}
