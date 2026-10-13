package com.example.contentstudio.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/** Expose the same selection that is shown visually; never change domain state here. */
internal fun Modifier.accessibleSelection(value: Boolean) = semantics { selected = value }

/** A media time/quality value is not the percentage of the slider's arbitrary range. */
internal fun Modifier.accessibleSlider(label: String, value: String) = semantics {
    contentDescription = label
    stateDescription = value
}

internal fun Modifier.accessibleHeading() = semantics { heading() }
