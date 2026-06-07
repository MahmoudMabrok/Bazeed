package tools.mo3ta.bazeed.data.repo.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import tools.mo3ta.bazeed.data.auth.AuthUser
import tools.mo3ta.bazeed.data.auth.UserRole

/** Mapping + access helpers for the `users` collection. */
internal object UserDocs {

    const val COLLECTION = "users"

    fun roleFrom(raw: String?): UserRole =
        if (raw?.equals("admin", ignoreCase = true) == true) UserRole.ADMIN else UserRole.USER

    fun toAuthUser(doc: DocumentSnapshot, fallbackEmail: String? = null): AuthUser? {
        if (!doc.exists()) return null
        val email = doc.getString("email") ?: fallbackEmail ?: return null
        return AuthUser(
            uid = doc.id,
            email = email,
            displayName = doc.getString("displayName")?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@"),
            role = roleFrom(doc.getString("role")),
            active = doc.getBoolean("active") ?: true,
        )
    }

    /** Reads a single user doc; returns null when missing (= unprovisioned). */
    suspend fun load(
        firestore: FirebaseFirestore,
        uid: String,
        fallbackEmail: String?,
    ): AuthUser? = try {
        toAuthUser(firestore.collection(COLLECTION).document(uid).get().await(), fallbackEmail)
    } catch (e: Exception) {
        null
    }
}
