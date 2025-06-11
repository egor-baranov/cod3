package com.github.egorbaranov.cod3.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font

fun highlightSelectedLinesWithHintColor(editor: Editor): RangeHighlighter? {
    val selectionModel = editor.selectionModel
    if (!selectionModel.hasSelection()) return null

    val markupModel = editor.markupModel
    val attributes = TextAttributes(
        null,
        JBColor.background().brighter(),
        null,
        null,
        Font.PLAIN
    )

    return markupModel.addRangeHighlighter(
        selectionModel.selectionStart,
        selectionModel.selectionEnd,
        HighlighterLayer.ADDITIONAL_SYNTAX,
        attributes,
        HighlighterTargetArea.EXACT_RANGE
    )
}