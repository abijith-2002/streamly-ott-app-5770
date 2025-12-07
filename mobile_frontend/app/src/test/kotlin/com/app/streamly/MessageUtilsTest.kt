package com.app.streamly

import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * PUBLIC_INTERFACE
 * Basic unit test for MessageUtils. Uses JUnit4 to ensure discovery with Android unit tests.
 */
class MessageUtilsTest {
    @Test
    fun testGetMessage() {
        // Expect exactly the message produced by MessageUtils
        assertEquals("Hello     World!", MessageUtils.message())
    }
}
