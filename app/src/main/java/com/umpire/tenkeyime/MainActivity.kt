
package com.umpire.tenkeyime

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var updateButton: Button

    private var latestApkUrl: String? = null
    private var latestVersion: String? = null
    private var downloadedApk: File? = null

    private val releaseApi =
        "https://api.github.com/repos/parkpyh/TenKeyIME/releases/latest"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 기존 블루투스 자동 전환 서비스 시작
        startForegroundService(
            Intent(this, KeyboardSwitchService::class.java)
        )

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Dual TenKey"
            textSize = 26f
        }

        val versionText = TextView(this).apply {
            text = "현재 버전: ${currentVersionName()}"
            textSize = 16f
        }

        statusText = TextView(this).apply {
            text = "GitHub에서 최신 버전을 확인할 수 있습니다."
            textSize = 16f
        }

        updateButton = Button(this).apply {
            text = "업데이트 확인"
            setOnClickListener {
                when {
                    downloadedApk != null ->
                        openInstaller(downloadedApk!!)

                    latestApkUrl != null ->
                        downloadApk(latestApkUrl!!)

                    else ->
                        checkForUpdates()
                }
            }
        }

        layout.addView(title)
        layout.addView(versionText)
        layout.addView(statusText)
        layout.addView(updateButton)

        setContentView(layout)

        // 앱을 열면 최신 버전을 자동 확인
        checkForUpdates()
    }

    private fun currentVersionName(): String {
        return packageManager
            .getPackageInfo(packageName, 0)
            .versionName ?: "0"
    }

    private fun checkForUpdates() {
        latestApkUrl = null
        latestVersion = null
        downloadedApk = null

        statusText.text = "최신 버전 확인 중..."
        updateButton.isEnabled = false

        thread {
            try {
                val connection =
                    URL(releaseApi).openConnection()
                            as HttpURLConnection

                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty(
                    "Accept",
                    "application/vnd.github+json"
                )

                val response = try {
                    if (connection.responseCode !in 200..299) {
                        error(
                            "GitHub 응답: ${connection.responseCode}"
                        )
                    }

                    connection.inputStream.bufferedReader()
                        .use { it.readText() }
                } finally {
                    connection.disconnect()
                }

                val release = JSONObject(response)
                val version = release
                    .getString("tag_name")
                    .removePrefix("v")

                val assets = release.getJSONArray("assets")
                var apkUrl: String? = null

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)

                    if (asset.getString("name") == "app-debug.apk") {
                        apkUrl = asset.getString(
                            "browser_download_url"
                        )
                        break
                    }
                }

                runOnUiThread {
                    updateButton.isEnabled = true

                    val current = currentVersionName()

                    when {
                        compareVersions(version, current) <= 0 -> {
                            statusText.text =
                                "최신 버전($current)을 사용 중입니다."
                            updateButton.text = "다시 확인"
                        }

                        apkUrl == null -> {
                            statusText.text =
                                "새 버전 $version 발견\n" +
                                        "다운로드할 APK가 없습니다."
                            updateButton.text = "다시 확인"
                        }

                        else -> {
                            latestVersion = version
                            latestApkUrl = apkUrl

                            statusText.text =
                                "새 버전 $version 발견!\n" +
                                        "현재 버전: $current"

                            updateButton.text =
                                "v$version 다운로드"
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text =
                        "업데이트 확인 실패: ${e.message}"
                    updateButton.text = "다시 확인"
                    updateButton.isEnabled = true
                }
            }
        }
    }

    private fun downloadApk(url: String) {
        statusText.text = "APK 다운로드 중..."
        updateButton.isEnabled = false

        thread {
            try {
                val directory = File(cacheDir, "updates")
                directory.mkdirs()

                val apk = File(directory, "TenKeyIME-update.apk")
                val temporary = File(
                    directory,
                    "TenKeyIME-update.apk.part"
                )

                val connection =
                    URL(url).openConnection()
                            as HttpURLConnection

                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15000
                connection.readTimeout = 30000

                try {
                    if (connection.responseCode !in 200..299) {
                        error(
                            "다운로드 응답: ${connection.responseCode}"
                        )
                    }

                    connection.inputStream.use { input ->
                        temporary.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } finally {
                    connection.disconnect()
                }

                if (!temporary.renameTo(apk)) {
                    error("APK 파일 저장 실패")
                }

                runOnUiThread {
                    downloadedApk = apk
                    statusText.text =
                        "v${latestVersion} 다운로드 완료.\n" +
                                "설치 버튼을 눌러주세요."
                    updateButton.text = "설치하기"
                    updateButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text =
                        "다운로드 실패: ${e.message}"
                    updateButton.text = "다시 다운로드"
                    updateButton.isEnabled = true
                }
            }
        }
    }

    private fun openInstaller(apk: File) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            statusText.text =
                "Dual TenKey의 '알 수 없는 앱 설치'를 허용한 뒤 " +
                        "설치 버튼을 다시 눌러주세요."

            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            )

            startActivity(intent)
            return
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                apk
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    apkUri,
                    "application/vnd.android.package-archive"
                )
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            statusText.text =
                "APK 설치 프로그램을 찾을 수 없습니다."
        } catch (e: Exception) {
            statusText.text =
                "설치 화면 실행 실패: ${e.message}"
        }
    }

    private fun compareVersions(
        first: String,
        second: String
    ): Int {
        val a = first.split(".")
        val b = second.split(".")

        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrNull(i)?.toIntOrNull() ?: 0
            val y = b.getOrNull(i)?.toIntOrNull() ?: 0

            if (x != y) {
                return x.compareTo(y)
            }
        }

        return 0
    }
}
