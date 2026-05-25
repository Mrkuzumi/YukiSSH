package com.yukissh

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class HomeFragment : Fragment() {

    private lateinit var storage: ConnectionStorage
    private lateinit var adapter: ConnectionAdapter
    private var connections = listOf<SSHConnection>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        storage = ConnectionStorage(requireContext())

        adapter = ConnectionAdapter { conn ->
            val intent = Intent(requireContext(), TerminalActivity::class.java).apply {
                putExtra("connection_id", conn.id)
            }
            startActivity(intent)
        }

        view.findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HomeFragment.adapter
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
        AlertDialog.Builder(requireContext())
            .setTitle(conn.name.ifEmpty { conn.host })
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(requireContext(), EditConnectionActivity::class.java).apply {
                            putExtra("connection_id", conn.id)
                        }
                        startActivity(intent)
                    }
                    1 -> {
                        AlertDialog.Builder(requireContext())
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
