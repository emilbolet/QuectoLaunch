package dk.emilbolet.quectolaunch.data

import java.text.Collator
import java.text.Normalizer

/**
 * Sorting and grouping for the A–Z drawer.
 *
 * Uses the phone's language rules, so on a Danish phone Æ, Ø and Å get their own
 * sections at the end of the alphabet, while on an English phone "Écoute" is filed under E.
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
        return sortedApps
            .groupBy { sectionFor(it.label, primary) }
            .map { (letter, apps) -> AppSection(letter, apps) }
    }

    private val combiningMarks = Regex("\\p{Mn}+")

    private fun sectionFor(label: String, primary: Collator): String {
        val first = label.trimStart().firstOrNull() ?: return OTHER
        if (!first.isLetter()) return OTHER

        val upper = first.uppercase()
        // Strip accents ("É" -> "E"), but only if the language treats them as the same letter.
        // In Danish "Å" is its own letter, so it stays "Å".
        val base = Normalizer.normalize(upper, Normalizer.Form.NFD).replace(combiningMarks, "")
        return if (base.isNotEmpty() && primary.compare(base, upper) == 0) base else upper
    }
}
