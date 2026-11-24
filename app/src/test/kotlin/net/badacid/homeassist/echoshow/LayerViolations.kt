package net.badacid.homeassist.echoshow

/** Which layers each layer may import, besides itself. A layer not listed here may import anything. */
private val ALLOWED: Map<String, Set<String>> = mapOf(
    "screens" to setOf("devices", "core"),
    "devices" to setOf("core"),
    "platform" to setOf("core"),
    "core" to emptySet(),
)

private val IMPORT = Regex("""^import\s+net\.badacid\.homeassist\.echoshow\.(\w+)""", RegexOption.MULTILINE)

/**
 * Lists every import that breaks the layer rules, as "<file>: imports <layer>".
 * `files` maps a path relative to the app's package folder ("screens/Home.kt") to the file's text.
 */
fun layerViolations(files: Map<String, String>): List<String> =
    files.flatMap { (path, text) ->
        val layer = path.substringBefore('/', missingDelimiterValue = "")
        val allowed = ALLOWED[layer] ?: return@flatMap emptyList()
        IMPORT.findAll(text)
            .map { it.groupValues[1] }
            .filter { it != layer && it !in allowed }
            .distinct()
            .map { "$path: imports $it" }
            .toList()
    }.sorted()
