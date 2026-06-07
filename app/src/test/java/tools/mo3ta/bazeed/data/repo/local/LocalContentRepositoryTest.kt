package tools.mo3ta.bazeed.data.repo.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tools.mo3ta.bazeed.data.Announcement
import tools.mo3ta.bazeed.data.AnnouncementType

class LocalContentRepositoryTest {

    private fun sample(id: String, createdAt: Long = 1L) = Announcement(
        id = id,
        title = "t-$id",
        description = "d-$id",
        type = AnnouncementType.Health,
        expirationDate = createdAt + 86_400_000L,
        createdAt = createdAt,
    )

    @Test fun create_appends_and_flow_updates() = runBlocking {
        val repo = LocalContentRepository()
        repo.create(sample("a", createdAt = 100)).getOrThrow()
        repo.create(sample("b", createdAt = 200)).getOrThrow()
        val list = repo.announcements.value
        assertEquals(listOf("b", "a"), list.map { it.id }) // sorted createdAt desc
    }

    @Test fun update_replaces_fields() = runBlocking {
        val repo = LocalContentRepository()
        repo.create(sample("a")).getOrThrow()
        val edited = sample("a").copy(title = "edited")
        repo.update("a", edited).getOrThrow()
        assertEquals("edited", repo.announcements.value.single().title)
    }

    @Test fun delete_removes() = runBlocking {
        val repo = LocalContentRepository()
        repo.create(sample("a")).getOrThrow()
        repo.delete("a").getOrThrow()
        assertTrue(repo.announcements.value.isEmpty())
    }
}
