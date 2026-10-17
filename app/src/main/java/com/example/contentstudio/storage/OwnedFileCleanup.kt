package com.example.contentstudio.storage

import java.io.File
import java.io.IOException

/** Age is only a grace period. Ownership and recovered references must be checked first. */
object OwnedFileCleanup {
    const val DAY = 24L * 60 * 60 * 1000

    fun files(
        folder: File,
        names: Regex,
        retained: Set<File> = emptySet(),
        age: Long = DAY,
        now: Long = System.currentTimeMillis(),
    ) {
        require(age > 0)
        val root = ownedRoot(folder) ?: return
        val keep = retained.map { canonical(it) ?: return }.toSet()
        folder.listFiles()?.forEach { file ->
            val path = canonical(file) ?: return@forEach
            if (path == File(root, file.name) && file.isFile && names.matches(file.name) &&
                path !in keep && file.lastModified() in 1 until (now - age)) file.delete()
        }
    }

    /** Flat, known scratch directories only; unknown children prevent any deletion. No recursion. */
    fun directories(
        folder: File,
        directoryNames: Regex,
        childNames: Regex,
        retained: Set<File> = emptySet(),
        age: Long = 7 * DAY,
        now: Long = System.currentTimeMillis(),
    ) {
        require(age > 0)
        val root = ownedRoot(folder) ?: return
        val keep = retained.map { canonical(it) ?: return }.toSet()
        folder.listFiles()?.forEach { directory ->
            if (!ownedChild(root, directory) || !directory.isDirectory ||
                !directoryNames.matches(directory.name) || directory.lastModified() !in 1 until (now - age)) return@forEach
            val directoryPath = canonical(directory) ?: return@forEach
            if (keep.any { it == directoryPath || it.parentFile == directoryPath }) return@forEach
            val children = directory.listFiles() ?: return@forEach
            if (children.any { !ownedChild(directoryPath, it) || !it.isFile ||
                    !childNames.matches(it.name) || it.lastModified() !in 1 until (now - age) }) return@forEach
            children.forEach { it.delete() }
            directory.delete() // Failed child deletes keep the directory intact.
        }
    }

    private fun ownedChild(root: File, child: File) = canonical(child) == File(root, child.name)

    private fun ownedRoot(folder: File): File? {
        val parent = canonical(folder.absoluteFile.parentFile ?: return null) ?: return null
        return canonical(folder)?.takeIf { it == File(parent, folder.name) }
    }

    private fun canonical(file: File): File? = try { file.canonicalFile }
    catch (_: IOException) { null }
    catch (_: SecurityException) { null }
}
