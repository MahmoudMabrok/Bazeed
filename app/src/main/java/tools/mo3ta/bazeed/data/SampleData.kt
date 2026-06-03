package tools.mo3ta.bazeed.data

object SampleData {

    val pharmacy = PharmacyInfo()

    val featuredAnnouncement = Announcement(
        id = "feat-1",
        title = "خصم على فيتامينات الأطفال والمكملات",
        snippet = "هذا الأسبوع فقط — ينتهي العرض يوم الجمعة",
        body = "خصم خاص بمناسبة فصل الصيف على جميع الفيتامينات والمكملات الغذائية للأطفال. " +
                "اسأل الصيدلي عن المنتجات المتاحة.",
        category = AnnouncementCategory.Offer,
        timeAgoAr = "اليوم · 09:30",
        featured = true,
        highlight = "20%"
    )

    val announcements = listOf(
        Announcement(
            id = "ann-1",
            title = "وصول لقاحات الأطفال الموسمية",
            snippet = "متاحة الآن للحجز المسبق بالصيدلية",
            body = "وصلت لقاحات الأطفال الموسمية لعام ٢٠٢٦. " +
                    "يمكنكم حجز موعد طفلكم خلال الأسبوع الحالي. تواصل مع الصيدلية لتفاصيل أكثر.",
            category = AnnouncementCategory.Health,
            timeAgoAr = "منذ ٤ ساعات"
        ),
        Announcement(
            id = "ann-2",
            title = "تمديد ساعات العمل خلال يونيو",
            snippet = "من السبت إلى الخميس · ٩ ص حتى ١٢ م",
            body = "نُعلن عن تمديد ساعات العمل خلال شهر يونيو لخدمتكم بشكل أفضل. " +
                    "الجمعة من ٢ ظ حتى ١٢ م.",
            category = AnnouncementCategory.Alert,
            timeAgoAr = "أمس · 18:00"
        ),
        Announcement(
            id = "ann-3",
            title = "كيف تخزّن أدويتك في الصيف؟",
            snippet = "نصائح للحفاظ على فعالية الأدوية في الجو الحار",
            body = "درجات حرارة البحيرة المرتفعة تؤثر على فعالية بعض الأدوية. " +
                    "احفظ الأدوية في مكان بارد وجاف، بعيدًا عن أشعة الشمس المباشرة.",
            category = AnnouncementCategory.Tip,
            timeAgoAr = "منذ يومين"
        ),
        Announcement(
            id = "ann-4",
            title = "خدمة التوصيل المنزلي لكبار السن",
            snippet = "مجاني داخل دمنهور — اتصل بنا",
            body = "نُقدّم خدمة توصيل مجانية لكبار السن داخل دمنهور والمناطق المجاورة.",
            category = AnnouncementCategory.Alert,
            timeAgoAr = "منذ ٣ أيام"
        )
    )

    val benefits = listOf(
        Benefit(
            titleAr = "توصيل لباب البيت",
            detailAr = "مجاني داخل دمنهور",
            accent = BenefitAccent.Green
        ),
        Benefit(
            titleAr = "مراجعة الصيدلي",
            detailAr = "قبل كل إمداد شهري",
            accent = BenefitAccent.Terracotta
        ),
        Benefit(
            titleAr = "تجديد تلقائي",
            detailAr = "لا تنفد أدويتك",
            accent = BenefitAccent.Saffron
        ),
        Benefit(
            titleAr = "تنبيه قبل التوصيل",
            detailAr = "قبل اليوم بثلاثة أيام",
            accent = BenefitAccent.Ink
        )
    )
}
