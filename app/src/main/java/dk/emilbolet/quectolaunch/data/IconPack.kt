package dk.emilbolet.quectolaunch.data

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser

/** An installed icon pack, as listed in settings. */
data class IconPackInfo(val packageName: String, val label: String)

/**
 * A loaded icon pack: which drawable in the pack replaces which app.
 *
 * Icon packs are ordinary apps that ship an `appfilter.xml` with lines like
 * `<item component="ComponentInfo{com.android.chrome/com.google.android.apps.chrome.Main}" drawable="chrome" />`.
 */
class IconPack private constructor(
    val packageName: String,
    private val resources: Resources,
    /** Flattened component name -> drawable name. */
    private val drawableNames: Map<String, String>,
) {
    /** The pack's icon for an app, or null if the pack doesn't cover it. */
    // The pack only tells us drawable names, so they have to be looked up by name
    @SuppressLint("DiscouragedApi")
    fun iconFor(component: ComponentName): Drawable? {
        val name = drawableNames[component.flattenToString()] ?: return null
        var id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) id = resources.getIdentifier(name, "mipmap", packageName)
        if (id == 0) return null
        return try {
            ResourcesCompat.getDrawable(resources, id, null)
        } catch (e: Resources.NotFoundException) {
            null
        }
    }

    companion object {
        /** Reads a pack's appfilter.xml. Slow for big packs; call off the main thread. */
        @SuppressLint("DiscouragedApi")
        fun load(context: Context, packageName: String): IconPack? {
            return try {
                val packContext = context.createPackageContext(packageName, 0)
                val resources = packContext.resources
                val xmlId = resources.getIdentifier("appfilter", "xml", packageName)
                val names = if (xmlId != 0) {
                    resources.getXml(xmlId).use { parse(it) }
                } else {
                    // Some packs keep the file in assets instead
                    packContext.assets.open("appfilter.xml").use { stream ->
                        parse(Xml.newPullParser().apply { setInput(stream, null) })
                    }
                }
                IconPack(packageName, resources, names)
            } catch (e: Exception) {
                // Pack uninstalled, or it has no (readable) appfilter.xml
                null
            }
        }

        private fun parse(parser: XmlPullParser): Map<String, String> {
            val names = HashMap<String, String>()
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
                val component = parser.getAttributeValue(null, "component") ?: continue
                val drawable = parser.getAttributeValue(null, "drawable") ?: continue
                if (!component.startsWith(PREFIX) || !component.endsWith("}")) continue
                // Normalises short forms such as "pkg/.Main" to the full class name
                val flat = component.substring(PREFIX.length, component.length - 1)
                val name = ComponentName.unflattenFromString(flat) ?: continue
                names.putIfAbsent(name.flattenToString(), drawable)
            }
            return names
        }

        private const val PREFIX = "ComponentInfo{"
    }
}
