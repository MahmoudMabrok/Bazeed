package tools.mo3ta.bazeed.data.repo.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tools.mo3ta.bazeed.data.Announcement
import tools.mo3ta.bazeed.data.repo.ContentRepository

/** In-memory ContentRepository for unit tests and developer iteration. */
class LocalContentRepository : ContentRepository {

    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    override val announcements: StateFlow<List<Announcement>> = _announcements.asStateFlow()

    override suspend fun create(a: Announcement): Result<Announcement> {
        _announcements.value = (_announcements.value + a).sortedByDescending { it.createdAt }
        return Result.success(a)
    }

    override suspend fun update(id: String, a: Announcement): Result<Unit> {
        val current = _announcements.value
        val idx = current.indexOfFirst { it.id == id }
        if (idx < 0) return Result.failure(IllegalArgumentException("not found: $id"))
        val replaced = current.toMutableList().apply {
            // Preserve original createdAt; the update form does not edit it.
            set(idx, a.copy(createdAt = current[idx].createdAt))
        }
        _announcements.value = replaced.sortedByDescending { it.createdAt }
        return Result.success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        _announcements.value = _announcements.value.filterNot { it.id == id }
        return Result.success(Unit)
    }
}
