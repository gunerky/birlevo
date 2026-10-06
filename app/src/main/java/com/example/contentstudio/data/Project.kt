package com.example.contentstudio.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ProjectKind { PRESENTATION, VIDEO }

data class Slide(val heading: String = "", val body: String = "", val imageUri: String? = null,
    val id: String = UUID.randomUUID().toString(), val layout: SlideLayout = SlideLayout.TEXT_IMAGE)

data class StudioProject(
    val id: String,
    val kind: ProjectKind,
    val title: String,
    val updatedAt: Long,
    val slides: List<Slide> = emptyList(),
    val sourceUri: String? = null,
    val sourceName: String? = null,
    val theme: PresentationTheme = PresentationTheme.CLASSIC,
)

object ProjectCodec {
    private const val VERSION = 1

    fun encode(projects: List<StudioProject>): String = JSONObject().apply {
        put("version", VERSION)
        put("projects", JSONArray().apply {
            projects.forEach { project ->
                put(JSONObject().apply {
                    put("id", project.id)
                    put("kind", project.kind.name)
                    put("title", project.title)
                    put("updatedAt", project.updatedAt)
                    put("theme", project.theme.name)
                    put("sourceUri", project.sourceUri ?: JSONObject.NULL)
                    put("sourceName", project.sourceName ?: JSONObject.NULL)
                    put("slides", JSONArray().apply {
                        project.slides.forEach { slide ->
                            put(JSONObject().put("id", slide.id).put("layout", slide.layout.name)
                                .put("heading", slide.heading).put("body", slide.body)
                                .put("imageUri", slide.imageUri ?: JSONObject.NULL))
                        }
                    })
                })
            }
        })
    }.toString()

    fun decode(json: String): List<StudioProject> {
        val root = JSONObject(json)
        require(root.getInt("version") == VERSION) { "Unsupported project schema" }
        val projects = root.getJSONArray("projects")
        val result = (0 until projects.length()).map { index ->
            val item = projects.getJSONObject(index)
            val slides = item.getJSONArray("slides")
            StudioProject(
                id = item.getString("id").also { require(it.isNotBlank()) },
                kind = ProjectKind.valueOf(item.getString("kind")),
                title = item.getString("title"),
                updatedAt = item.getLong("updatedAt"),
                slides = (0 until slides.length()).map { slideIndex ->
                    val slide = slides.getJSONObject(slideIndex)
                    Slide(slide.getString("heading"), slide.getString("body"),
                        if (slide.has("imageUri") && !slide.isNull("imageUri")) slide.getString("imageUri") else null,
                        if (slide.has("id")) slide.getString("id").also { require(it.isNotBlank()) }
                        else UUID.nameUUIDFromBytes("${item.getString("id")}:slide:$slideIndex".toByteArray(Charsets.UTF_8)).toString(),
                        SlideLayout.valueOf(slide.optString("layout", SlideLayout.TEXT_IMAGE.name)))
                },
                sourceUri = if (item.isNull("sourceUri")) null else item.getString("sourceUri"),
                sourceName = if (item.isNull("sourceName")) null else item.getString("sourceName"),
                theme = PresentationTheme.valueOf(item.optString("theme", PresentationTheme.CLASSIC.name)),
            )
        }
        require(result.map { it.id }.distinct().size == result.size) { "Duplicate project ids" }
        require(result.all { project -> project.slides.map { it.id }.distinct().size == project.slides.size }) {
            "Duplicate slide ids"
        }
        return result
    }
}
