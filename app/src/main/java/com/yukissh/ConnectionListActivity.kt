package com.yukissh

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ConnectionListActivity : AppCompatActivity() {

    private lateinit var homeFragment: HomeFragment
    private lateinit var aboutFragment: AboutFragment
    private lateinit var fabAdd: ExtendedFloatingActionButton
    private var downloadId: Long = -1
    private var latestApkUrl = ""
    private var latestVersion = ""

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
            if (id == downloadId) {
                val query = DownloadManager.Query().setFilterById(id)
                val dm = context?.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val cursor = dm.query(query)
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        val uri = dm.getUriForDownloadedFile(id)
                        installApk(uri)
                    }
                }
                cursor.close()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection_list)

        homeFragment = HomeFragment()
        aboutFragment = AboutFragment()
        fabAdd = findViewById(R.id.fabAdd)

        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, aboutFragment, "about").hide(aboutFragment)
            .add(R.id.fragmentContainer, homeFragment, "home")
            .commit()

        fabAdd.setOnClickListener {
            startActivity(Intent(this, EditConnectionActivity::class.java))
        }

        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showFragment(homeFragment)
                    fabAdd.show()
                    true
                }
                R.id.nav_about -> {
                    showFragment(aboutFragment)
                    fabAdd.hide()
                    true
                }
                else -> false
            }
        }

        registerReceiver(
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // 启动时自动检查更新
        checkUpdateOnStart()
    }

    private fun checkUpdateOnStart() {
        MainScope().launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    fetchLatestRelease()
                }
                if (result == null) return@launch
                val (version, url) = result
                val currentVersion = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
                } catch (_: Exception) { "1.0" }

                if (isNewer(version, currentVersion)) {
                    latestVersion = version
                    latestApkUrl = url
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(version)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun showUpdateDialog(version: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("发现新版本 V$version")
            .setMessage("GitHub Releases 上有新版本可用，是否立即下载更新？")
            .setPositiveButton("立马下载更新") { _, _ ->
                downloadApk(latestApkUrl, latestVersion)
            }
            .setNegativeButton("继续使用") { _, _ -> }
            .setCancelable(true)
            .show()
    }

    private fun fetchLatestRelease(): Pair<String, String>? {
        return try {
            val url = URL("https://api.github.com/repos/Mrkuzumi/YukiSSH/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val code = conn.responseCode
            if (code != 200) return null
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            val tag = obj.getString("tag_name")
                .trimStart('v', 'V')
                .trimStart('-', '_')
            val assets = obj.getJSONArray("assets")
            var apkUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }
            if (apkUrl.isEmpty()) null else Pair(tag, apkUrl)
        } catch (_: Exception) {
            null
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val lParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val cParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(lParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val lv = lParts.getOrElse(i) { 0 }
            val cv = cParts.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }

    private fun downloadApk(url: String, version: String) {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri).apply {
            setTitle("YukiSSH V$version")
            setDescription("正在下载更新…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "YukiSSH_V$version.apk")
        }
        downloadId = dm.enqueue(request)
    }

    private fun installApk(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                AlertDialog.Builder(this)
                    .setTitle("需要安装权限")
                    .setMessage("请允许从未知来源安装应用后重试")
                    .setPositiveButton("去设置") { _, _ ->
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    }
                    .setNegativeButton("取消", null)
                    .show()
                return
            }
        }
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(downloadReceiver) } catch (_: Exception) {}
    }

    private fun showFragment(fragment: Fragment) {
        val ft = supportFragmentManager.beginTransaction()
        if (fragment === homeFragment) {
            ft.hide(aboutFragment).show(homeFragment)
        } else {
            ft.hide(homeFragment).show(aboutFragment)
        }
        ft.commit()
    }
}
