package tools.mo3ta.bazeed.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthExceptionTest {

    @Test fun validation_carries_passed_message() {
        val ex = AuthException.Validation("custom validation message")
        assertEquals("custom validation message", ex.userMessage)
        assertEquals("custom validation message", ex.message)
    }

    @Test fun invalid_credentials_has_arabic_message() {
        val ex = AuthException.InvalidCredentials()
        assertEquals("بيانات الدخول غير صحيحة", ex.userMessage)
    }

    @Test fun user_not_provisioned_has_arabic_message() {
        val ex = AuthException.UserNotProvisioned()
        assertTrue(ex.userMessage.isNotBlank())
    }

    @Test fun not_authorized_has_arabic_message() {
        val ex = AuthException.NotAuthorized()
        assertTrue(ex.userMessage.isNotBlank())
    }

    @Test fun network_has_arabic_message() {
        val ex = AuthException.Network()
        assertTrue(ex.userMessage.isNotBlank())
    }

    @Test fun too_many_attempts_has_arabic_message() {
        val ex = AuthException.TooManyAttempts()
        assertTrue(ex.userMessage.isNotBlank())
    }

    @Test fun email_in_use_has_arabic_message() {
        val ex = AuthException.EmailInUse()
        assertTrue(ex.userMessage.isNotBlank())
    }

    @Test fun unknown_preserves_cause_and_message() {
        val cause = RuntimeException("boom")
        val ex = AuthException.Unknown(cause)
        assertNotNull(ex.cause)
        assertSame(cause, ex.cause)
        assertTrue(ex.userMessage.isNotBlank())
    }

    @Test fun userMessage_is_also_exception_message() {
        val ex = AuthException.InvalidCredentials()
        assertEquals(ex.userMessage, ex.message)
    }
}
