package net.sudoer.nipo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.app.Service
import android.os.IBinder
import android.net.TrafficStats
import android.os.Process as AndroidProcess
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File

class NipoVpnService : Service() {
    private var nipoProcess: Process? = null
    private var logTailThread: Thread? = null
    private var statsThread: Thread? = null

    @Volatile
    private var tailRunning = false

    @Volatile
    private var statsRunning = false

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        startNipoVpn()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopNipoVpn()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null


    private fun startNipoVpn() {
        if (nipoProcess != null) {
            LogManager.append("NipoVPN is already running")
            return
        }

        try {
            val profile = loadActiveProfile(this)

            if (profile == null) {
                LogManager.append("No active profile selected")
                return
            }

            val configFile = generateConfigFile(this, profile.config)

            val logDir = File(filesDir, "logs")
            logDir.mkdirs()

            val logFile = File(logDir, "nipovpn.log")
            
            // Clear old logs on each start
            logFile.writeText("")
            
            logFile.appendText(
                "\n\n===== Starting NipoVPN: ${profile.name} =====\n"
            )

            LogManager.append("===== Starting NipoVPN: ${profile.name} =====")
            LogManager.append("Config: ${configFile.absolutePath}")
            LogManager.append("Log: ${logFile.absolutePath}")

            startTailLogFile(logFile)

            val binaryFile = findNipoBinary()
            LogManager.append("Binary: ${binaryFile.absolutePath}")

            nipoProcess = ProcessBuilder(
                binaryFile.absolutePath,
                "agent",
                configFile.absolutePath
            )
                .redirectErrorStream(true)
                .start()

            Thread {
                try {
                    nipoProcess?.inputStream
                        ?.bufferedReader()
                        ?.forEachLine { line ->
                            Log.i("NipoVPN", line)
                            LogManager.append(line)
                            logFile.appendText("$line\n")
                        }
                } catch (e: Exception) {
                    val msg = "stdout reader failed: ${e.message}"
                    Log.e("NipoVPN", msg, e)
                    LogManager.append(msg)
                    logFile.appendText("$msg\n")
                }
            }.start()

            ConnectionStatus.started()
            startStatsSampler()
        } catch (e: Exception) {
            ConnectionStatus.stopped()
            val msg = "Failed to start NipoVPN: ${e.message}"
            Log.e("NipoVPN", msg, e)
            LogManager.append(msg)

            val logFile = File(filesDir, "logs/nipovpn.log")
            logFile.parentFile?.mkdirs()
            logFile.appendText("$msg\n")
        }
    }

    private fun stopNipoVpn() {
        try {
            tailRunning = false
            logTailThread?.interrupt()
            logTailThread = null

            statsRunning = false
            statsThread?.interrupt()
            statsThread = null
            ConnectionStatus.stopped()

            nipoProcess?.destroy()
            nipoProcess = null

            val msg = "===== Stopped NipoVPN ====="
            File(filesDir, "logs/nipovpn.log").appendText("$msg\n")

            LogManager.append(msg)
            Log.i("NipoVPN", "Process stopped")
        } catch (e: Exception) {
            val msg = "Failed to stop process: ${e.message}"
            Log.e("NipoVPN", msg, e)
            LogManager.append(msg)
        }
    }

    private fun startStatsSampler() {
        statsRunning = false
        statsThread?.interrupt()

        statsRunning = true

        statsThread = Thread {
            val uid = AndroidProcess.myUid()
            var lastRx = TrafficStats.getUidRxBytes(uid)
            var lastTx = TrafficStats.getUidTxBytes(uid)
            var lastSampleMs = System.currentTimeMillis()

            while (statsRunning) {
                try {
                    Thread.sleep(1000)

                    val rx = TrafficStats.getUidRxBytes(uid)
                    val tx = TrafficStats.getUidTxBytes(uid)
                    val now = System.currentTimeMillis()
                    val elapsedSec = (now - lastSampleMs).coerceAtLeast(1L) / 1000f

                    // UNSUPPORTED (-1) or no prior baseline → report 0 (UI shows "—").
                    val downKBs = if (rx < 0 || lastRx < 0) 0f
                        else ((rx - lastRx).coerceAtLeast(0L) / 1024f) / elapsedSec
                    val upKBs = if (tx < 0 || lastTx < 0) 0f
                        else ((tx - lastTx).coerceAtLeast(0L) / 1024f) / elapsedSec

                    ConnectionStatus.updateThroughput(downKBs, upKBs)

                    lastRx = rx
                    lastTx = tx
                    lastSampleMs = now
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e("NipoVPN", "stats sampler error: ${e.message}", e)
                }
            }
        }

        statsThread?.start()
    }

    private fun startTailLogFile(logFile: File) {
        tailRunning = false
        logTailThread?.interrupt()

        tailRunning = true

        logTailThread = Thread {
            var lastSize = 0L

            while (tailRunning) {
                try {
                    if (logFile.exists()) {
                        val currentSize = logFile.length()

                        if (currentSize < lastSize) {
                            lastSize = 0L
                        }

                        if (currentSize > lastSize) {
                            logFile.inputStream().use { input ->
                                input.skip(lastSize)
                                input.bufferedReader().forEachLine { line ->
                                    if (line.isNotBlank()) {
                                        LogManager.append(line)
                                    }
                                }
                            }

                            lastSize = currentSize
                        }
                    }

                    Thread.sleep(500)
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    LogManager.append("Log tail error: ${e.message}")

                    try {
                        Thread.sleep(1000)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }

        logTailThread?.start()
    }

    private fun findNipoBinary(): File {
        val direct = File(applicationInfo.nativeLibraryDir, "libnipovpn_exec.so")

        if (direct.exists()) {
            return direct
        }

        val parent = File(applicationInfo.nativeLibraryDir).parentFile

        parent?.walkTopDown()?.forEach { file ->
            if (file.name == "libnipovpn_exec.so") {
                return file
            }
        }

        throw IllegalStateException(
            "libnipovpn_exec.so not found.\n" +
                "nativeLibraryDir=${applicationInfo.nativeLibraryDir}"
        )
    }

    private fun createNotification(): Notification {
        val channelId = "nipovpn"

        val channel = NotificationChannel(
            channelId,
            "NipoVPN",
            NotificationManager.IMPORTANCE_LOW
        )

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("NipoVPN")
            .setContentText("VPN is running")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .build()
    }
}
