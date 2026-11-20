package com.kayf.risco

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * What risco remembers about each app between starts. Reading an app's name and icon means opening
 * its installed files, which for every app took over a second with icons on, so a start only reads
 * the apps that are new or were updated since, and takes the rest from here.
 *
 * Everything is thrown away when [header] changes: the phone's languages (names are translated)
 * and the size icons are drawn at.
 */
class AppCache(val header: String, val entries: Map<String, Entry>) {
    /** An app's own name, its grey icon as PNG if icons were on, and the file it was read from. */
    class Entry(val source: String, val label: String, val icon: ByteArray?)

    /**
     * The saved [key] if it still holds for the app now installed at [source]. Android installs
     * every update in a new folder, so a different [source] means the app changed. With [icons] on,
     * an entry saved while they were off has none and is read again.
     */
    fun fresh(key: String, source: String, icons: Boolean): Entry? =
        entries[key]?.takeIf { it.source == source && (!icons || it.icon != null) }

    fun write(output: OutputStream) {
        val out = DataOutputStream(output.buffered())
        out.writeInt(VERSION)
        out.writeUTF(header)
        out.writeInt(entries.size)
        for ((key, entry) in entries) {
            out.writeUTF(key)
            out.writeUTF(entry.source)
            out.writeUTF(entry.label)
            val icon = entry.icon
            out.writeInt(icon?.size ?: -1)
            if (icon != null) out.write(icon)
        }
        out.flush()
    }

    companion object {
        private const val VERSION = 1
        private const val MOST_ENTRIES = 100_000
        private const val BIGGEST_ICON = 1 shl 20

        fun header(locales: String, iconSize: Int, iconDpi: Int) = "$locales|$iconSize|$iconDpi"

        /** The cache [write] saved, or an empty one if there's none, it's damaged, or [header] changed. */
        fun read(input: InputStream?, header: String): AppCache {
            val empty = AppCache(header, emptyMap())
            if (input == null) return empty
            return try {
                val data = DataInputStream(input.buffered())
                if (data.readInt() != VERSION || data.readUTF() != header) return empty
                val count = data.readInt()
                if (count !in 0..MOST_ENTRIES) return empty
                val entries = HashMap<String, Entry>(count)
                repeat(count) {
                    val key = data.readUTF()
                    val source = data.readUTF()
                    val label = data.readUTF()
                    val size = data.readInt()
                    if (size > BIGGEST_ICON) return empty
                    val icon = if (size < 0) null else ByteArray(size).also { data.readFully(it) }
                    entries[key] = Entry(source, label, icon)
                }
                AppCache(header, entries)
            } catch (e: IOException) {
                empty
            }
        }
    }
}
