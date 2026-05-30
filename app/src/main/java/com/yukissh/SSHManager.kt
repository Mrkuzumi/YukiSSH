package com.yukissh

import android.util.Log
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class SSHManager {

    private var session: com.jcraft.jsch.Session? = null
    private var channel: ChannelShell? = null
    private var job: Job? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val outputListeners = mutableListOf<(ByteArray, Int) -> Unit>()
    private val statusListeners = mutableListOf<(Status) -> Unit>()

    fun addOutputListener(listener: (ByteArray, Int) -> Unit) = outputListeners.add(listener)
    fun removeOutputListener(listener: (ByteArray, Int) -> Unit) = outputListeners.remove(listener)
    fun addStatusListener(listener: (Status) -> Unit) = statusListeners.add(listener)
    fun removeStatusListener(listener: (Status) -> Unit) = statusListeners.remove(listener)

    enum class Status { CONNECTING, CONNECTED, DISCONNECTED, ERROR }

    val isRunning: Boolean get() = session?.isConnected == true && channel?.isConnected == true

    fun connect(conn: SSHConnection, cols: Int, rows: Int) {
        job?.cancel()
        job = scope.launch {
            try {
                statusListeners.forEach { it(Status.CONNECTING) }

                val jsch = JSch()
                jsch.setKnownHosts("/dev/null")
                session = jsch.getSession(conn.username, conn.host, conn.port)
                session?.setPassword(conn.password)
                session?.setConfig("StrictHostKeyChecking", "no")
                session?.setConfig("PreferredAuthentications", "password,keyboard-interactive")
                session?.setTimeout(0)
                session?.connect(8000)
                // Keepalive: send SSH protocol-level ping every 15s to prevent timeout
                session?.setServerAliveInterval(15000)
                session?.setServerAliveCountMax(999)

                val ch = session?.openChannel("shell") as? ChannelShell
                channel = ch

                val stdinPipe = PipedInputStream()
                val stdinWriter = PipedOutputStream(stdinPipe)
                ch?.setInputStream(stdinPipe)
                outputStream = stdinWriter

                ch?.setPtySize(cols, rows, 0, 0)
                ch?.setPtyType("xterm-256color")

                inputStream = ch?.inputStream

                ch?.connect(5000)
                statusListeners.forEach { it(Status.CONNECTED) }

                try {
                    stdinWriter.write('\r'.code)
                    stdinWriter.flush()
                } catch (e: Exception) {
                    Log.e("SSHManager", "test send failed", e)
                }

                val buf = ByteArray(4096)
                while (isActive) {
                    try {
                        val avail = inputStream?.available() ?: 0
                        if (avail > 0) {
                            val len = inputStream!!.read(buf, 0, minOf(avail, buf.size))
                            if (len > 0) {
                                withContext(Dispatchers.Main) {
                                    outputListeners.forEach { it(buf, len) }
                                }
                            }
                        } else {
                            delay(10)
                        }
                    } catch (e: Exception) {
                        if (!isActive) break
                        throw e
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("SSHManager", "connection error", e)
                withContext(Dispatchers.Main) {
                    statusListeners.forEach { it(Status.ERROR) }
                }
            }
        }
    }

    fun send(data: ByteArray) {
        try {
            outputStream?.let {
                it.write(data)
                it.flush()
            }
        } catch (e: Exception) {
            Log.e("SSHManager", "send failed", e)
        }
    }

    fun sendResize(cols: Int, rows: Int) {
        try {
            channel?.setPtySize(cols, rows, 0, 0)
        } catch (_: Exception) {}
    }

    fun disconnect() {
        job?.cancel()
        try { outputStream?.close() } catch (_: Exception) {}
        try { inputStream?.close() } catch (_: Exception) {}
        try { channel?.disconnect() } catch (_: Exception) {}
        try { session?.disconnect() } catch (_: Exception) {}
        session = null
        channel = null
        outputStream = null
        inputStream = null
        statusListeners.forEach { it(Status.DISCONNECTED) }
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
