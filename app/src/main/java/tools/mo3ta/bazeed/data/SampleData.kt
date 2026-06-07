package tools.mo3ta.bazeed.data

object SampleData {

    val pharmacy = PharmacyInfo()

    val benefits = listOf(
        Benefit(
            titleAr = "توصيل لباب البيت",
            detailAr = "مجاني داخل النوبارية",
            accent = BenefitAccent.Green,
        ),
        Benefit(
            titleAr = "مراجعة الصيدلي",
            detailAr = "قبل كل إمداد شهري",
            accent = BenefitAccent.Terracotta,
        ),
        Benefit(
            titleAr = "تجديد تلقائي",
            detailAr = "لا تنفد أدويتك",
            accent = BenefitAccent.Saffron,
        ),
        Benefit(
            titleAr = "تنبيه قبل التوصيل",
            detailAr = "قبل اليوم بثلاثة أيام",
            accent = BenefitAccent.Ink,
        ),
    )
}
