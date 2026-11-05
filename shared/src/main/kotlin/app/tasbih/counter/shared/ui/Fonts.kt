package app.tasbih.counter.shared.ui

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.tasbih.counter.shared.R

@OptIn(ExperimentalTextApi::class)
private fun dmSans(weight: Int) = Font(
    R.font.dm_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val SansFamily = FontFamily(dmSans(400), dmSans(500), dmSans(600), dmSans(700))

/** Amiri carries the vowel marks on Arabic text far better than the system font. */
val ArabicFamily = FontFamily(Font(R.font.amiri))
