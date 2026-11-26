package dk.emilbolet.quectolaunch.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * What a backup file holds: the home layout and every saved setting (icons picked by hand
 * included). Android widgets from other apps are left out, because Android ties a widget to
 * the installation that added it; [skippedWidgets] says how many there were.
 *
 * The file is JSON, written and read with Android's own org.json.
 */
class Backup(
    /** Every saved item but Android widgets. IDs are only used to find a folder's contents. */
    val items: List<HomeItem>,
    val pages: Int,
    /** DataStore entries by name; values are Boolean, Int, Long, Float, Double, String or Set<String>. */
    val settings: Map<String, Any>,
    val skippedWidgets: Int,
    /** When it was made, in milliseconds since 1970. */
    val createdAt: Long,
) {
    /** Apps on the home screens, in the dock and in folders. */
    val appCount: Int get() = items.count { it.appKey != null }

    fun toJson(): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("createdAt", createdAt)
        .put("pages", pages)
        .put("skippedWidgets", skippedWidgets)
        .put("items", JSONArray().apply { items.forEach { put(itemToJson(it)) } })
        .put("settings", JSONObject().apply {
            settings.forEach { (name, value) -> put(name, settingToJson(value)) }
        })
        .toString(2)

    class NotABackup : Exception()

    companion object {
        private const val FORMAT = "quectolaunch-backup"
        private const val VERSION = 1

        fun fromLayout(
            items: List<HomeItem>,
            pages: Int,
            settings: Map<String, Any>,
            createdAt: Long,
        ): Backup {
            val androidWidgets = items.filter { it.appWidgetId != null }
            return Backup(
                items = items - androidWidgets.toSet(),
                pages = pages,
                settings = settings,
                skippedWidgets = androidWidgets.size,
                createdAt = createdAt,
            )
        }

        /** Throws [NotABackup] if [text] isn't a backup this version can read. */
        fun fromJson(text: String): Backup = try {
            val json = JSONObject(text)
            if (json.optString("format") != FORMAT || json.optInt("version") !in 1..VERSION) {
                throw NotABackup()
            }
            val items = json.getJSONArray("items").let { array ->
                List(array.length()) { itemFromJson(array.getJSONObject(it)) }
            }
            val settingsJson = json.getJSONObject("settings")
            val settings = buildMap {
                settingsJson.keys().forEach { name ->
                    settingFromJson(settingsJson.getJSONObject(name))?.let { put(name, it) }
                }
            }
            Backup(
                items = items,
                pages = json.optInt("pages", 1).coerceAtLeast(1),
                settings = settings,
                skippedWidgets = json.optInt("skippedWidgets"),
                createdAt = json.optLong("createdAt"),
            )
        } catch (e: JSONException) {
            throw NotABackup()
        }

        private fun itemToJson(item: HomeItem) = JSONObject()
            .put("id", item.id)
            .put("container", item.container)
            .put("page", item.page)
            .put("position", item.position)
            .putOpt("appKey", item.appKey)
            .putOpt("title", item.title)
            .put("spanX", item.spanX)
            .put("spanY", item.spanY)
            .putOpt("builtIn", item.builtIn)

        private fun itemFromJson(json: JSONObject) = HomeItem(
            id = json.getLong("id"),
            container = json.getLong("container"),
            page = json.optInt("page"),
            position = json.getInt("position"),
            appKey = json.optStringOrNull("appKey"),
            title = json.optStringOrNull("title"),
            spanX = json.optInt("spanX", 1).coerceAtLeast(1),
            spanY = json.optInt("spanY", 1).coerceAtLeast(1),
            builtIn = json.optStringOrNull("builtIn"),
        )

        private fun JSONObject.optStringOrNull(name: String): String? =
            if (has(name) && !isNull(name)) getString(name) else null

        // Each setting keeps its type, because DataStore keys are typed
        private fun settingToJson(value: Any): JSONObject = when (value) {
            is Boolean -> JSONObject().put("type", "boolean").put("value", value)
            is Int -> JSONObject().put("type", "int").put("value", value)
            is Long -> JSONObject().put("type", "long").put("value", value)
            is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
            is Double -> JSONObject().put("type", "double").put("value", value)
            is Set<*> -> JSONObject().put("type", "stringSet").put("value", JSONArray(value.map { it.toString() }))
            else -> JSONObject().put("type", "string").put("value", value.toString())
        }

        /** Null for a type this version doesn't know; that setting is skipped. */
        private fun settingFromJson(json: JSONObject): Any? = when (json.optString("type")) {
            "boolean" -> json.getBoolean("value")
            "int" -> json.getInt("value")
            "long" -> json.getLong("value")
            "float" -> json.getDouble("value").toFloat()
            "double" -> json.getDouble("value")
            "string" -> json.getString("value")
            "stringSet" -> json.getJSONArray("value").let { array ->
                (0 until array.length()).map { array.getString(it) }.toSet()
            }
            else -> null
        }
    }
}
