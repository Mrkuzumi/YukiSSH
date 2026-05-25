package com.yukissh

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class ConnectionListActivity : AppCompatActivity() {

    private lateinit var storage: ConnectionStorage
    private lateinit var adapter: ConnectionAdapter
    private var connections = listOf<SSHConnection>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection_list)

        storage = ConnectionStorage(this)

        adapter = ConnectionAdapter { conn ->
            val intent = Intent(this, TerminalActivity::class.java).apply {
                putExtra("connection_id", conn.id)
            }
            startActivity(intent)
        }

        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@ConnectionListActivity)
            adapter = this@ConnectionListActivity.adapter
        }

        findViewById<com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton>(R.id.fabAdd)
            .setOnClickListener {
                startActivity(Intent(this, EditConnectionActivity::class.java))
            }
    }

    override fun onResume() {
        super.onResume()
        connections = storage.loadAll()
        adapter.submitList(connections)
    }

    private inner class ConnectionAdapter(
        private val onConnect: (SSHConnection) -> Unit
    ) : RecyclerView.Adapter<ConnectionAdapter.VH>() {

        private var list = listOf<SSHConnection>()

        fun submitList(l: List<SSHConnection>) {
            list = l
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_connection, parent, false) as MaterialCardView
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(list[position])
        }

        override fun getItemCount() = list.size

        inner class VH(private val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
            private val tvName = card.findViewById<TextView>(R.id.tvName)
            private val tvDetail = card.findViewById<TextView>(R.id.tvDetail)

            fun bind(conn: SSHConnection) {
                tvName.text = conn.name.ifEmpty { conn.host }
                tvDetail.text = "${conn.username}@${conn.host}:${conn.port}"
                card.setOnClickListener { onConnect(conn) }
                card.setOnLongClickListener {
                    showOptions(conn)
                    true
                }
            }
        }
    }

    private fun showOptions(conn: SSHConnection) {
        val items = arrayOf("编辑", "删除")
        AlertDialog.Builder(this)
            .setTitle(conn.name.ifEmpty { conn.host })
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, EditConnectionActivity::class.java).apply {
                            putExtra("connection_id", conn.id)
                        }
                        startActivity(intent)
                    }
                    1 -> {
                        AlertDialog.Builder(this)
                            .setTitle("确认删除")
                            .setMessage("确定要删除连接 \"${conn.name.ifEmpty { conn.host }}\" 吗？")
                            .setPositiveButton("删除") { _, _ ->
                                storage.delete(conn.id)
                                onResume()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
            }
            .show()
    }
}
