package tools.mo3ta.bazeed.data

enum class AnnouncementCategory(val labelAr: String) {
    Health("صحة"),
    Alert("تنبيه"),
    Tip("نصيحة"),
    Offer("عروض")
}

data class Announcement(
    val id: String,
    val title: String,
    val snippet: String,
    val body: String,
    val category: AnnouncementCategory,
    val timeAgoAr: String,
    val featured: Boolean = false,
    val highlight: String? = null
)

data class PharmacyInfo(
    val nameAr: String = "صيدلية بازيد",
    val nameEn: String = "Bazeed Pharmacy",
    val cityAr: String = "البحيرة، دمنهور",
    val streetAr: String = "شارع الجيش — دمنهور",
    val hoursAr: String = "السبت — الخميس · ٩ ص حتى ١٢ م",
    val phone: String = "045 333 0 333",
    val openNow: Boolean = true
)

data class Benefit(
    val titleAr: String,
    val detailAr: String,
    val accent: BenefitAccent
)

enum class BenefitAccent { Green, Terracotta, Saffron, Ink }
