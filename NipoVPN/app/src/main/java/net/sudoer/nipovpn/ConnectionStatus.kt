package net.sudoer.nipovpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Connection state shared between [NipoVpnService] and the UI.
 *
 * Mirrors the [LogManager] pattern: the service and the Compose UI run in the
 * same process, so a singleton [StateFlow] is the simplest cross-component
 * channel (no bound service or broadcast needed).
 *
 * - [startedAtMillis] drives a true uptime timer that survives activity
 *   recreation (the UI computes elapsed = now - startedAtMillis).
 * - [downKBs] / [upKBs] are sampled app-UID-wide via TrafficStats, so they
 *   include the app's own minor traffic and read 0 on devices where per-UID
 *   stats are unsupported.
 */
data class ConnectionState(
    val running: Boolean = false,
    val startedAtMillis: Long? = null,
    val downKBs: Float = 0f,
    val upKBs: Float = 0f,
)

object ConnectionStatus {
    private val _state = MutableStateFlow(ConnectionState())
    val state: StateFlow<ConnectionState> = _state

    @Synchronized
    fun started() {
        _state.value = ConnectionState(
            running = true,
            startedAtMillis = System.currentTimeMillis(),
            downKBs = 0f,
            upKBs = 0f,
        )
    }

    @Synchronized
    fun stopped() {
        _state.value = ConnectionState()
    }

    @Synchronized
    fun updateThroughput(downKBs: Float, upKBs: Float) {
        val current = _state.value
        if (!current.running) return
        _state.value = current.copy(downKBs = downKBs, upKBs = upKBs)
    }
}
