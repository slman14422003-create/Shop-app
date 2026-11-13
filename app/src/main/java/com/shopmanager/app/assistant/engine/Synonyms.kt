package com.shopmanager.app.assistant.engine

internal object Synonyms {
    private val GROUPS: List<List<String>> = listOf(
        listOf("نسكافيه", "نسكافية", "نسكافي", "نيسكافيه", "نيسكافية", "نسكافه", "نسكفيه", "نسكافيهه", "nescafe", "nescafé", "نسكافيا"),
        listOf("قهوه", "قهوة", "كوفي", "كافي", "coffee", "قهوا"),
        listOf("شاي", "شاى", "تي", "chai", "tea", "شايي"),
        listOf("سكر", "سكّر", "sugar", "سكره"),
        listOf("ملح", "milh", "salt", "ملحه"),
        listOf("رز", "ارز", "أرز", "رزّ", "rice", "رزه"),
        listOf("برغل", "بلغور", "برقل"),
        listOf("عدس", "عدسه", "lentil"),
        listOf("حمص", "حمّص", "ملانه", "حمصه"),
        listOf("فول", "فوله", "فولا"),
        listOf("فاصولياء", "فاصوليا", "فاصوليه", "فاصولية", "فصوليا", "فاصولي"),
        listOf("بندوره", "بندورة", "طماطم", "طماطه", "طماطة", "قوطه", "قوطة", "اوطه", "tomato"),
        listOf("بطاطا", "بطاطس", "بطاطه", "بطاطة", "بطاطاس", "potato"),
        listOf("خيار", "قتا", "قثاء", "قثا", "خيارة", "خياره"),
        listOf("كوسا", "كوسة", "كوسه", "قرع", "قرعه"),
        listOf("باذنجان", "بدنجان", "بتنجان", "باذنجانه", "بذنجان", "ابيرجين"),
        listOf("ثوم", "توم", "تومه", "ثومه", "garlic"),
        listOf("بصل", "بصله", "بصلة", "onion"),
        listOf("جزر", "جزره", "carrot"),
        listOf("ليمون", "ليمونه", "حامض", "حامضه", "lemon", "ليمونة"),
        listOf("موز", "موزه", "banana"),
        listOf("تفاح", "تفاحه", "تفاحة", "apple"),
        listOf("برتقال", "برتقاله", "orange", "برتقالة"),
        listOf("زنجبيل", "جنزبيل", "جنجبيل", "زنجبيله", "ginger"),
        listOf("كركم", "كركوم", "turmeric", "كركمه"),
        listOf("هيل", "هال", "حبهان", "cardamom", "هيله"),
        listOf("قرفه", "قرفة", "كرفه", "cinnamon", "قرفا"),
        listOf("كمون", "كمونه", "cumin", "كمّون"),
        listOf("كزبره", "كزبرة", "كسبره", "كسبرة", "coriander", "كزبرا"),
        listOf("بقدونس", "معدنوس", "مقدونس", "parsley", "بقدنس"),
        listOf("نعناع", "نعنع", "mint", "نعناعه"),
        listOf("زعتر", "صعتر", "thyme", "زعترر"),
        listOf("يانسون", "ينسون", "anise", "يانسوون"),
        listOf("قرنفل", "قرنفله", "clove", "قرنفلل"),
        listOf("ميرميه", "مريميه", "ميرمية", "مريمية", "sage", "ميريميه"),
        listOf("مستكه", "مستكا", "مصطكي", "مصطكى", "mastic", "مستكة"),
        listOf("سماق", "سماقه", "sumac"),
        listOf("دبس", "دبسه", "molasses"),
        listOf("طحينه", "طحينة", "tahini", "طحينا"),
        listOf("حلاوه", "حلاوة", "halawa", "حلاوا"),
        listOf("مربي", "مربى", "مربا", "jam", "مربه"),
        listOf("قشطه", "قشطة", "قيمر", "كريمه", "كريمة", "قشطا"),
        listOf("ذره", "ذرة", "درة", "درا", "corn", "ذرا"),
        listOf("فشار", "بوشار", "popcorn", "فشاار"),
        listOf("سميد", "سميده", "سميدا", "semolina"),
        listOf("معكرونه", "مكرونه", "مكرونة", "معكرونة", "باستا", "pasta", "معجونه"),
        listOf("شعيريه", "شعيرية", "شعيريا", "vermicelli"),
        listOf("زيت", "زيته", "oil"),
        listOf("سمنه", "سمن", "سمنة", "ghee", "سمنا"),
        listOf("جبنه", "جبن", "جبنة", "cheese", "جبنا"),
        listOf("بيض", "بيضه", "بيضة", "egg", "بيضا"),
        listOf("دقيق", "طحين", "flour", "دقيقه"),
        listOf("نشا", "نشاء", "نشاا", "starch", "نشاه"),
        listOf("فانيلا", "فانيليا", "فانيلين", "فنيلا", "vanilla"),
        listOf("شوكولاته", "شوكولا", "شوكلاته", "شوكلا", "شكولاته", "شوكولاتة", "شكلاته", "chocolate", "شوكلاتة"),
        listOf("بسكويت", "بسكوت", "بسكويته", "biscuit", "بسكوييت"),
        listOf("تونه", "تونا", "تون", "tuna", "تونة"),
        listOf("سردين", "سردينه", "sardine", "سردينا"),
        listOf("ماء", "ميه", "ميا", "مويه", "ماي", "مياه", "water", "موية"),
        listOf("عصير", "جوس", "juice", "عصيير"),
        listOf("صابون", "صابونه", "صابونة", "soap", "صبون"),
        listOf("شامبو", "شمبو", "shampoo", "شامبوو"),
        listOf("مناديل", "محارم", "كلينكس", "tissue", "منديل", "محرمه", "محرمة"),
        listOf("حفاضات", "حفاظات", "حفايض", "بامبرز", "حفاضه", "pampers", "حفاض"),
        listOf("ولاعه", "ولاعة", "قداحه", "قداحة", "ولاعا", "lighter"),
        listOf("دخان", "سجاير", "سجائر", "تبغ", "سيجاره", "سيجارة", "سيجارا"),
        listOf("علكه", "علكة", "لبانه", "لبانة", "علكا", "gum", "لبان"),
        listOf("شيبس", "جبس", "chips", "جيبس", "شيبسي"),
        listOf("كاتشب", "كتشب", "كاتشاب", "ketchup", "كتشاب"),
        listOf("مايونيز", "مايو", "mayonnaise", "مايونيس", "مايونيزز"),
        listOf("خل", "خلل", "vinegar", "خلّ"),
        listOf("لوبيا", "لوبياء", "لوبيه", "لوبية"),
        listOf("فستق", "فستقه", "fistuq", "فستقا"),
        listOf("لوز", "لوزه", "almond", "لوزز"),
        listOf("زبيب", "زبيبه", "raisin", "زبيبا"),
        listOf("تمر", "تمره", "dates", "تمرر"),
        listOf("كعك", "كعكه", "كعكة", "كعكا"),
        listOf("مصاصه", "مصاصة", "مصاص", "مصاصا", "lollipop"),
        listOf("بهارات", "بهار", "spice", "بهارا", "spices"),
        listOf("فلفل", "فلفلل", "pepper", "فلفله"),
        listOf("كبه", "كبة", "كبا"),
        listOf("بيبسي", "pepsi", "ببسي", "بيبسى"),
        listOf("كوكاكولا", "cocacola", "coca-cola", "coke", "كوكا", "كولا", "كوكاكولاا"),
        listOf("كيتكات", "kitkat", "kit-kat", "كتكات", "كيتكاتت"),
        listOf("ميرندا", "mirinda", "ميرنده"),
        listOf("ليبتون", "lipton", "ليبتن"),
        listOf("نوتيلا", "nutella", "نيوتيلا"),
        listOf("اندومي", "indomie", "اندومى"),
        listOf("ماجي", "maggi", "ماغي"),
        listOf("تايد", "tide", "تيد"),
        listOf("اوريو", "oreo", "اوريوو"),
        listOf("سنيكرز", "snickers", "سنكرز"),
        listOf("تويكس", "twix", "تويكسس"),
        listOf("لايز", "lays", "ليز"),
        listOf("برينجلز", "pringles", "برنجلز"),
        listOf("ريدبول", "redbull", "red-bull", "ريد"),
        listOf("فيري", "fairy", "فيرى"),
        listOf("منظف", "منظفات", "ديتول", "detergent", "منضف")
    )

    private val MAP: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        for (g in GROUPS) {
            if (g.isEmpty()) continue
            val rep = ArabicText.stripAl(ArabicText.normalize(g[0]))
            for (w in g) m[ArabicText.stripAl(ArabicText.normalize(w))] = rep
        }
        m
    }

    fun canon(token: String): String = MAP[token] ?: token

    fun sameGroup(a: String, b: String): Boolean {
        val ca = canon(ArabicText.stripAl(ArabicText.normalize(a)))
        val cb = canon(ArabicText.stripAl(ArabicText.normalize(b)))
        return ca == cb
    }
}
