package tools.mo3ta.bazeed.data

/** Renders an Arabic relative-time label like "الآن", "منذ ٣ ساعات", "أمس", "منذ ٤ أيام". */
object TimeAgo {

    private val arabicDigits = charArrayOf('٠','١','٢','٣','٤','٥','٦','٧','٨','٩')

    fun format(createdAt: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - createdAt).coerceAtLeast(0L)
        val minutes = diff / 60_000
        val hours = diff / 3_600_000
        val days = diff / (24 * 3_600_000)
        return when {
            minutes < 1 -> "الآن"
            hours < 1 -> "منذ ${toArabic(minutes)} ${pluralAr(minutes, "دقيقة", "دقيقتين", "دقائق")}"
            days < 1 -> "منذ ${toArabic(hours)} ${pluralAr(hours, "ساعة", "ساعتين", "ساعات")}"
            days < 2 -> "أمس"
            else -> "منذ ${toArabic(days)} ${pluralAr(days, "يوم", "يومين", "أيام")}"
        }
    }

    private fun pluralAr(n: Long, one: String, two: String, plural: String): String =
        when (n) { 1L -> one; 2L -> two; else -> plural }

    private fun toArabic(n: Long): String =
        n.toString().map { arabicDigits[it.digitToInt()] }.joinToString("")
}
