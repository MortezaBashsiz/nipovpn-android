package net.sudoer.nipo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConnectionStatusTest {

    @Test
    fun refusalStopsTheConnectionAndKeepsTheReason() {
        ConnectionStatus.started()

        ConnectionStatus.refused(Refusal.QUOTA_OR_DISABLED)

        val state = ConnectionStatus.state.value
        assertFalse(state.running)
        assertEquals(Refusal.QUOTA_OR_DISABLED, state.refusal)
    }

    // The service stops itself after a refusal, which reports stopped() too.
    @Test
    fun refusalOutlivesTheServiceShuttingDown() {
        ConnectionStatus.started()
        ConnectionStatus.refused(Refusal.QUOTA_OR_DISABLED)

        ConnectionStatus.stopped()

        assertEquals(Refusal.QUOTA_OR_DISABLED, ConnectionStatus.state.value.refusal)
    }

    @Test
    fun connectingAgainClearsTheOldReason() {
        ConnectionStatus.refused(Refusal.QUOTA_OR_DISABLED)

        ConnectionStatus.started()

        assertEquals(null, ConnectionStatus.state.value.refusal)
    }
}
