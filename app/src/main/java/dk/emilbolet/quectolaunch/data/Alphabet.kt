package dk.emilbolet.quectolaunch.data

import android.icu.text.AlphabeticIndex
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * Sorting and grouping for the A–Z drawer.
 *
 * Uses the phone's language rules, so on a Danish phone Æ, Ø and Å get their own
 * sections at the end of the alphabet, while on an English phone "Écoute" is filed under E.
 * Letters written with two characters are filed where the language sorts them: in Danish
 * "Aarhus" sorts as "Århus" and goes under Å, in Czech "Chrome" goes under CH after H.
 */
object Alphabet {

    const val OTHER = "#"

    fun sort(apps: List<AppInfo>): List<AppInfo> {
        val collator = Collator.getInstance()
        return apps.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /** Expects apps already sorted with [sort]. */
    fun sections(sortedApps: List<AppInfo>): List<AppSection> {
        val primary = Collator.getInstance().apply { strength = Collator.PRIMARY }
        // The phone's language's own index letters, plus A–Z for app names in Latin letters
        // on phones set to another script
        val index = AlphabeticIndex<Any>(Locale.getDefault())
            .addLabels(Locale.ENGLISH)
            .buildImmutableIndex()
        return sortedApps
            .groupBy { sectionFor(it.label, index, primary) }
            .map { (letter, apps) -> AppSection(letter, apps) }
    }

    private val combiningMarks = Regex("\\p{Mn}+")

    private fun sectionFor(
        label: String,
        index: AlphabeticIndex.ImmutableIndex<Any>,
        primary: Collator,
    ): String {
        val first = label.trimStart().firstOrNull() ?: return OTHER
        if (!first.isLetter()) return OTHER

        val bucket = index.getBucket(index.getBucketIndex(label.trimStart()))
        if (bucket.labelType == AlphabeticIndex.Bucket.LabelType.NORMAL) return bucket.label

        // A letter the index has no section for, such as another script: use the letter itself
        val upper = first.uppercase()
        // Strip accents ("É" -> "E"), but only if the language treats them as the same letter.
        // In Danish "Å" is its own letter, so it stays "Å".
        val base = Normalizer.normalize(upper, Normalizer.Form.NFD).replace(combiningMarks, "")
        return if (base.isNotEmpty() && primary.compare(base, upper) == 0) base else upper
    }
}
