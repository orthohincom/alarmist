package com.orthohin.alarmist.core.sounds

/** File names for imported sounds: the original name made safe, and unique in the sound folder. Pure. */
internal object SoundFileNames {
    private const val MAX_LENGTH = 80
    private val UNSAFE = Regex("""[\u0000-\u001f/\\:*?"<>|]""")

    fun safe(displayName: String?): String {
        val cleaned = displayName.orEmpty().replace(UNSAFE, "_").trim().trimStart('.')
        if (cleaned.isEmpty()) return "sound"
        val ext = cleaned.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 }
        val base = if (ext == null) cleaned else cleaned.dropLast(ext.length + 1)
        val maxBase = MAX_LENGTH - (ext?.length?.plus(1) ?: 0)
        val shortBase = base.take(maxBase).trimEnd().ifEmpty { "sound" }
        return if (ext == null) shortBase else "$shortBase.$ext"
    }

    /** [name], or "name (2).ext", "name (3).ext", … if taken. */
    fun unique(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val base = name.substring(0, dot)
        val ext = name.substring(dot)
        return generateSequence(2) { it + 1 }.map { "$base ($it)$ext" }.first { it !in taken }
    }

    /** What the user sees: the file name without its extension. */
    fun title(fileName: String): String = fileName.substringBeforeLast('.').ifEmpty { fileName }
}
