package com.yukissh

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SSHService : Service() {

    private val binder = SSHBinder()
    val sshManager = SSHManager()

    inner class SSHBinder : Binder() {
        fun getManager(): SSHManager = sshManager
        fun getService(): SSHService = this@SSHService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SSH 会话",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private var connName: String = ""
    private var currentStatus: String = ""
    private var notifyStatusListener: ((SSHManager.Status) -> Unit)? = null

    fun startConnection(conn: SSHConnection, cols: Int, rows: Int) {
        connName = conn.name.ifEmpty { "${conn.username}@${conn.host}" }
        currentStatus = "正在连接…"
        updateNotification()
        notifyStatusListener?.let { sshManager.removeStatusListener(it) }
        notifyStatusListener = { status ->
            currentStatus = when (status) {
                SSHManager.Status.CONNECTING -> "正在连接…"
                SSHManager.Status.CONNECTED -> "已连接"
                SSHManager.Status.DISCONNECTED -> "已断开"
                SSHManager.Status.ERROR -> "连接断开"
            }
            updateNotification()
        }
        sshManager.addStatusListener(notifyStatusListener!!)
        sshManager.connect(conn, cols, rows)
    }

    private fun updateNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, TerminalActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("connection_id", connId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(connName)
            .setContentText(currentStatus)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    var connId: Long = -1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        connId = intent?.getLongExtra("connection_id", -1) ?: connId
        return START_STICKY
    }

    override fun onDestroy() {
        sshManager.destroy()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "ssh_session"
        const val NOTIFICATION_ID = 1001
    }
}
