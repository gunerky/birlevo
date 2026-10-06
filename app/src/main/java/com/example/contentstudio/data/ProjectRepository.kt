package com.example.contentstudio.data

import android.content.Context
import android.util.AtomicFile
import java.io.File

interface ProjectStorage {
    fun load(): List<StudioProject>
    fun save(projects: List<StudioProject>)
}

class ProjectRepository(context: Context) : ProjectStorage {
    private val storage = AtomicFile(File(context.filesDir, "projects.json"))

    override fun load(): List<StudioProject> {
        // A backup from an interrupted write is recovered by AtomicFile.openRead().
        if (!storage.baseFile.exists() && !File(storage.baseFile.path + ".bak").exists()) {
            return emptyList()
        }
        return storage.openRead().use { ProjectCodec.decode(it.readBytes().toString(Charsets.UTF_8)) }
    }

    override fun save(projects: List<StudioProject>) {
        val bytes = ProjectCodec.encode(projects).toByteArray(Charsets.UTF_8)
        val output = storage.startWrite()
        try {
            output.write(bytes)
            storage.finishWrite(output)
        } catch (error: Exception) {
            storage.failWrite(output)
            throw error
        }
    }
}
