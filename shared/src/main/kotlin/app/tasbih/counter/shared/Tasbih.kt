package app.tasbih.counter.shared

/**
 * The tasbihs on offer. [id] is stored and synced, so it must never change.
 *
 * @param afterPrayerTarget counts for this tasbih in after-prayer mode, or null
 *   if it isn't part of that sequence.
 */
enum class Tasbih(
    val id: String,
    val nameRes: Int = R.string.custom_dhikr,
    val transliterationRes: Int = 0,
    val meaningRes: Int = 0,
    val afterPrayerTarget: Long? = null,
) {
    SUBHAN_ALLAH(
        "subhan_allah", R.string.subhan_allah, R.string.subhan_allah_latin, R.string.subhan_allah_meaning, 33,
    ),
    ALHAMDULILLAH(
        "alhamdulillah", R.string.alhamdulillah, R.string.alhamdulillah_latin, R.string.alhamdulillah_meaning, 33,
    ),
    ALLAHU_AKBAR(
        "allahu_akbar", R.string.allahu_akbar, R.string.allahu_akbar_latin, R.string.allahu_akbar_meaning, 34,
    ),
    LA_ILAHA_ILLA_ALLAH(
        "la_ilaha_illa_allah", R.string.la_ilaha, R.string.la_ilaha_latin, R.string.la_ilaha_meaning,
    ),
    ASTAGHFIRULLAH(
        "astaghfirullah", R.string.astaghfirullah, R.string.astaghfirullah_latin, R.string.astaghfirullah_meaning,
    ),
    SALAWAT(
        "salawat", R.string.salawat, R.string.salawat_latin, R.string.salawat_meaning,
    ),
    SUBHAN_ALLAHI_WA_BIHAMDIHI(
        "subhan_allahi_wa_bihamdihi", R.string.wa_bihamdihi, R.string.wa_bihamdihi_latin, R.string.wa_bihamdihi_meaning,
    ),
    SUBHAN_ALLAHIL_AZIM(
        "subhan_allahil_azim", R.string.al_azim, R.string.al_azim_latin, R.string.al_azim_meaning,
    ),
    LA_HAWLA(
        "la_hawla", R.string.la_hawla, R.string.la_hawla_latin, R.string.la_hawla_meaning,
    ),
    HASBUNALLAH(
        "hasbunallah", R.string.hasbunallah, R.string.hasbunallah_latin, R.string.hasbunallah_meaning,
    ),

    // Slots for the person's own dhikr, named in [Settings.customNames]. Fixed slots
    // give each one the same stored, synced counter as the built-in dhikr.
    CUSTOM_1("custom_1"),
    CUSTOM_2("custom_2"),
    CUSTOM_3("custom_3"),
    CUSTOM_4("custom_4"),
    CUSTOM_5("custom_5"),
    CUSTOM_6("custom_6"),
    CUSTOM_7("custom_7"),
    CUSTOM_8("custom_8");

    val isCustom: Boolean
        get() = id.startsWith("custom_")

    /** The next tasbih in the after-prayer sequence, wrapping round after the last. */
    val nextAfterPrayer: Tasbih
        get() {
            val sequence = entries.filter { it.afterPrayerTarget != null }
            return sequence[(sequence.indexOf(this) + 1) % sequence.size]
        }

    companion object {
        val builtIn: List<Tasbih>
            get() = entries.filterNot { it.isCustom }

        fun fromId(id: String?): Tasbih? = entries.firstOrNull { it.id == id }
    }
}
