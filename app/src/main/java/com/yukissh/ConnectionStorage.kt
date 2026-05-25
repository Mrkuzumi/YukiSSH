package com.yukissh

import android.content.Context
import org.json.JSONArray

class ConnectionStorage(context: Context) {

    private val prefs = context.getSharedPreferences("connections", Context.MODE_PRIVATE)

    fun loadAll(): List<SSHConnection> {
        val json = prefs.getString("list", "[]") ?: "[]"
        val arr = JSONArray(json)
        val result = mutableListOf<SSHConnection>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(
                SSHConnection(
                    id = obj.getLong("id"),
                    name = obj.getString("name"),
                    host = obj.getString("host"),
                    port = obj.getInt("port"),
                    username = obj.getString("username"),
                    password = obj.getString("password")
                )
            )
        }
        return result
    }

    fun saveAll(list: List<SSHConnection>) {
        val arr = JSONArray()
        for (conn in list) {
            val obj = org.json.JSONObject()
            obj.put("id", conn.id)
            obj.put("name", conn.name)
            obj.put("host", conn.host)
            obj.put("port", conn.port)
            obj.put("username", conn.username)
            obj.put("password", conn.password)
            arr.put(obj)
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    fun save(conn: SSHConnection) {
        val list = loadAll().toMutableList()
        val idx = list.indexOfFirst { it.id == conn.id }
        if (idx >= 0) list[idx] = conn else list.add(conn)
        saveAll(list)
    }

    fun delete(id: Long) {
        saveAll(loadAll().filter { it.id != id })
    }
}
