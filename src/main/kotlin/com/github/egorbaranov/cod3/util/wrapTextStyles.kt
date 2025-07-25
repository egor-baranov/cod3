package com.github.egorbaranov.cod3.util

fun wrapTextStyles(text: String): String {
    var result = text.trim()

    // Horizontal rule: --- → <hr>
    result = result.replace(Regex("(?m)^---\\s*$"), "<hr>")

    // Headers
    result = result.replace(Regex("(?m)^###\\s+(.*)$")) {
        "<h3>${it.groupValues[1]}</h3>"
    }

    result = result.replace(Regex("(?m)^##\\s+(.*)$")) {
        "<h2>${it.groupValues[1]}</h2>"
    }

    result = result.replace(Regex("(?m)^#\\s+(.*)$")) {
        "<h1>${it.groupValues[1]}</h1>"
    }

//    // List items: convert "- item" to <li>...</li> inside <ul>
//    result = result.replace(Regex("(?m)(^\\s*- .+$)+")) { match ->
//        val items = match.value.lines().joinToString("") { line ->
//            "<li>${line.removePrefix("- ").trim()}</li>"
//        }
//        "<ul>$items</ul>"
//    }

    // Bold, Italic, Underline
    result = result.replace(Regex("(?<!\\*)\\*\\*(.+?)\\*\\*(?!\\*)")) { "<b>${it.groupValues[1]}</b>" }
    result = result.replace(Regex("(?<!\\*)\\*(.+?)\\*(?!\\*)")) { "<i>${it.groupValues[1]}</i>" }
    result = result.replace(Regex("__(.+?)__")) { "<u>${it.groupValues[1]}</u>" }

    // Paragraphs and line breaks
//    result = result.replace("\n\n", "<p></p>")  // double newlines = paragraph break
    result = result.replace("\n", "<br>")       // single newline = line break

    return result
}
