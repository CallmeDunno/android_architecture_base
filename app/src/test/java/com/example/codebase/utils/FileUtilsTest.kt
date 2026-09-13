package com.example.codebase.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class FileUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `sanitizeFileName replaces illegal and control characters`() {
        assertEquals("a_b_c_d_e_f_g_h_i_", FileUtils.sanitizeFileName("a/b\\c:d*e?f\"g<h>i|"))
        assertEquals("tab_name", FileUtils.sanitizeFileName("tab\tname"))
    }

    @Test
    fun `sanitizeFileName trims dots and whitespace and falls back when nothing is left`() {
        assertEquals("report.pdf", FileUtils.sanitizeFileName("  report.pdf. "))
        assertEquals("file", FileUtils.sanitizeFileName(".."))
        assertEquals("file", FileUtils.sanitizeFileName("   "))
    }

    @Test
    fun `sanitizeFileName cuts to maxLength without splitting a surrogate pair`() {
        assertEquals("abcde", FileUtils.sanitizeFileName("abcdefgh", maxLength = 5))
        assertEquals("ab", FileUtils.sanitizeFileName("ab.cdef", maxLength = 3))
        assertEquals("ab", FileUtils.sanitizeFileName("ab😀", maxLength = 3))
    }

    @Test
    fun `resolveChild keeps paths inside the base directory`() {
        val base = tempFolder.newFolder("base")

        assertEquals(
            File(base, "notes/today.txt").canonicalFile,
            FileUtils.resolveChild(base, "notes/today.txt")
        )
    }

    @Test
    fun `resolveChild rejects escaping, absolute, blank and self paths`() {
        val base = tempFolder.newFolder("base")

        assertNull(FileUtils.resolveChild(base, "../outside.txt"))
        assertNull(FileUtils.resolveChild(base, "notes/../../outside.txt"))
        assertNull(FileUtils.resolveChild(base, "../base-evil/x.txt"))
        assertNull(FileUtils.resolveChild(base, File(base, "x.txt").absolutePath))
        assertNull(FileUtils.resolveChild(base, " "))
        assertNull(FileUtils.resolveChild(base, "."))
    }

    @Test
    fun `atomic write creates parents, replaces existing content and leaves no temp file`() {
        val file = File(tempFolder.root, "nested/dir/data.txt")

        assertTrue(FileUtils.writeTextAtomically(file, "first"))
        assertTrue(FileUtils.writeTextAtomically(file, "second"))

        assertEquals("second", FileUtils.readTextOrNull(file))
        assertEquals(listOf("data.txt"), file.parentFile!!.list()!!.toList())
    }

    @Test
    fun `atomic write refuses to replace a directory`() {
        val dir = tempFolder.newFolder("target")

        assertFalse(FileUtils.writeBytesAtomically(dir, byteArrayOf(1)))
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `copyStreamAtomically writes the stream content`() {
        val file = File(tempFolder.root, "copy.bin")
        val bytes = byteArrayOf(1, 2, 3)

        assertTrue(FileUtils.copyStreamAtomically(ByteArrayInputStream(bytes), file))

        assertArrayEquals(bytes, FileUtils.readBytesOrNull(file))
    }

    @Test
    fun `read returns null for missing files and directories`() {
        assertNull(FileUtils.readTextOrNull(File(tempFolder.root, "missing.txt")))
        assertNull(FileUtils.readBytesOrNull(tempFolder.root))
    }

    @Test
    fun `sizeOf sums files recursively`() {
        val dir = tempFolder.newFolder("sized")
        File(dir, "a.bin").writeBytes(ByteArray(10))
        File(dir, "sub").mkdirs()
        File(dir, "sub/b.bin").writeBytes(ByteArray(5))

        assertEquals(15L, FileUtils.sizeOf(dir))
        assertEquals(10L, FileUtils.sizeOf(File(dir, "a.bin")))
        assertEquals(0L, FileUtils.sizeOf(File(dir, "missing")))
    }

    @Test
    fun `deleteContents empties the directory but keeps it`() {
        val dir = tempFolder.newFolder("cache")
        File(dir, "sub").mkdirs()
        File(dir, "sub/x.txt").writeText("x")
        File(dir, "y.txt").writeText("y")

        assertTrue(FileUtils.deleteContents(dir))

        assertTrue(dir.isDirectory)
        assertEquals(0, dir.list()!!.size)
        assertTrue(FileUtils.deleteContents(File(tempFolder.root, "missing")))
    }

    @Test
    fun `uniqueFile numbers the name until it is free, keeping the extension`() {
        val dir = tempFolder.newFolder("pictures")
        File(dir, "photo.jpg").writeText("")
        File(dir, "photo (1).jpg").writeText("")
        File(dir, "notes").writeText("")

        assertEquals(File(dir, "photo (2).jpg"), FileUtils.uniqueFile(dir, "photo.jpg"))
        assertEquals(File(dir, "notes (1)"), FileUtils.uniqueFile(dir, "notes"))
        assertEquals(File(dir, "new.png"), FileUtils.uniqueFile(dir, "new.png"))
    }

    @Test
    fun `getExtension handles case, paths and dotfiles`() {
        assertEquals("jpg", FileUtils.getExtension("photo.JPG"))
        assertEquals("gz", FileUtils.getExtension("dir.v2/archive.tar.gz"))
        assertEquals("", FileUtils.getExtension(".gitignore"))
        assertEquals("", FileUtils.getExtension("README"))
        assertEquals("", FileUtils.getExtension("trailing."))
    }
}
