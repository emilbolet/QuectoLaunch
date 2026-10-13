package dk.emilbolet.quectolaunch.data

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.Xml
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import java.util.Calendar

/** An installed icon pack, as listed in settings. */
data class IconPackInfo(val packageName: String, val label: String)

/** An icon picked by hand for one app: a drawable of some installed icon pack. */
data class IconChoice(val pack: String, val drawable: String)

/** A pack's drawables, looked up by name. Cheap to create; doesn't read the pack's app list. */
class PackResources private constructor(val packageName: String, private val resources: Resources) {

    // The pack only tells us drawable names, so they have to be looked up by name
    @SuppressLint("DiscouragedApi")
    fun drawable(name: String): Drawable? {
        var id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) id = resources.getIdentifier(name, "mipmap", packageName)
        if (id == 0) return null
        return try {
            ResourcesCompat.getDrawable(resources, id, null)
        } catch (e: Resources.NotFoundException) {
            null
        }
    }

    /** Opens one of the pack's XML files, from `res/xml` or, as some packs have it, from assets. */
    @SuppressLint("DiscouragedApi")
    fun <T> readXml(context: Context, name: String, read: (XmlPullParser) -> T): T? {
        return try {
            val id = resources.getIdentifier(name, "xml", packageName)
            if (id != 0) {
                resources.getXml(id).use(read)
            } else {
                context.createPackageContext(packageName, 0).assets.open("$name.xml").use { stream ->
                    read(Xml.newPullParser().apply { setInput(stream, null) })
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun open(context: Context, packageName: String): PackResources? = try {
            PackResources(packageName, context.createPackageContext(packageName, 0).resources)
        } catch (e: Exception) {
            null // uninstalled
        }
    }
}

/** A clock icon whose hands are layers that turn with their level (`<dynamic-clock>`). */
private class ClockSpec(
    val hourLayer: Int,
    val minuteLayer: Int,
    val secondLayer: Int,
    /** The time the drawable shows when every level is 0. */
    val defaultHour: Int,
    val defaultMinute: Int,
)

/**
 * A loaded icon pack: which drawable in the pack replaces which app, and how to dress up
 * the apps it doesn't cover.
 *
 * Icon packs are ordinary apps that ship an `appfilter.xml` with lines like
 * `<item component="ComponentInfo{com.android.chrome/com.google.android.apps.chrome.Main}" drawable="chrome" />`.
 * The same file may hold:
 * - `<iconback img1=".." img2=".."/>`, `<iconmask img1=".."/>`, `<iconupon img1=".."/>` and
 *   `<scale factor="0.7"/>`: a background, a cut-out shape and an overlay for uncovered apps
 * - `<calendar component=".." prefix="calendar_"/>`: one drawable per day of the month
 * - `<dynamic-clock drawable=".." hourLayerIndex=".." minuteLayerIndex=".." .../>`: a clock
 *   drawable with movable hands
 */
class IconPack private constructor(
    val resources: PackResources,
    /** Flattened component name -> drawable name. */
    private val drawableNames: Map<String, String>,
    /** Flattened component name -> drawable name prefix; the day of the month is added. */
    private val calendars: Map<String, String>,
    /** Drawable name -> how to set its hands. */
    private val clocks: Map<String, ClockSpec>,
    private val backs: List<String>,
    private val mask: String?,
    private val upon: String?,
    private val scale: Float,
) {
    val packageName: String get() = resources.packageName

    /** Whether the icon of this app changes with the date or the time. */
    fun isCalendar(component: ComponentName): Boolean = component.flattenToString() in calendars

    fun isClock(component: ComponentName): Boolean = drawableNames[component.flattenToString()] in clocks

    val hasCalendars: Boolean get() = calendars.isNotEmpty()
    val hasClocks: Boolean get() = clocks.isNotEmpty()

    /** The pack's icon for an app as it should look right now, or null if the pack doesn't cover it. */
    fun iconFor(component: ComponentName): Drawable? {
        val flat = component.flattenToString()
        val now = Calendar.getInstance()
        calendars[flat]?.let { prefix ->
            resources.drawable(prefix + now.get(Calendar.DAY_OF_MONTH))?.let { return it }
        }
        val name = drawableNames[flat] ?: return null
        val drawable = resources.drawable(name) ?: return null
        val clock = clocks[name] ?: return drawable
        return setClock(drawable, clock, now)
    }

    private fun setClock(drawable: Drawable, clock: ClockSpec, now: Calendar): Drawable {
        val mutable = drawable.mutate()
        val layers = (mutable as? AdaptiveIconDrawable)?.foreground as? LayerDrawable
            ?: mutable as? LayerDrawable
            ?: return drawable
        val hour = now.get(Calendar.HOUR)
        val minute = now.get(Calendar.MINUTE)
        // Same arithmetic as Android's own launcher: a hand's level is minutes past its default
        if (clock.hourLayer in 0 until layers.numberOfLayers) {
            layers.getDrawable(clock.hourLayer).level =
                (hour + 12 - clock.defaultHour) % 12 * 60 + minute
        }
        if (clock.minuteLayer in 0 until layers.numberOfLayers) {
            layers.getDrawable(clock.minuteLayer).level =
                hour * 60 + (minute + 60 - clock.defaultMinute) % 60
        }
        // Icons are redrawn once a minute, so a second hand would only stand still
        if (clock.secondLayer in 0 until layers.numberOfLayers) {
            layers.setDrawable(clock.secondLayer, ColorDrawable(Color.TRANSPARENT))
        }
        return mutable
    }

    /**
     * An app the pack doesn't cover, made to match: its own icon shrunk onto the pack's
     * background, cut to the pack's shape, with the pack's overlay on top. Null if the pack
     * offers none of that. [seed] picks the background when the pack has several, the same
     * one for the same app every time.
     */
    fun wrap(icon: Drawable, sizePx: Int, seed: Int): Bitmap? {
        val back = backs.takeIf { it.isNotEmpty() }
            ?.let { resources.drawable(it[Math.floorMod(seed, it.size)]) }
        val maskDrawable = mask?.let(resources::drawable)
        val uponDrawable = upon?.let(resources::drawable)
        if (back == null && maskDrawable == null && uponDrawable == null) return null

        val result = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        back?.let { canvas.drawBitmap(it.toBitmap(sizePx, sizePx), 0f, 0f, null) }

        // The app's icon, smaller and in the middle, with the mask's shape cut away from it
        val inner = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val innerCanvas = Canvas(inner)
        val scaled = (sizePx * scale).toInt().coerceIn(1, sizePx)
        val offset = (sizePx - scaled) / 2f
        innerCanvas.drawBitmap(icon.toBitmap(scaled, scaled), offset, offset, null)
        maskDrawable?.let {
            val cutOut = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
            innerCanvas.drawBitmap(it.toBitmap(sizePx, sizePx), 0f, 0f, cutOut)
        }
        canvas.drawBitmap(inner, 0f, 0f, null)

        uponDrawable?.let { canvas.drawBitmap(it.toBitmap(sizePx, sizePx), 0f, 0f, null) }
        return result
    }

    companion object {
        /** Reads a pack's appfilter.xml. Slow for big packs; call off the main thread. */
        fun load(context: Context, packageName: String): IconPack? {
            val resources = PackResources.open(context, packageName) ?: return null
            // A pack without a (readable) appfilter.xml is no use
            return resources.readXml(context, "appfilter") { parse(resources, it) }
        }

        /**
         * Every icon the pack offers, by drawable name, for choosing one by hand. Packs list
         * them in `drawable.xml`; the ones named in `appfilter.xml` are added in case that
         * file is missing or incomplete. Call off the main thread.
         */
        fun iconNames(context: Context, packageName: String): List<String> {
            val resources = PackResources.open(context, packageName) ?: return emptyList()
            val names = LinkedHashSet<String>()
            resources.readXml(context, "drawable") { collectDrawables(it, names) }
            resources.readXml(context, "appfilter") { collectDrawables(it, names) }
            return names.toList()
        }

        private fun collectDrawables(parser: XmlPullParser, into: MutableSet<String>) {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
                parser.getAttributeValue(null, "drawable")?.takeIf { it.isNotBlank() }?.let(into::add)
            }
        }

        private fun parse(resources: PackResources, parser: XmlPullParser): IconPack {
            val names = HashMap<String, String>()
            val calendars = HashMap<String, String>()
            val clocks = HashMap<String, ClockSpec>()
            val backs = ArrayList<String>()
            var mask: String? = null
            var upon: String? = null
            var scale = 1f

            fun attribute(name: String): String? = parser.getAttributeValue(null, name)
            fun number(name: String, default: Int): Int = attribute(name)?.toIntOrNull() ?: default

            // "ComponentInfo{pkg/.Main}" -> the flattened name with the full class name
            fun component(): String? {
                val value = attribute("component") ?: return null
                if (!value.startsWith(PREFIX) || !value.endsWith("}")) return null
                val flat = value.substring(PREFIX.length, value.length - 1)
                return ComponentName.unflattenFromString(flat)?.flattenToString()
            }

            // img1, img2, ... in the order they are written
            fun images(): List<String> = (0 until parser.attributeCount)
                .filter { parser.getAttributeName(it).startsWith("img") }
                .map { parser.getAttributeValue(it) }
                .filter { it.isNotBlank() }

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "item" -> {
                        val component = component() ?: continue
                        val drawable = attribute("drawable") ?: continue
                        names.putIfAbsent(component, drawable)
                    }
                    "calendar" -> {
                        val component = component() ?: continue
                        val prefix = attribute("prefix") ?: continue
                        calendars.putIfAbsent(component, prefix)
                    }
                    "dynamic-clock" -> {
                        val drawable = attribute("drawable") ?: continue
                        clocks[drawable] = ClockSpec(
                            hourLayer = number("hourLayerIndex", -1),
                            minuteLayer = number("minuteLayerIndex", -1),
                            secondLayer = number("secondLayerIndex", -1),
                            defaultHour = number("defaultHour", 0),
                            defaultMinute = number("defaultMinute", 0),
                        )
                    }
                    "iconback" -> backs += images()
                    "iconmask" -> mask = images().firstOrNull() ?: mask
                    "iconupon" -> upon = images().firstOrNull() ?: upon
                    "scale" -> scale = attribute("factor")?.toFloatOrNull()?.coerceIn(0.3f, 1f) ?: scale
                }
            }
            return IconPack(resources, names, calendars, clocks, backs, mask, upon, scale)
        }

        private const val PREFIX = "ComponentInfo{"
    }
}
