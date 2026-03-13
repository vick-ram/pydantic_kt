package com.pydantic.plugin.utils

fun extractClassContent(content: String, className: String): String {
    // Look for "class Name", but handle potential annotations or keywords before it
    val start = content.indexOf("class $className")
    if (start == -1) return ""

    var braceCount = 0
    var parenCount = 0
    var hasStarted = false
    var end = content.length

    for (i in start until content.length) {
        val char = content[i]
        when (char) {
            '(' -> {
                parenCount++
                hasStarted = true
            }

            ')' -> {
                parenCount--
                if (parenCount == 0 && braceCount == 0 && hasStarted) {
                    // Check if a brace starts immediately after (ignoring whitespace)
                    val remaining = content.substring(i + 1).trimStart()
                    if (!remaining.startsWith("{")) {
                        end = i + 1
                        break
                    }
                }
            }

            '{' -> {
                braceCount++
                hasStarted = true
            }

            '}' -> {
                braceCount--
                if (braceCount == 0 && parenCount == 0 && hasStarted) {
                    end = i + 1
                    break
                }
            }
        }
    }

    // Fallback: If no delimiters were found, capture until the next class or end of file
    return content.substring(start, end)
}