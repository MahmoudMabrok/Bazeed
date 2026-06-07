package tools.mo3ta.bazeed.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeAgoTest {

    private val now = 1_717_000_000_000L // arbitrary fixed "now"

    @Test fun under_a_minute_returns_now_label() {
        assertEquals("الآن", TimeAgo.format(now - 30_000, now))
    }

    @Test fun under_an_hour_returns_minutes() {
        assertEquals("منذ ٥ دقائق", TimeAgo.format(now - 5 * 60_000, now))
    }

    @Test fun under_a_day_returns_hours() {
        assertEquals("منذ ٣ ساعات", TimeAgo.format(now - 3 * 3_600_000, now))
    }

    @Test fun yesterday_returns_yesterday_label() {
        assertEquals("أمس", TimeAgo.format(now - 26 * 3_600_000, now))
    }

    @Test fun multiple_days_returns_days() {
        assertEquals("منذ ٤ أيام", TimeAgo.format(now - 4L * 24 * 3_600_000, now))
    }
}
