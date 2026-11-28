package hev.htproxy

// JNI binding for the prebuilt libhev-socks5-tunnel.so (upstream 2.20.0). On load the library
// registers its natives on this exact class and these four signatures, so the package, class and
// method names cannot change without rebuilding it.
object TProxyService {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic
    external fun TProxyStartService(configPath: String, fd: Int): Boolean

    @JvmStatic
    external fun TProxyStopService(): Boolean

    @JvmStatic
    external fun TProxyIsRunning(): Boolean

    @JvmStatic
    external fun TProxyGetStats(): LongArray
}
