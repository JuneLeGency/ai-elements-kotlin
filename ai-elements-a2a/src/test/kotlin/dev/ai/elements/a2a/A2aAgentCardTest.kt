package dev.ai.elements.a2a

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

/** Listing agents must not wait on one that hangs: the HTTP client's blocking I/O ignores cancellation. */
class A2aAgentCardTest {
    @Test
    fun cardOrNull_returnsOnTime_whenTheAgentNeverAnswers() = runBlocking {
        // Accepts connections (the backlog) but never answers: the request blocks in a read.
        ServerSocket(0).use { silent ->
            val agent = A2aAgent("http://127.0.0.1:${silent.localPort}")
            val started = System.currentTimeMillis()
            assertNull(agent.cardOrNull(timeoutMs = 500))
            val first = System.currentTimeMillis() - started
            assertTrue("took $first ms", first < 2_000)

            // The same fetch is still pending: a second turn waits no longer and starts no new request.
            val again = System.currentTimeMillis()
            assertNull(agent.cardOrNull(timeoutMs = 300))
            assertTrue(System.currentTimeMillis() - again < 1_500)
        }
    }

    @Test
    fun unreachableAgent_isNotRetriedRightAway() = runBlocking {
        val port = ServerSocket(0).use { it.localPort } // closed: connections are refused
        val agent = A2aAgent("http://127.0.0.1:$port")
        assertNull(agent.cardOrNull(timeoutMs = 2_000))
        val started = System.currentTimeMillis()
        assertNull(agent.cardOrNull(timeoutMs = 2_000, retryAfterMs = 60_000))
        assertTrue(System.currentTimeMillis() - started < 100) // answered from the failure, no request
    }
}
