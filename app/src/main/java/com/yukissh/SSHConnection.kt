package com.yukissh

data class SSHConnection(
    val id: Long = System.currentTimeMillis(),
    val name: String = "",
    val host: String = "",
    val port: Int = 22,
    val username: String = "root",
    val password: String = ""
)
