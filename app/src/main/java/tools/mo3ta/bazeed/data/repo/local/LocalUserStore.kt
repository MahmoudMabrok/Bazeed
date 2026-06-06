package tools.mo3ta.bazeed.data.repo.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tools.mo3ta.bazeed.data.auth.AuthUser
import tools.mo3ta.bazeed.data.auth.UserRole
import java.util.UUID

/**
 * In-memory account store shared by the local Auth and User repositories so both
 * see the same data within a process. Seeded with one admin and one customer so
 * the flavors are testable out of the box.
 *
 * NOTE: state is process-lifetime only — accounts created at runtime are lost on
 * cold start, and exist only on this device. Persisting via DataStore and syncing
 * via Firebase are the documented follow-ups (see the architecture spec).
 */
object LocalUserStore {

    private val passwords = mutableMapOf<String, String>() // email (lowercased) -> password
    private val _users = MutableStateFlow<List<AuthUser>>(emptyList())
    val users: StateFlow<List<AuthUser>> = _users.asStateFlow()

    init {
        register(
            AuthUser("seed-admin", "admin@bazeed.app", "مدير الصيدلية", UserRole.ADMIN),
            password = "admin123",
        )
        register(
            AuthUser("seed-user", "user@bazeed.app", "عميل تجريبي", UserRole.USER),
            password = "user123",
        )
    }

    @Synchronized
    fun register(user: AuthUser, password: String) {
        passwords[user.email.trim().lowercase()] = password
        _users.value = _users.value + user
    }

    fun exists(email: String): Boolean {
        val key = email.trim().lowercase()
        return _users.value.any { it.email.lowercase() == key }
    }

    /** Returns the user when credentials match an active account, else null. */
    fun verify(email: String, password: String): AuthUser? {
        val key = email.trim().lowercase()
        val user = _users.value.firstOrNull { it.email.lowercase() == key } ?: return null
        return if (user.active && passwords[key] == password) user else null
    }

    fun newUid(): String = "local-" + UUID.randomUUID().toString().take(8)
}
