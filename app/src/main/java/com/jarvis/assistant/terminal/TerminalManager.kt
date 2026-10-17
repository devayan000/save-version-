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

    // Commands that need a real Linux filesystem — routed through proot + Alpine rootfs.
    private val prootCommands = setOf(
        "apt", "apt-get", "pkg", "apk", "python", "python3", "pip", "pip3",
        "node", "nodejs", "npm", "npx", "gcc", "g++", "bash", "wget", "curl", "crontab"
    )

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

    /** Explicit one-time environment setup (also happens automatically on first package command). */
    fun setupEnvironment(onProgress: (String) -> Unit = {}, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            val ready = runCatching { PRootBootstrap.setup(onProgress) }.getOrDefault(false)
            onDone(ready)
        }
    }

    fun isLinuxEnvReady(): Boolean = PRootBootstrap.isReady()

    /**
     * Maps Termux-style commands onto the Alpine rootfs and decides whether the
     * command needs proot. Returns (command, needsProot).
     */
    private fun routeCommand(cmd: String): Pair<String, Boolean> {
        val parts = cmd.split(Regex("\\s+"))
        val first = parts.firstOrNull()?.lowercase() ?: ""
        if (first !in prootCommands) return cmd to false
        val mapped = when (first) {
            "apt", "apt-get", "pkg" -> {
                val rest = parts.drop(1)
                val adjusted = if (rest.firstOrNull()?.lowercase() == "install") listOf("add") + rest.drop(1) else rest
                "apk " + adjusted.joinToString(" ")
            }
            else -> cmd
        }
        return mapped to true
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
            val (mapped, useProot) = routeCommand(trimmed)
            val bootstrapLog = StringBuilder()
            if (useProot && !PRootBootstrap.isReady()) {
                // First run: download proot + Alpine rootfs once (APK size stays the same).
                val ready = runCatching { PRootBootstrap.setup { bootstrapLog.appendLine(it) } }.getOrDefault(false)
                if (!ready) {
                    val result = TerminalResult(
                        newId(), command,
                        (bootstrapLog.toString().trim() + "\nEnvironment setup failed — check internet connection.").trim(),
                        -1
                    )
                    addToHistory(result)
                    return@withContext result
                }
            }
            val effectiveTimeout = if (useProot) maxOf(timeoutMs, 600_000) else timeoutMs
            val pb = if (useProot) {
                ProcessBuilder(PRootBootstrap.launchCommand(mapped)).redirectErrorStream(true)
            } else {
                ProcessBuilder("sh", "-c", trimmed).redirectErrorStream(true)
            }
            pb.directory(File(currentDir()))
            if (useProot) {
                pb.environment()["HOME"] = "/root"
                pb.environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            }
            val proc = pb.start()
            val out = StringBuilder()
            if (bootstrapLog.isNotBlank()) out.appendLine(bootstrapLog.toString().trim())
            val reader = Thread { proc.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }
            reader.start()
            val exited = proc.waitFor(effectiveTimeout, TimeUnit.MILLISECONDS)
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
