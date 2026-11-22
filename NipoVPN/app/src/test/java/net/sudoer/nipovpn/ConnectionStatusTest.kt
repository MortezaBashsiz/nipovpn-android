package net.sudoer.nipo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConnectionStatusTest {

    private val quotaMessage =
        "Your account's data allowance has been exhausted or your account has been disabled."

    @Test
    fun refusalStopsTheConnectionAndKeepsTheReason() {
        ConnectionStatus.started()

        ConnectionStatus.refused(quotaMessage)

        val state = ConnectionStatus.state.value
        assertFalse(state.running)
        assertEquals(quotaMessage, state.refusal)
    }

    // The service stops itself after a refusal, which reports stopped() too.
    @Test
    fun refusalOutlivesTheServiceShuttingDown() {
        ConnectionStatus.started()
        ConnectionStatus.refused(quotaMessage)

        ConnectionStatus.stopped()

        assertEquals(quotaMessage, ConnectionStatus.state.value.refusal)
    }

    @Test
    fun connectingAgainClearsTheOldReason() {
        ConnectionStatus.refused(quotaMessage)

        ConnectionStatus.started()

        assertEquals(null, ConnectionStatus.state.value.refusal)
    }
}
