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
    val cityAr: String = "البحيرة، مدينة النوبارية",
    val streetAr: String = "الـ11 عمارة، نهاية شارع الدائري، أمام الكوبري الجديد",
    val hoursAr: String = "مفتوحة ٢٤ ساعة",
    val phone: String = "010 4476 7648",
    val facebookUrl: String = "https://www.facebook.com/profile.php?id=61577072276679",
    val mapsUrl: String = "https://www.google.com/maps?q=%D8%A7%D9%84%D9%A1%D9%A1+%D8%B9%D9%85%D8%A7%D8%B1%D9%87+%D9%86%D9%87%D8%A7%D9%8A%D9%87+%D8%B4%D8%A7%D8%B1%D8%B9+%D8%A7%D9%84%D8%AF%D8%A7%D8%A6%D8%B1%D9%8A+%D8%A7%D9%85%D8%A7%D9%85+%D8%A7%D9%84%D9%83%D9%88%D8%A8%D8%B1%D9%8A+%D8%A7%D9%84%D8%AC%D8%AF%D9%8A%D8%AF,+Med%C3%AEnet+el-N%C3%BBb%C3%A2r%C3%AEya,+Egypt",
)

data class Benefit(
    val titleAr: String,
    val detailAr: String,
    val accent: BenefitAccent,
)

enum class BenefitAccent { Green, Terracotta, Saffron, Ink }
