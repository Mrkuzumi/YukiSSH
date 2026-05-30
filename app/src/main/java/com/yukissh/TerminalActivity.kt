package com.yukissh

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class TerminalActivity : AppCompatActivity() {

    private var sshManager: SSHManager? = null
    private var sshService: SSHService? = null
    private lateinit var terminalView: TerminalView
    private lateinit var hiddenInput: EditText
    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var statusDot: View
    private lateinit var statusPill: LinearLayout
    private lateinit var shortcutBar: HorizontalScrollView
    private var connection: SSHConnection? = null
    private var clearing = false
    private var isReconnect = false
    private var started = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SSHService.SSHBinder
            sshManager = binder.getManager()
            sshService = binder.getService()
            setupSSHCallbacks()
            if (sshManager?.isRunning != true) {
                sshService?.startConnection(connection!!, terminalView.cols, terminalView.rows)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sshManager = null
            sshService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        terminalView = findViewById(R.id.terminalView)
        hiddenInput = findViewById(R.id.hiddenInput)
        tvTitle = findViewById(R.id.tvTitle)
        tvStatus = findViewById(R.id.tvStatus)
        statusDot = findViewById(R.id.statusDot)
        statusPill = findViewById(R.id.statusPill)
        shortcutBar = findViewById(R.id.shortcutBar)

        val storage = ConnectionStorage(this)
        val connId = intent.getLongExtra("connection_id", -1)
        connection = storage.loadAll().find { it.id == connId }
        if (connection == null) {
            finish()
            return
        }

        tvTitle.text = connection!!.name.ifEmpty { "${connection!!.username}@${connection!!.host}" }

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
            sshManager?.disconnect()
            unbindService(serviceConnection)
            stopService(Intent(this, SSHService::class.java))
            finish()
        }

        buildShortcuts()

        findViewById<ImageButton>(R.id.btnFontDown).setOnClickListener {
            terminalView.changeFontSize(-1f)
            MainScope().launch(Dispatchers.IO) {
                sshManager?.sendResize(terminalView.cols, terminalView.rows)
            }
        }

        findViewById<ImageButton>(R.id.btnFontUp).setOnClickListener {
            terminalView.changeFontSize(1f)
            MainScope().launch(Dispatchers.IO) {
                sshManager?.sendResize(terminalView.cols, terminalView.rows)
            }
        }

        findViewById<ImageButton>(R.id.btnCopy).setOnClickListener {
            val text = terminalView.getText()
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
            Toast.makeText(this, "已复制终端内容", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener {
            isReconnect = true
            sshManager?.disconnect()
            sshService?.startConnection(connection!!, terminalView.cols, terminalView.rows)
        }

        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (clearing) return
                s?.let {
                    if (it.isNotEmpty()) {
                        val text = it.toString()
                        for (ch in text) {
                            if (ch == '\n') sshManager?.send(byteArrayOf('\r'.code.toByte()))
                            else sshManager?.send(ch.toString().toByteArray())
                        }
                        clearing = true
                        it.clear()
                        clearing = false
                    }
                }
            }
        })

        hiddenInput.setOnEditorActionListener { _, actionId, event ->
            if (event != null && event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        sshManager?.send(byteArrayOf('\r'.code.toByte()))
                        true
                    }
                    else -> false
                }
            } else if (actionId == EditorInfo.IME_ACTION_DONE
                || actionId == EditorInfo.IME_ACTION_SEND
                || actionId == EditorInfo.IME_ACTION_NEXT
                || actionId == EditorInfo.IME_ACTION_GO) {
                sshManager?.send(byteArrayOf('\r'.code.toByte()))
                true
            } else false
        }

        hiddenInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        sshManager?.send(byteArrayOf('\r'.code.toByte()))
                        true
                    }
                    KeyEvent.KEYCODE_DEL -> {
                        sshManager?.send(byteArrayOf(0x7F))
                        true
                    }
                    KeyEvent.KEYCODE_TAB -> {
                        sshManager?.send(byteArrayOf('\t'.code.toByte()))
                        true
                    }
                    else -> false
                }
            } else false
        }

        terminalView.setOnClickListener {
            hiddenInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT)
        }

        // Request notification permission on Android 13+ then start service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
                return
            }
        }
        startAndBind()
    }

    private fun startAndBind() {
        if (started) return
        started = true
        // Request battery optimization exemption to prevent Doze from killing network
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
        val conn = connection ?: return
        val serviceIntent = Intent(this, SSHService::class.java).apply {
            putExtra("connection_id", conn.id)
            putExtra("connection_name", conn.name.ifEmpty { "${conn.username}@${conn.host}" })
        }
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFY) {
            startAndBind()
        }
    }

    private val outputListener: (ByteArray, Int) -> Unit = { data, len ->
        terminalView.write(data, len)
    }

    private val statusListener: (SSHManager.Status) -> Unit = { status ->
        runOnUiThread {
            when (status) {
                SSHManager.Status.CONNECTING -> {
                    tvStatus.text = getString(R.string.connecting)
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connecting_text))
                    statusDot.setBackgroundResource(R.drawable.dot_yellow)
                    setPillColor(statusPill, R.color.status_connecting_bg)
                }
                SSHManager.Status.CONNECTED -> {
                    tvStatus.text = getString(R.string.connected)
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected_text))
                    statusDot.setBackgroundResource(R.drawable.dot_green)
                    setPillColor(statusPill, R.color.status_connected_bg)
                    if (!isReconnect) {
                        terminalView.reset()
                    }
                    isReconnect = false
                }
                SSHManager.Status.DISCONNECTED -> {
                    tvStatus.text = getString(R.string.disconnected)
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected_text))
                    statusDot.setBackgroundResource(R.drawable.dot_red)
                    setPillColor(statusPill, R.color.status_disconnected_bg)
                }
                SSHManager.Status.ERROR -> {
                    tvStatus.text = "连接断开"
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected_text))
                    statusDot.setBackgroundResource(R.drawable.dot_red)
                    setPillColor(statusPill, R.color.status_disconnected_bg)
                }
            }
        }
    }

    private fun setupSSHCallbacks() {
        val mgr = sshManager ?: return
        mgr.addOutputListener(outputListener)
        mgr.addStatusListener(statusListener)
    }

    private fun removeSSHCallbacks() {
        val mgr = sshManager ?: return
        mgr.removeOutputListener(outputListener)
        mgr.removeStatusListener(statusListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        removeSSHCallbacks()
        unbindService(serviceConnection)
    }

    private fun setPillColor(pill: LinearLayout, colorRes: Int) {
        val bg = pill.background as? GradientDrawable
        bg?.setColor(ContextCompat.getColor(this, colorRes))
    }

    private fun buildShortcuts() {
        val container = findViewById<LinearLayout>(R.id.shortcutContainer)
        val keys = listOf(
            "Esc" to byteArrayOf(0x1B),
            "Tab" to byteArrayOf(0x09),
            "Ctrl+C" to byteArrayOf(0x03),
            "Ctrl+Z" to byteArrayOf(0x1A),
            "Ctrl+L" to byteArrayOf(0x0C),
            "←" to byteArrayOf(0x1B, 0x5B, 0x44),
            "↓" to byteArrayOf(0x1B, 0x5B, 0x42),
            "↑" to byteArrayOf(0x1B, 0x5B, 0x41),
            "→" to byteArrayOf(0x1B, 0x5B, 0x43),
            "Home" to byteArrayOf(0x1B, 0x5B, 0x48),
            "End" to byteArrayOf(0x1B, 0x5B, 0x46),
            "PgUp" to byteArrayOf(0x1B, 0x5B, 0x35, 0x7E),
            "PgDn" to byteArrayOf(0x1B, 0x5B, 0x36, 0x7E),
            "/" to byteArrayOf(0x2F),
            "|" to byteArrayOf(0x7C),
            "-" to byteArrayOf(0x2D),
            "_" to byteArrayOf(0x5F),
            "[" to byteArrayOf(0x5B),
            "]" to byteArrayOf(0x5D),
            "(" to byteArrayOf(0x28),
            ")" to byteArrayOf(0x29),
        )

        val padH = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
        val padV = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics).toInt()

        for ((label, data) in keys) {
            val btn = Button(this).apply {
                text = label
                textSize = 12f
                setPadding(padH, padV, padH, padV)
                setTextColor(ContextCompat.getColor(this@TerminalActivity, R.color.terminal_on_surface))
                setBackgroundColor(ContextCompat.getColor(this@TerminalActivity, R.color.terminal_toolbar))
                setOnClickListener { sshManager?.send(data) }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics).toInt()
            }
            btn.layoutParams = lp
            container.addView(btn)
        }
    }

    companion object {
        private const val REQ_NOTIFY = 100
    }
}
