package com.lanraragi.reader.data.catalog

/** LANraragi clauses are comma separated; quotes surround the entire exact clause. */
object SearchQueryCodec {
    data class Clause(val value: String, val excluded: Boolean, val exact: Boolean, val start: Int, val end: Int)
    data class Edit(val text: String, val cursor: Int)

    fun parse(text: String): List<Clause> {
        val result = mutableListOf<Clause>()
        var start = 0
        var quoted = false
        for (i in 0..text.length) {
            if (i < text.length && text[i] == '"') quoted = !quoted
            if (i == text.length || (text[i] == ',' && !quoted)) {
                val raw = text.substring(start, i).trim()
                val excluded = raw.startsWith('-')
                val value = raw.removePrefix("-").trimStart()
                val exact = value.startsWith('"') || value.endsWith('$')
                val decoded = if (value.startsWith('"')) value.removePrefix("\"").removeSuffix("$").removeSuffix("\"")
                    else value.removeSuffix("$")
                result += Clause(decoded, excluded, exact, start, i)
                start = i + 1
            }
        }
        return result
    }

    fun validationError(text: String): String? = when {
        text.count { it == '"' } % 2 != 0 -> "双引号尚未闭合；精确匹配可写为 artist:name$"
        parse(text).any { it.value.startsWith('~') } -> "当前服务器不支持 ~ 运算符，请用逗号组合条件"
        parse(text).any { '"' in it.value } -> "请将整个条件放入双引号，或使用末尾 $ 精确匹配"
        else -> null
    }

    fun active(text: String, cursor: Int): Clause = parse(text).firstOrNull {
        cursor.coerceIn(0, text.length) in it.start..it.end
    } ?: Clause("", false, false, text.length, text.length)

    fun exactTag(full: String, excluded: Boolean = false): String {
        require(full.isNotBlank() && full.none { it in "\",*?_%" }) { "该标签含有服务器搜索保留符号，请手动编辑查询" }
        return (if (excluded) "-" else "") + full.trim() + "$"
    }

    fun replace(text: String, start: Int, end: Int, full: String, excluded: Boolean? = null): Edit {
        val token = active(text, start)
        val replacement = exactTag(full, excluded ?: token.excluded)
        val from = if (start != end) minOf(start, end) else token.start
        val to = if (start != end) maxOf(start, end) else token.end
        val clauses = parse(text)
        // Repeated picks must not grow the query, or create contradictory conditions.
        val duplicate = clauses.firstOrNull { it.start != token.start && it.value.equals(full, true) }
        if (duplicate != null) {
            require(duplicate.excluded == (excluded ?: token.excluded)) { "该标签已有相反条件，请先移除原条件再选择包含或排除" }
            val revised = clauses.filter { it.start != token.start && it.start != duplicate.start }
                .map { text.substring(it.start, it.end).trim() }.filter(String::isNotBlank) + replacement
            val result = revised.joinToString(",") + ","
            return Edit(result, result.length)
        }
        val suffix = text.substring(to)
        val inserted = replacement + if (suffix.isEmpty()) "," else ""
        val result = text.substring(0, from) + inserted + suffix
        return Edit(result, from + inserted.length)
    }

    fun matches(text: String, title: String, tags: List<String>, pageCount: Int = 0, progress: Int = 0): Boolean {
        require(validationError(text) == null) { validationError(text).orEmpty() }
        return parse(text).filter { it.value.isNotBlank() }.all { clause ->
            val numeric = Regex("^(pages|read):(>=|<=|>|<)?(\\d+)$").matchEntire(clause.value)
            val hit = if (numeric != null) {
                val value = if (numeric.groupValues[1] == "pages") pageCount else progress
                val target = numeric.groupValues[3].toIntOrNull() ?: Int.MAX_VALUE
                when (numeric.groupValues[2]) { ">" -> value > target; "<" -> value < target; ">=" -> value >= target; "<=" -> value <= target; else -> value == target }
            } else {
                val pattern = buildString {
                    clause.value.forEach { c -> append(when(c) { '*', '%' -> ".*"; '?', '_' -> "."; else -> Regex.escape(c.toString()) }) }
                }
                val regex = Regex(if (clause.exact) "^$pattern$" else pattern, RegexOption.IGNORE_CASE)
                (listOf(title) + tags).any { regex.containsMatchIn(it) }
            }
            if (clause.excluded) !hit else hit
        }
    }
}
