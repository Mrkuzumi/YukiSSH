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

    var onOutput: ((ByteArray, Int) -> Unit)? = null
    var onStatusChange: ((Status) -> Unit)? = null

    enum class Status { CONNECTING, CONNECTED, DISCONNECTED, ERROR }

    fun connect(conn: SSHConnection, cols: Int, rows: Int, scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            try {
                onStatusChange?.invoke(Status.CONNECTING)

                val jsch = JSch()
                jsch.setKnownHosts("/dev/null")
                session = jsch.getSession(conn.username, conn.host, conn.port)
                session?.setPassword(conn.password)
                session?.setConfig("StrictHostKeyChecking", "no")
                session?.setConfig("PreferredAuthentications", "password,keyboard-interactive")
                session?.setTimeout(8000)
                session?.connect(8000)

                val ch = session?.openChannel("shell") as? ChannelShell
                channel = ch

                // Use explicit pipe for stdin instead of getOutputStream
                val stdinPipe = PipedInputStream()
                val stdinWriter = PipedOutputStream(stdinPipe)
                ch?.setInputStream(stdinPipe)
                outputStream = stdinWriter

                ch?.setPtySize(cols, rows, 0, 0)
                ch?.setPtyType("xterm-256color")

                inputStream = ch?.inputStream

                ch?.connect(5000)
                onStatusChange?.invoke(Status.CONNECTED)

                // Test send from IO thread
                try {
                    stdinWriter.write('\r'.code)
                    stdinWriter.flush()
                    Log.d("SSHManager", "test newline sent from IO thread")
                } catch (e: Exception) {
                    Log.e("SSHManager", "test send failed", e)
                }

                val buf = ByteArray(4096)
                while (isActive) {
                    val avail = inputStream?.available() ?: 0
                    if (avail > 0) {
                        val len = inputStream!!.read(buf, 0, minOf(avail, buf.size))
                        if (len > 0) {
                            withContext(Dispatchers.Main) {
                                onOutput?.invoke(buf, len)
                            }
                        }
                    } else {
                        delay(10)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("SSHManager", "connection error", e)
                withContext(Dispatchers.Main) {
                    onStatusChange?.invoke(Status.ERROR)
                }
            }
        }
    }

    fun send(data: ByteArray) {
        try {
            outputStream?.let {
                it.write(data)
                it.flush()
                Log.d("SSHManager", "sent ${data.size} bytes")
            } ?: Log.w("SSHManager", "outputStream is null")
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
        onStatusChange?.invoke(Status.DISCONNECTED)
    }
}
