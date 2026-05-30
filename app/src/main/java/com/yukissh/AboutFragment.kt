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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AboutFragment : Fragment() {

    private var downloadId: Long = -1
    private var currentVersion = ""
    private var latestVersion = ""
    private lateinit var tvUpdateHint: TextView
    private lateinit var tvVersion: TextView

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
                    } else {
                        tvUpdateHint.text = "下载失败，请重试"
                    }
                }
                cursor.close()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_about, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvUpdateHint = view.findViewById(R.id.tvUpdateHint)
        tvVersion = view.findViewById(R.id.tvVersion)

        try {
            val pkgInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            currentVersion = pkgInfo.versionName ?: "1.0"
            tvVersion.text = "版本 V$currentVersion"
        } catch (_: Exception) {
            currentVersion = "1.0"
            tvVersion.text = "版本 V1.0"
        }

        view.findViewById<View>(R.id.cardSource).setOnClickListener {
            openUrl("https://github.com/Mrkuzumi/YukiSSH")
        }

        view.findViewById<View>(R.id.cardAuthor).setOnClickListener {
            openUrl("https://github.com/Mrkuzumi")
        }

        view.findViewById<View>(R.id.cardUpdate).setOnClickListener {
            checkUpdate()
        }

        requireContext().registerReceiver(
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun checkUpdate() {
        tvUpdateHint.text = "正在检查更新…"
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    fetchLatestRelease()
                }
                if (result == null) {
                    tvUpdateHint.text = "检查更新失败，请检查网络"
                    return@launch
                }
                val (version, url) = result
                latestVersion = version
                if (version == currentVersion) {
                    tvUpdateHint.text = "已是最新版本 V$currentVersion"
                } else if (isNewer(version, currentVersion)) {
                    tvUpdateHint.text = "发现新版本 V$version，正在下载…"
                    downloadApk(url, version)
                } else {
                    tvUpdateHint.text = "已是最新版本 V$currentVersion"
                }
            } catch (e: Exception) {
                tvUpdateHint.text = "检查更新失败：${e.localizedMessage}"
            }
        }
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
            if (code != 200) {
                android.util.Log.e("AboutFragment", "GitHub API returned $code")
                return null
            }
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            val tag = obj.getString("tag_name")
                .trimStart('v', 'V')
                .trimStart('-', '_')
            val assets = obj.getJSONArray("assets")
            var apkUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }
            if (apkUrl.isEmpty()) {
                android.util.Log.e("AboutFragment", "No APK asset in release $tag")
                return null
            }
            Pair(tag, apkUrl)
        } catch (e: Exception) {
            android.util.Log.e("AboutFragment", "fetchLatestRelease failed", e)
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
        val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
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
            if (!requireContext().packageManager.canRequestPackageInstalls()) {
                tvUpdateHint.text = "请允许安装未知应用后，点击此处重试"
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
                return
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        tvUpdateHint.text = "下载完成，点击安装 V$latestVersion"
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            requireContext().unregisterReceiver(downloadReceiver)
        } catch (_: Exception) {}
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}
