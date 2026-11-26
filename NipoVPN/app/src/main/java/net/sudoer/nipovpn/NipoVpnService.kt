package net.sudoer.nipo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process as AndroidProcess
import android.util.Log
import androidx.core.app.NotificationCompat
import hev.htproxy.TProxyService
import java.io.File

// A VpnService in both modes; only VPN mode opens a TUN device.
class NipoVpnService : VpnService() {
    private var nipoProcess: Process? = null
    private var tun: ParcelFileDescriptor? = null
    private var logTailThread: Thread? = null
    private var statsThread: Thread? = null

    @Volatile
    private var tailRunning = false

    @Volatile
    private var statsRunning = false

    // Bumped per start so a stale log line can't stop a newer session.
    // Guarded by the service lock, like nipoProcess.
    private var session = 0

    override fun onCreate() {
        super.onCreate()
        goForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopNipoVpn()
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground()
        startNipoVpn()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopNipoVpn()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // Another VPN took over, or the user disconnected this one in system settings.
    override fun onRevoke() {
        LogManager.append("VPN was revoked by the system; stopping")
        stopNipoVpn()
        super.onRevoke()
    }

    // Android 15+ enforces a total execution-time limit for "dataSync" foreground
    // services and calls this when it's hit, expecting us to stop within seconds.
    // Ignoring it is what causes ForegroundServiceDidNotStopInTimeException on
    // long-running VPN sessions.
    override fun onTimeout(startId: Int, fgsType: Int) {
        LogManager.append("Foreground service execution time limit reached; stopping")
        stopNipoVpn()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }


    @Synchronized
    private fun startNipoVpn() {
        if (nipoProcess != null) {
            LogManager.append("NipoVPN is already running")
            return
        }
        session++

        try {
            val profile = loadActiveProfile(this)

            if (profile == null) {
                LogManager.append("No active profile selected")
                return
            }

            val mode = loadConnectionMode(filesDir)
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

            if (mode == ConnectionMode.VPN) startTunnel(profile.config.normalized().listenPort.trim())

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

            // The core may already be up when the tunnel fails.
            stopNipoVpn()
            stopSelf()
        }
    }

    // Sends every other app's traffic to the core's SOCKS5 listener.
    private fun startTunnel(socksPort: String) {
        val builder = Builder()
            .setSession("NipoVPN")
            .setMtu(TUN_MTU)
            .addAddress(TUN_IPV4, 32)
            .addAddress(TUN_IPV6, 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer(MAPDNS_ADDRESS)
            // The core is our child process and cannot protect() its socket, so the whole
            // app stays outside the tunnel; otherwise its link to the server would loop.
            .addDisallowedApplication(packageName)
        // A VPN counts as metered unless told to follow the network under it.
        if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false)

        val fd = builder.establish() ?: error("VPN permission is not granted")
        tun = fd

        val config = File(filesDir, "tunnel.yaml")
        config.writeText(tunnelYaml(socksPort))
        check(TProxyService.TProxyStartService(config.absolutePath, fd.fd)) { "the tunnel did not start" }
        LogManager.append("VPN tunnel up: all apps -> SOCKS5 127.0.0.1:$socksPort (TCP only)")
    }

    // Retrying can't help a refused account, so stop and show why.
    @Synchronized
    private fun onServerRefused(refusal: Refusal, fromSession: Int) {
        // Stale session, or a refusal already handled.
        if (fromSession != session || nipoProcess == null) return
        LogManager.append(refusal.message)
        stopNipoVpn()
        ConnectionStatus.refused(refusal)
        stopSelf()
    }

    @Synchronized
    private fun stopNipoVpn() {
        // Already stopped, e.g. onDestroy right after a refusal.
        if (nipoProcess == null && logTailThread == null && statsThread == null && tun == null) return
        try {
            // Tunnel first, so nothing is still reading the TUN device when it closes.
            tun?.let {
                try {
                    TProxyService.TProxyStopService()
                } finally {
                    it.close()
                    tun = null
                }
            }

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
        val tailSession = session

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
                                        // The core flushes its log file on each write but not stdout, so check here.
                                        coreRefusal(line)?.let { onServerRefused(it, tailSession) }
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

    // API 34+ wants one declared type per start. A VPN the user has consented to may run as
    // "systemExempted", which has no time limit; the proxy keeps "dataSync" as before.
    private fun goForeground() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT < 34) {
            startForeground(1, notification)
            return
        }
        val vpn = loadConnectionMode(filesDir) == ConnectionMode.VPN && prepare(this) == null
        startForeground(
            1,
            notification,
            if (vpn) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
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

    companion object {
        // While the tunnel is up the system stays bound to this service, so stopService()
        // alone would not end it; the activity sends this instead.
        const val ACTION_STOP = "net.sudoer.nipo.STOP"
    }
}
