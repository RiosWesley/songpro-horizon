package com.andrerinas.openheadunit.byd.theme

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.zip.Inflater

/**
 * Qt binary resource files (RCC, format 3), as the BYD cluster loads them.
 *
 * Reading and writing follow DashCast's `build_live_marker.py` (MIT, Cedric Carre,
 * https://github.com/Kiroha/byd-dashcast): a record's stored bytes are kept as they are, so the
 * stock entries of a carrier stay byte for byte what the car shipped.
 */
object Rcc {

    /** One file in an RCC: its path, locale, flags (1 = zlib) and its bytes as stored and as read. */
    class Entry(val path: String, val country: Int, val language: Int, val flags: Int, val stored: ByteArray) {
        val data: ByteArray by lazy { if (flags and 1 != 0) inflate(stored) else stored }
    }

    /**
     * An uncompressed record of [data]. Never zlib: deflate output differs between implementations,
     * and the carrier must come out the same on the car as offline.
     */
    fun entry(path: String, data: ByteArray): Entry = Entry(path, 0, 1, 0, data)

    /** Every file entry, in tree order. */
    fun decode(bytes: ByteArray): List<Entry> {
        val rows = ArrayList<Entry>()
        walk(ByteBuffer.wrap(bytes)) { path, b, o, data ->
            rows.add(record(b, o, data, path))
            true
        }
        return rows
    }

    /** The data of one entry of a (large) RCC file, read through a memory map; fails unless exactly one matches. */
    fun read(file: File, alias: String): ByteArray {
        RandomAccessFile(file, "r").use { raf ->
            val b = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
            val found = ArrayList<ByteArray>()
            walk(b) { path, buf, o, data ->
                if (path == alias) found.add(record(buf, o, data, path).data)
                true
            }
            check(found.size == 1) { "$alias: expected exactly one entry in ${file.name}, found ${found.size}" }
            return found[0]
        }
    }

    /** Writes [entries] as one RCC; children sorted by Qt's name hash, as rcc does. */
    fun build(entries: List<Entry>): ByteArray {
        val root = Node("")
        for (e in entries) {
            val parts = e.path.split("/")
            var node = root
            for (part in parts.dropLast(1)) node = node.children!!.getOrPut(part) { Node(part) }
            check(node.children!!.put(parts.last(), Node(parts.last(), e)) == null) { "duplicate entry ${e.path}" }
        }
        val nodes = arrayListOf(root)
        var i = 0
        while (i < nodes.size) {
            val node = nodes[i++]
            val children = node.children ?: continue
            node.first = nodes.size
            node.count = children.size
            nodes.addAll(children.values.sortedWith(compareBy<Node>({ qhash(it.name) }, { it.name })))
        }

        val names = ByteArrayOutputStream()
        val nameOffsets = HashMap<String, Int>()
        val payload = ByteArrayOutputStream()
        val tree = ByteArrayOutputStream()
        val namesOut = DataOutputStream(names)
        val payloadOut = DataOutputStream(payload)
        val treeOut = DataOutputStream(tree)
        for (node in nodes) {
            val nameOffset = nameOffsets.getOrPut(node.name) {
                val offset = names.size()
                namesOut.writeShort(node.name.length)
                namesOut.writeInt(qhash(node.name).toInt())
                namesOut.write(node.name.toByteArray(Charsets.UTF_16BE))
                offset
            }
            val record = node.record
            treeOut.writeInt(nameOffset)
            if (record == null) {
                treeOut.writeShort(2)
                treeOut.writeInt(node.count)
                treeOut.writeInt(node.first)
            } else {
                treeOut.writeShort(record.flags)
                treeOut.writeShort(record.country)
                treeOut.writeShort(record.language)
                treeOut.writeInt(payload.size())
                payloadOut.writeInt(record.stored.size)
                payloadOut.write(record.stored)
            }
            treeOut.writeLong(0)
        }

        val dataOffset = 24
        val namesOffset = dataOffset + payload.size()
        val treeOffset = namesOffset + names.size()
        val out = ByteArrayOutputStream(treeOffset + tree.size())
        DataOutputStream(out).apply {
            write("qres".toByteArray(Charsets.US_ASCII))
            writeInt(3)
            writeInt(treeOffset)
            writeInt(dataOffset)
            writeInt(namesOffset)
            writeInt(1)
        }
        payload.writeTo(out)
        names.writeTo(out)
        tree.writeTo(out)
        return out.toByteArray()
    }

    /** Qt's qt_hash, which orders the children of each directory. */
    fun qhash(s: String): Long {
        var h = 0L
        for (c in s) {
            h = (h shl 4) + c.code
            h = h xor ((h and 0xf0000000L) shr 23)
            h = h and 0x0fffffffL
        }
        return h
    }

    private class Node(val name: String, val record: Entry? = null) {
        val children: LinkedHashMap<String, Node>? = if (record == null) LinkedHashMap() else null
        var first = 0
        var count = 0
    }

    /** Calls [visit] with (path, buffer, tree record offset, data offset) for every file; stops when it returns false. */
    private fun walk(b: ByteBuffer, visit: (String, ByteBuffer, Int, Int) -> Boolean) {
        check(b.get(0) == 'q'.code.toByte() && b.get(1) == 'r'.code.toByte() && b.get(2) == 'e'.code.toByte() && b.get(3) == 's'.code.toByte()) { "not an RCC" }
        check(b.getInt(4) == 3) { "RCC format ${b.getInt(4)}, expected 3" }
        val tree = b.getInt(8)
        val data = b.getInt(12)
        val names = b.getInt(16)

        fun node(i: Int, parent: String): Boolean {
            val o = tree + 22 * i
            val nameOffset = b.getInt(o)
            val flags = b.getShort(o + 4).toInt() and 0xffff
            val path = if (i == 0) "" else {
                val length = b.getShort(names + nameOffset).toInt() and 0xffff
                val chars = CharArray(length) { b.getChar(names + nameOffset + 6 + 2 * it) }
                if (parent.isEmpty()) String(chars) else parent + "/" + String(chars)
            }
            if (flags and 2 != 0) {
                val count = b.getInt(o + 6)
                val first = b.getInt(o + 10)
                for (j in first until first + count) if (!node(j, path)) return false
                return true
            }
            return visit(path, b, o, data)
        }
        node(0, "")
    }

    private fun record(b: ByteBuffer, o: Int, data: Int, path: String): Entry {
        val flags = b.getShort(o + 4).toInt() and 0xffff
        val country = b.getShort(o + 6).toInt() and 0xffff
        val language = b.getShort(o + 8).toInt() and 0xffff
        val offset = data + b.getInt(o + 10)
        val stored = ByteArray(b.getInt(offset))
        for (k in stored.indices) stored[k] = b.get(offset + 4 + k)
        return Entry(path, country, language, flags, stored)
    }

    private fun inflate(stored: ByteArray): ByteArray {
        val size = ByteBuffer.wrap(stored, 0, 4).int
        val inflater = Inflater()
        try {
            inflater.setInput(stored, 4, stored.size - 4)
            val out = ByteArray(size)
            var n = 0
            while (n < size && !inflater.finished()) {
                val got = inflater.inflate(out, n, size - n)
                if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                n += got
            }
            check(n == size) { "zlib entry is $n bytes, header says $size" }
            return out
        } finally {
            inflater.end()
        }
    }
}
