package com.neko.video.ui.component.miuix.modifier

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope

/**
 * Pointer helper used by liquid-glass floating bar interactions.
 * Adapted from compose-miuix-ui example patterns (Apache-2.0).
 */
suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)
        val completed = drag(down.id) { change ->
            val dragAmount = change.position - change.previousPosition
            onDrag(change, dragAmount)
        }
        if (completed) onDragEnd() else onDragCancel()
    }
}

@Suppress("unused")
private suspend fun AwaitPointerEventScope.unused() = Unit
