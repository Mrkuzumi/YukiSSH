package com.yukissh

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class EditConnectionActivity : AppCompatActivity() {

    private lateinit var storage: ConnectionStorage
    private var editingId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_connection)

        storage = ConnectionStorage(this)
        editingId = intent.getLongExtra("connection_id", -1)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etHost = findViewById<TextInputEditText>(R.id.etHost)
        val etPort = findViewById<TextInputEditText>(R.id.etPort)
        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)

        if (editingId > 0) {
            toolbar.title = getString(R.string.edit_connection)
            val conn = storage.loadAll().find { it.id == editingId }
            if (conn != null) {
                etName.setText(conn.name)
                etHost.setText(conn.host)
                etPort.setText(conn.port.toString())
                etUsername.setText(conn.username)
                etPassword.setText(conn.password)
            }
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave).setOnClickListener {
            val name = etName.text.toString().trim()
            val host = etHost.text.toString().trim()
            val port = etPort.text.toString().trim().toIntOrNull() ?: 22
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString()

            if (host.isEmpty()) {
                Toast.makeText(this, "请输入主机地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (username.isEmpty()) {
                Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val conn = SSHConnection(
                id = if (editingId > 0) editingId else System.currentTimeMillis(),
                name = name,
                host = host,
                port = port,
                username = username,
                password = password
            )
            storage.save(conn)
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
