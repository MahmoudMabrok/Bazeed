package tools.mo3ta.bazeed.data

enum class AnnouncementType(val labelAr: String) {
    Health("صحة"),
    Alert("تنبيه"),
    Tip("نصيحة"),
    Offer("عروض"),
}

data class Announcement(
    val id: String,
    val title: String,
    val description: String,
    val type: AnnouncementType,
    val expirationDate: Long, // epoch ms, admin-set, not rendered on customer UI
    val createdAt: Long,      // epoch ms, used for the time-ago label
)

data class PharmacyInfo(
    val nameAr: String = "صيدلية بازيد",
    val nameEn: String = "Bazeed Pharmacy",
    val cityAr: String = "البحيرة، دمنهور",
    val streetAr: String = "شارع الجيش — دمنهور",
    val hoursAr: String = "السبت — الخميس · ٩ ص حتى ١٢ م",
    val phone: String = "045 333 0 333",
    val openNow: Boolean = true,
)

data class Benefit(
    val titleAr: String,
    val detailAr: String,
    val accent: BenefitAccent,
)

enum class BenefitAccent { Green, Terracotta, Saffron, Ink }
