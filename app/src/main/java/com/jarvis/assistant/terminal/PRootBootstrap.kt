package com.jarvis.assistant.terminal

import android.content.Context
import com.jarvis.assistant.JarvisApp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.zip.GZIPInputStream

/**
 * Termux-style runtime bootstrap for the terminal.
 * Downloads a static proot binary (~1.5 MB) and an Alpine Linux rootfs (~30 MB)
 * on first use, so package commands (pkg/apt/apk) and python/node run inside a
 * real Linux filesystem without growing the APK size.
 */
object PRootBootstrap {

    private const val PROOT_URL = "https://github.com/ysdragon/proot-static/releases/download/v5.4.0/proot-aarch64-static"
    private const val ROOTFS_URL = "https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/aarch64/alpine-minirootfs-3.19.1-aarch64.tar.gz"

    @Volatile
    private var cachedBase: File? = null

    fun baseDir(): File {
        cachedBase?.let { return it }
        val ctx: Context = JarvisApp.instance.applicationContext
        val dir = File(ctx.filesDir, "proot_env")
        if (!dir.exists()) dir.mkdirs()
        cachedBase = dir
        return dir
    }

    private fun prootBin(): File = File(baseDir(), "proot")

    private fun rootfsDir(): File = File(baseDir(), "rootfs")

    fun isReady(): Boolean {
        val proot = prootBin()
        val sh = File(rootfsDir(), "bin/sh")
        return proot.exists() && proot.canExecute() && sh.exists()
    }

    /** Downloads and extracts everything needed. Safe to call more than once. */
    fun setup(onProgress: (String) -> Unit = {}): Boolean {
        val ctx: Context = JarvisApp.instance.applicationContext
        // 1. Static proot binary
        val proot = prootBin()
        if (!proot.exists() || proot.length() < 100_000L) {
            onProgress("Downloading proot (~1.5 MB)...")
            download(PROOT_URL, proot)
            proot.setExecutable(true, false)
        }
        // 2. Alpine Linux rootfs
        val rootfs = rootfsDir()
        if (!File(rootfs, "bin/sh").exists()) {
            onProgress("Downloading Alpine Linux rootfs (~30 MB)...")
            val archive = File(ctx.cacheDir, "alpine-minirootfs.tar.gz")
            download(ROOTFS_URL, archive)
            onProgress("Extracting rootfs...")
            extractTarGz(archive, rootfs)
            archive.delete()
        }
        // 3. DNS for package downloads inside the rootfs
        val resolv = File(rootfs, "etc/resolv.conf")
        if (!resolv.exists()) {
            resolv.parentFile?.mkdirs()
            resolv.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        }
        return isReady()
    }

    /** Full proot launcher: runs [inner] inside the Linux rootfs. */
    fun launchCommand(inner: String): List<String> {
        val rootfs = rootfsDir().absolutePath
        val cwd = TerminalManager.currentDir()
        val workDir = if (cwd.startsWith("/sdcard") || cwd.startsWith("/storage")) cwd else "/root"
        val appFiles = baseDir().parentFile?.absolutePath ?: ""
        val args = mutableListOf(
            prootBin().absolutePath,
            "-r", rootfs,
            "-0",
            "-w", workDir,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "/sdcard:/sdcard"
        )
        if (appFiles.isNotBlank()) args += listOf("-b", "$appFiles:/jarvis-files")
        args += listOf("/bin/sh", "-c", inner)
        return args
    }

    private fun download(url: String, dest: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 120000
        conn.instanceFollowRedirects = true
        try {
            conn.connect()
            dest.parentFile?.mkdirs()
            val tmp = File(dest.absolutePath + ".part")
            val total = conn.contentLengthLong
            var done = 0L
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                        done += n
                    }
                }
            }
            if (total > 0 && done < total) {
                tmp.delete()
                throw IllegalStateException("Download incomplete ($done/$total bytes)")
            }
            if (!tmp.renameTo(dest)) {
                dest.delete()
                if (!tmp.renameTo(dest)) throw IllegalStateException("Could not save download")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Minimal tar.gz extractor that keeps symlinks and exec permissions. */
    private fun extractTarGz(archive: File, dest: File) {
        dest.mkdirs()
        GZIPInputStream(FileInputStream(archive), 64 * 1024).use { input ->
            val header = ByteArray(512)
            var pendingLongName: String? = null
            while (readFully(input, header)) {
                if (isAllZero(header)) break
                var name = readString(header, 0, 100)
                val size = readOctal(header, 124, 12) ?: 0L
                val type = header[156].toInt().toChar()
                val linkName = readString(header, 157, 100)
                val mode = readOctal(header, 100, 8) ?: 0
                if (name.startsWith("./")) name = name.substring(2)
                if (type == 'L') {
                    val body = ByteArray(size.toInt())
                    readFully(input, body)
                    pendingLongName = String(body, Charsets.UTF_8).trimEnd('\u0000')
                    continue
                }
                if (pendingLongName != null) {
                    name = pendingLongName
                    pendingLongName = null
                }
                val target = File(dest, name)
                when (type) {
                    '5' -> target.mkdirs()
                    '2' -> {
                        target.parentFile?.mkdirs()
                        target.delete()
                        runCatching {
                            Files.createSymbolicLink(target.toPath(), FileSystems.getDefault().getPath(linkName))
                        }
                    }
                    '1' -> {
                        target.parentFile?.mkdirs()
                        runCatching { Files.createLink(target.toPath(), File(dest, linkName).toPath()) }
                    }
                    '0', '\u0000' -> {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out -> copyExactly(input, out, size) }
                        applyMode(target, mode)
                    }
                    else -> skipFully(input, size)
                }
                val consumed = if (size % 512L == 0L) size else size + (512L - size % 512L)
                if (type == '0' || type == '\u0000') skipFully(input, consumed - size)
                else if (type != '5') skipFully(input, consumed)
            }
        }
    }

    private fun applyMode(file: File, mode: Long) {
        val m = mode.toInt()
        file.setReadable(true, false)
        file.setWritable((m and 0x22) != 0, false)
        if ((m and 0x111) != 0) file.setExecutable(true, false)
    }

    private fun readFully(input: java.io.InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n == -1) return off == buf.size // EOF mid-block means truncated archive
            off += n
        }
        return true
    }

    private fun skipFully(input: java.io.InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val n = input.skip(remaining)
            if (n <= 0) {
                if (input.read() == -1) break
                remaining -= 1
            } else remaining -= n
        }
    }

    private fun copyExactly(input: java.io.InputStream, out: FileOutputStream, count: Long) {
        var remaining = count
        val buf = ByteArray(64 * 1024)
        while (remaining > 0) {
            val n = input.read(buf, 0, if (remaining < buf.size) remaining.toInt() else buf.size)
            if (n == -1) throw IllegalStateException("Truncated tar entry")
            out.write(buf, 0, n)
            remaining -= n
        }
    }

    private fun isAllZero(buf: ByteArray): Boolean {
        for (b in buf) if (b.toInt() != 0) return false
        return true
    }

    private fun readString(buf: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && buf[end].toInt() != 0) end++
        return String(buf, off, end - off, Charsets.UTF_8)
    }

    private fun readOctal(buf: ByteArray, off: Int, len: Int): Long? {
        val s = readString(buf, off, len).trim(' ', '\u0000')
        if (s.isEmpty()) return null
        return runCatching { java.lang.Long.parseLong(s, 8) }.getOrNull()
    }
}
