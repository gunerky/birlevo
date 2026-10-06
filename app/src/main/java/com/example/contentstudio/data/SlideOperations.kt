package com.example.contentstudio.data

import java.util.UUID

object SlideOperations {
    const val MAX_SLIDES = 100

    fun duplicate(slides: List<Slide>, id: String): List<Slide> {
        val index = slides.indexOfFirst { it.id == id }
        if (index < 0 || slides.size >= MAX_SLIDES) return slides
        return slides.toMutableList().apply { add(index + 1, slides[index].copy(id = UUID.randomUUID().toString())) }
    }

    fun remove(slides: List<Slide>, id: String): List<Slide> =
        if (slides.size <= 1) slides else slides.filterNot { it.id == id }

    fun move(slides: List<Slide>, id: String, offset: Int): List<Slide> {
        val index = slides.indexOfFirst { it.id == id }
        val target = index + offset
        if (index < 0 || offset !in listOf(-1, 1) || target !in slides.indices) return slides
        return slides.toMutableList().apply { add(target, removeAt(index)) }
    }
}
