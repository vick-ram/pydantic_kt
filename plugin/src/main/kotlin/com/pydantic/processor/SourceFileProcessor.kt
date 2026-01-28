//package com.pydantic.processor
//
//import com.pydantic.runtime.validation.ModelInfo
//import com.pydantic.runtime.validation.PropertyInfo
//import java.io.File
//
//class SourceFileProcessor {
//
//    fun processFile(file: File): List<ModelInfo> {
//        val content = file.readText()
//        return extractModels(content, file)
//    }
//
//    private fun extractModels(content: String, file: File): List<ModelInfo> {
//        val models = mutableListOf<ModelInfo>()
//
//        // Extract package name
//        val packageName = extractPackageName(content)
//
//        // Find all classes with @Serializable annotation
//        val classRegex = """@Serializable[^)]*\)\s*(?:data\s+)?class\s+(\w+)""".toRegex()
//        val classMatches = classRegex.findAll(content)
//
//        classMatches.forEach { match ->
//            val className = match.groupValues[1]
//            val classContent = extractClassContent(content, className)
//
//            if (classContent.isNotEmpty()) {
//                val modelInfo = parseClass(className, packageName, classContent, file)
//                models.add(modelInfo)
//            }
//        }
//
//        return models
//    }
//
//    private fun extractPackageName(content: String): String {
//        val packageRegex = """package\s+([a-zA-Z0-9_.]+)""".toRegex()
//        return packageRegex.find(content)?.groupValues?.get(1) ?: ""
//    }
//
//    private fun extractClassContent(content: String, className: String): String {
//        val start = content.indexOf("class $className")
//        if (start == -1) return ""
//
//        var braceCount = 0
//        var end = start
//
//        for (i in start until content.length) {
//            when (content[i]) {
//                '{' -> braceCount++
//                '}' -> {
//                    braceCount--
//                    if (braceCount == 0) {
//                        end = i + 1
//                        break
//                    }
//                }
//            }
//        }
//
//        return content.substring(start, end)
//    }
//
//    private fun parseClass(
//        className: String,
//        packageName: String,
//        classContent: String,
//        sourceFile: File
//    ): ModelInfo {
//        // Extract @Serializable annotation parameters
//        val serializableMatch = """@Serializable\(([^)]*)\)""".toRegex().find(classContent)
//        val annotations = mutableMapOf<String, String>()
//        var isStrict = false
//
//        serializableMatch?.groupValues?.get(1)?.let { params ->
//            params.split(',').forEach { param ->
//                val keyValue = param.trim().split('=')
//                if (keyValue.size == 2) {
//                    val key = keyValue[0].trim()
//                    val value = keyValue[1].trim()
//                    annotations[key] = value
//
//                    if (key == "strict" && value == "true") {
//                        isStrict = true
//                    }
//                }
//            }
//        }
//
//        // Extract properties
//        val properties = extractProperties(classContent)
//
//        return ModelInfo(
//            name = className,
//            packageName = packageName,
//            properties = properties,
//            annotations = annotations,
//            isStrict = isStrict
//        )
//    }
//
//    private fun extractProperties(classContent: String): List<PropertyInfo> {
//        val properties = mutableListOf<PropertyInfo>()
//
//        // Extract constructor parameters (for data classes)
//        val constructorRegex = """(?:class|data class)\s+\w+\s*\(([^)]*)\)""".toRegex()
//        val constructorMatch = constructorRegex.find(classContent)
//
//        constructorMatch?.groupValues?.get(1)?.let { params ->
//            val paramList = splitParams(params)
//
//            paramList.forEach { param ->
//                val property = parseProperty(param.trim())
//                if (property != null) {
//                    properties.add(property)
//                }
//            }
//        }
//
//        // Also look for properties in class body
//        val bodyRegex = """\{([^}]*)\}""".toRegex()
//        val bodyMatch = bodyRegex.find(classContent)
//
//        bodyMatch?.groupValues?.get(1)?.let { body ->
//            val propertyRegex = """(?:val|var)\s+(\w+)\s*:\s*([^=\n;]+)(?:\s*=\s*[^;\n]+)?""".toRegex()
//            val propertyMatches = propertyRegex.findAll(body)
//
//            propertyMatches.forEach { match ->
//                val name = match.groupValues[1]
//                val type = match.groupValues[2].trim()
//                val annotations = extractPropertyAnnotations(body, name)
//
//                properties.add(
//                    PropertyInfo(
//                        name = name,
//                        type = type,
//                        annotations = annotations,
//                        isNullable = type.endsWith('?')
//                    )
//                )
//            }
//        }
//
//        return properties
//    }
//
//    private fun splitParams(params: String): List<String> {
//        val result = mutableListOf<String>()
//        var current = StringBuilder()
//        var parenDepth = 0
//        var angleDepth = 0
//
//        for (char in params) {
//            when (char) {
//                '(' -> parenDepth++
//                ')' -> parenDepth--
//                '<' -> angleDepth++
//                '>' -> angleDepth--
//                ',' -> {
//                    if (parenDepth == 0 && angleDepth == 0) {
//                        result.add(current.toString())
//                        current = StringBuilder()
//                        continue
//                    }
//                }
//            }
//            current.append(char)
//        }
//
//        if (current.isNotEmpty()) {
//            result.add(current.toString())
//        }
//
//        return result
//    }
//
//    private fun parseProperty(param: String): PropertyInfo? {
//        // Pattern: annotations? val/var name: type = defaultValue?
//        val pattern = """^(.*?)\s+(val|var)\s+(\w+)\s*:\s*([^=\n]+)(?:\s*=\s*(.*))?$""".toRegex()
//        val match = pattern.find(param) ?: return null
//
//        val annotationsText = match.groupValues[1].trim()
//        val name = match.groupValues[3]
//        val type = match.groupValues[4].trim()
//        val defaultValue = match.groupValues[5].takeIf { it.isNotEmpty() }
//
//        val annotations = parseAnnotations(annotationsText)
//
//        return PropertyInfo(
//            name = name,
//            type = type,
//            annotations = annotations,
//            isNullable = type.endsWith('?'),
//            // We could extract defaultValue if needed
//        )
//    }
//
//    private fun parseAnnotations(annotationsText: String): Map<String, Map<String, Any>> {
//        val annotations = mutableMapOf<String, Map<String, Any>>()
//
//        // Match annotations like @Field(min = 1, max = 100)
//        val annotationRegex = """@(\w+)(?:\(([^)]*)\))?""".toRegex()
//        val matches = annotationRegex.findAll(annotationsText)
//
//        matches.forEach { match ->
//            val annotationName = match.groupValues[1]
//            val paramsText = match.groupValues[2]
//
//            val params = mutableMapOf<String, Any>()
//            if (paramsText.isNotEmpty()) {
//                paramsText.split(',').forEach { param ->
//                    val keyValue = param.trim().split('=')
//                    if (keyValue.size == 2) {
//                        val key = keyValue[0].trim()
//                        val value = parseAnnotationValue(keyValue[1].trim())
//                        params[key] = value
//                    }
//                }
//            }
//
//            annotations[annotationName] = params
//        }
//
//        return annotations
//    }
//
//    private fun parseAnnotationValue(value: String): Any {
//        return when {
//            value == "true" -> true
//            value == "false" -> false
//            value.startsWith('"') && value.endsWith('"') ->
//                value.substring(1, value.length - 1)
//            value.matches(Regex("""^\d+$""")) -> value.toLong()
//            value.matches(Regex("""^\d+\.\d+$""")) -> value.toDouble()
//            else -> value
//        }
//    }
//
//    private fun extractPropertyAnnotations(body: String, propertyName: String): Map<String, Map<String, Any>> {
//        // Find the line with the property
//        val lines = body.lines()
//        for (line in lines) {
//            if (line.contains("val $propertyName") || line.contains("var $propertyName")) {
//                // Extract annotations from this line
//                val beforeProp = line.substringBefore("val $propertyName").substringBefore("var $propertyName")
//                return parseAnnotations(beforeProp)
//            }
//        }
//        return emptyMap()
//    }
//}

package com.pydantic.processor

import com.pydantic.runtime.validation.ModelInfo
import com.pydantic.runtime.validation.PropertyInfo
import java.io.File

class SourceFileProcessor {

    fun processFile(file: File, detectDelegates: Boolean = false): List<ModelInfo> {
        val content = file.readText()
        return if (detectDelegates) {
            extractModelsWithDelegates(content, file)
        } else {
            extractModelsWithAnnotations(content, file)
        }
    }

    // Original method for annotation-based models
    private fun extractModelsWithAnnotations(content: String, file: File): List<ModelInfo> {
        val models = mutableListOf<ModelInfo>()
        val packageName = extractPackageName(content)

        val classRegex = """@Serializable[^)]*\)\s*(?:data\s+)?class\s+(\w+)""".toRegex()
        val classMatches = classRegex.findAll(content)

        classMatches.forEach { match ->
            val className = match.groupValues[1]
            val classContent = extractClassContent(content, className)

            if (classContent.isNotEmpty()) {
                val modelInfo = parseClassWithAnnotations(className, packageName, classContent, file)
                models.add(modelInfo)
            }
        }

        return models
    }

    // NEW: Method for delegate-based models
    private fun extractModelsWithDelegates(content: String, file: File): List<ModelInfo> {
        val models = mutableListOf<ModelInfo>()
        val packageName = extractPackageName(content)

        // Look for classes with property delegates (any class, not just @Serializable)
        val classRegex = """(?:class|data\s+class|open\s+class|abstract\s+class)\s+(\w+)\s*[:{\{]""".toRegex()
        val classMatches = classRegex.findAll(content)

        classMatches.forEach { match ->
            val className = match.groupValues[1]
            val classContent = extractClassContent(content, className)

            if (classContent.isNotEmpty()) {
                // Check if class has delegate properties
                if (hasDelegateProperties(classContent)) {
                    val modelInfo = parseClassWithDelegates(className, packageName, classContent, file)
                    models.add(modelInfo)
                }
            }
        }

        return models
    }

    // Check if class contains delegate properties
    private fun hasDelegateProperties(classContent: String): Boolean {
        // Patterns for detecting delegates
        val delegatePatterns = listOf(
            """by\s+Field""",
            """by\s+Field\.""",
            """by\s+stringField""",
            """by\s+intField""",
            """by\s+doubleField""",
            """by\s+field\s*\{"""
        )

        return delegatePatterns.any { pattern ->
            pattern.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(classContent)
        }
    }

    private fun parseClassWithDelegates(
        className: String,
        packageName: String,
        classContent: String,
        sourceFile: File
    ): ModelInfo {
        val properties = extractDelegateProperties(classContent)

        return ModelInfo(
            name = className,
            packageName = packageName,
            properties = properties,
            annotations = mapOf("usesDelegates" to "true"), // Mark as delegate-based
            isStrict = false // Delegates validate on assignment, not strict mode needed
        )
    }

    private fun extractDelegateProperties(classContent: String): List<PropertyInfo> {
        val properties = mutableListOf<PropertyInfo>()

        // Pattern 1: var name: Type by Field.string(minLength = 2, maxLength = 100)
        val simpleDelegatePattern = """(?:val|var)\s+(\w+)\s*:\s*([^=\n]+?)\s+by\s+(Field\.\w+|[Ff]ield)\(([^)]*)\)""".toRegex()

        simpleDelegatePattern.findAll(classContent).forEach { match ->
            val name = match.groupValues[1]
            val type = match.groupValues[2].trim()
            val delegateCall = match.groupValues[3] // Field.string or Field
            val paramsText = match.groupValues[4]

            val annotations = parseDelegateParams(paramsText, delegateCall)

            properties.add(
                PropertyInfo(
                    name = name,
                    type = type,
                    annotations = annotations,
                    isNullable = type.endsWith('?')
                )
            )
        }

        // Pattern 2: var name: Type by stringField { minLength(2) maxLength(100) }
        val dslDelegatePattern = """(?:val|var)\s+(\w+)\s*:\s*([^=\n]+?)\s+by\s+(\w+Field)\s*\{([^}]*)\}""".toRegex()

        dslDelegatePattern.findAll(classContent).forEach { match ->
            val name = match.groupValues[1]
            val type = match.groupValues[2].trim()
            val delegateType = match.groupValues[3] // stringField, intField, etc.
            val dslContent = match.groupValues[4]

            val annotations = parseDslDelegateParams(dslContent, delegateType)

            properties.add(
                PropertyInfo(
                    name = name,
                    type = type,
                    annotations = annotations,
                    isNullable = type.endsWith('?')
                )
            )
        }

        // Pattern 3: var name: Type by field { ... } (generic field)
        val genericFieldPattern = """(?:val|var)\s+(\w+)\s*:\s*([^=\n]+?)\s+by\s+field\s*\{([^}]*)\}""".toRegex()

        genericFieldPattern.findAll(classContent).forEach { match ->
            val name = match.groupValues[1]
            val type = match.groupValues[2].trim()
            val dslContent = match.groupValues[3]

            val annotations = parseGenericFieldParams(dslContent, type)

            properties.add(
                PropertyInfo(
                    name = name,
                    type = type,
                    annotations = annotations,
                    isNullable = type.endsWith('?')
                )
            )
        }

        return properties
    }

    private fun parseDelegateParams(paramsText: String, delegateCall: String): Map<String, Map<String, Any>> {
        val annotations = mutableMapOf<String, Map<String, Any>>()

        // Parse parameters like: minLength = 2, maxLength = 100, email = true
        val params = mutableMapOf<String, Any>()

        // Split by commas, but handle nested parentheses
        val paramRegex = """(\w+)\s*=\s*([^,]+(?:\s*\([^)]*\))?)""".toRegex()
        paramRegex.findAll(paramsText).forEach { match ->
            val key = match.groupValues[1].trim()
            val valueStr = match.groupValues[2].trim()
            val value = parseDelegateValue(valueStr)
            params[key] = value
        }

        // Map delegate calls to annotation types
        when {
            delegateCall.startsWith("Field.") -> {
                val fieldType = delegateCall.substringAfter("Field.").substringBefore("(")
                when (fieldType) {
                    "string" -> {
                        // Convert Field.string params to @Field annotation
                        val fieldParams = mutableMapOf<String, Any>()
                        params["minLength"]?.let { fieldParams["minLength"] = it }
                        params["maxLength"]?.let { fieldParams["maxLength"] = it }
                        params["pattern"]?.let { fieldParams["pattern"] = it }
                        if (params["email"] == true) {
                            annotations["Email"] = emptyMap()
                        }
                        if (params["url"] == true) {
                            annotations["Url"] = emptyMap()
                        }
                        if (fieldParams.isNotEmpty()) {
                            annotations["Field"] = fieldParams
                        }
                    }
                    "int", "long" -> {
                        val fieldParams = mutableMapOf<String, Any>()
                        params["min"]?.let { fieldParams["min"] = it }
                        params["max"]?.let { fieldParams["max"] = it }
                        if (fieldParams.isNotEmpty()) {
                            annotations["Field"] = fieldParams
                        }
                    }
                    "double", "float" -> {
                        val fieldParams = mutableMapOf<String, Any>()
                        params["min"]?.let { fieldParams["min"] = it }
                        params["max"]?.let { fieldParams["max"] = it }
                        if (fieldParams.isNotEmpty()) {
                            annotations["Field"] = fieldParams
                        }
                    }
                }
            }
            delegateCall.equals("Field", ignoreCase = true) -> {
                // Generic Field delegate
                val fieldParams = mutableMapOf<String, Any>()
                params.forEach { (key, value) ->
                    fieldParams[key] = value
                }
                if (fieldParams.isNotEmpty()) {
                    annotations["Field"] = fieldParams
                }
            }
        }

        return annotations
    }

    private fun parseDslDelegateParams(dslContent: String, delegateType: String): Map<String, Map<String, Any>> {
        val annotations = mutableMapOf<String, Map<String, Any>>()
        val fieldParams = mutableMapOf<String, Any>()

        // Parse DSL content like: minLength(2) maxLength(100)
        val functionCallRegex = """(\w+)\(([^)]*)\)""".toRegex()

        functionCallRegex.findAll(dslContent).forEach { match ->
            val functionName = match.groupValues[1]
            val argText = match.groupValues[2]

            when (delegateType) {
                "stringField" -> {
                    when (functionName) {
                        "minLength", "maxLength", "pattern" -> {
                            val value = parseDelegateValue(argText)
                            fieldParams[functionName] = value
                        }
                        "email", "url", "notBlank", "notEmpty" -> {
                            if (argText.toBooleanStrictOrNull() == true) {
                                annotations[functionName.capitalize()] = emptyMap()
                            }
                        }
                    }
                }
                "intField", "longField" -> {
                    when (functionName) {
                        "min", "max", "range" -> {
                            val value = parseDelegateValue(argText)
                            fieldParams[functionName] = value
                        }
                    }
                }
                "doubleField", "floatField" -> {
                    when (functionName) {
                        "min", "max" -> {
                            val value = parseDelegateValue(argText)
                            fieldParams[functionName] = value
                        }
                    }
                }
            }
        }

        if (fieldParams.isNotEmpty()) {
            annotations["Field"] = fieldParams
        }

        return annotations
    }

    private fun parseGenericFieldParams(dslContent: String, propertyType: String): Map<String, Map<String, Any>> {
        val annotations = mutableMapOf<String, Map<String, Any>>()
        val fieldParams = mutableMapOf<String, Any>()

        // Look for validator calls
        val validatorRegex = """validator\s*\{[^}]*\}""".toRegex()
        if (validatorRegex.containsMatchIn(dslContent)) {
            annotations["Validate"] = mapOf("custom" to "true")
        }

        // Look for initial value
        val initialRegex = """initial\(([^)]*)\)""".toRegex()
        initialRegex.find(dslContent)?.let { match ->
            val defaultValue = parseDelegateValue(match.groupValues[1])
            fieldParams["defaultValue"] = defaultValue
        }

        if (fieldParams.isNotEmpty()) {
            annotations["Field"] = fieldParams
        }

        return annotations
    }

    private fun parseDelegateValue(valueStr: String): Any {
        return when {
            valueStr == "true" -> true
            valueStr == "false" -> false
            valueStr.startsWith('"') && valueStr.endsWith('"') ->
                valueStr.substring(1, valueStr.length - 1)
            valueStr.matches(Regex("""^\d+$""")) -> valueStr.toLong()
            valueStr.matches(Regex("""^\d+\.\d+$""")) -> valueStr.toDouble()
            valueStr.startsWith("rangeOf(") -> {
                // Handle rangeOf(1, 100)
                val rangeValues = valueStr.removePrefix("rangeOf(").removeSuffix(")")
                    .split(',').map { it.trim().toLong() }
                mapOf("min" to rangeValues[0], "max" to rangeValues[1])
            }
            else -> valueStr
        }
    }

    // Keep all the original annotation parsing methods...
    private fun extractPackageName(content: String): String {
        val packageRegex = """package\s+([a-zA-Z0-9_.]+)""".toRegex()
        return packageRegex.find(content)?.groupValues?.get(1) ?: ""
    }

    private fun extractClassContent(content: String, className: String): String {
        val start = content.indexOf("class $className")
        if (start == -1) return ""

        var braceCount = 0
        var end = start

        for (i in start until content.length) {
            when (content[i]) {
                '{' -> braceCount++
                '}' -> {
                    braceCount--
                    if (braceCount == 0) {
                        end = i + 1
                        break
                    }
                }
            }
        }

        return content.substring(start, end)
    }

    private fun parseClassWithAnnotations(
        className: String,
        packageName: String,
        classContent: String,
        sourceFile: File
    ): ModelInfo {
        val serializableMatch = """@Serializable\(([^)]*)\)""".toRegex().find(classContent)
        val annotations = mutableMapOf<String, String>()
        var isStrict = false

        serializableMatch?.groupValues?.get(1)?.let { params ->
            params.split(',').forEach { param ->
                val keyValue = param.trim().split('=')
                if (keyValue.size == 2) {
                    val key = keyValue[0].trim()
                    val value = keyValue[1].trim()
                    annotations[key] = value

                    if (key == "strict" && value == "true") {
                        isStrict = true
                    }
                }
            }
        }

        val properties = extractPropertiesFromAnnotations(classContent)

        return ModelInfo(
            name = className,
            packageName = packageName,
            properties = properties,
            annotations = annotations,
            isStrict = isStrict
        )
    }

    private fun extractPropertiesFromAnnotations(classContent: String): List<PropertyInfo> {
        // Original annotation-based property extraction logic
        val properties = mutableListOf<PropertyInfo>()

        val constructorRegex = """(?:class|data class)\s+\w+\s*\(([^)]*)\)""".toRegex()
        val constructorMatch = constructorRegex.find(classContent)

        constructorMatch?.groupValues?.get(1)?.let { params ->
            val paramList = splitParams(params)

            paramList.forEach { param ->
                val property = parseProperty(param.trim())
                if (property != null) {
                    properties.add(property)
                }
            }
        }

        return properties
    }

    // Keep all other original methods...
    private fun splitParams(params: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var parenDepth = 0
        var angleDepth = 0

        for (char in params) {
            when (char) {
                '(' -> parenDepth++
                ')' -> parenDepth--
                '<' -> angleDepth++
                '>' -> angleDepth--
                ',' -> {
                    if (parenDepth == 0 && angleDepth == 0) {
                        result.add(current.toString())
                        current = StringBuilder()
                        continue
                    }
                }
            }
            current.append(char)
        }

        if (current.isNotEmpty()) {
            result.add(current.toString())
        }

        return result
    }

    private fun parseProperty(param: String): PropertyInfo? {
        // Pattern: annotations? val/var name: type = defaultValue?
        val pattern = """^(.*?)\s+(val|var)\s+(\w+)\s*:\s*([^=\n]+)(?:\s*=\s*(.*))?$""".toRegex()
        val match = pattern.find(param) ?: return null

        val annotationsText = match.groupValues[1].trim()
        val name = match.groupValues[3]
        val type = match.groupValues[4].trim()
        val defaultValue = match.groupValues[5].takeIf { it.isNotEmpty() }

        val annotations = parseAnnotations(annotationsText)

        return PropertyInfo(
            name = name,
            type = type,
            annotations = annotations,
            isNullable = type.endsWith('?'),
            // We could extract defaultValue if needed
        )
    }

    private fun parseAnnotations(annotationsText: String): Map<String, Map<String, Any>> {
        val annotations = mutableMapOf<String, Map<String, Any>>()

        // Match annotations like @Field(min = 1, max = 100)
        val annotationRegex = """@(\w+)(?:\(([^)]*)\))?""".toRegex()
        val matches = annotationRegex.findAll(annotationsText)

        matches.forEach { match ->
            val annotationName = match.groupValues[1]
            val paramsText = match.groupValues[2]

            val params = mutableMapOf<String, Any>()
            if (paramsText.isNotEmpty()) {
                paramsText.split(',').forEach { param ->
                    val keyValue = param.trim().split('=')
                    if (keyValue.size == 2) {
                        val key = keyValue[0].trim()
                        val value = parseAnnotationValue(keyValue[1].trim())
                        params[key] = value
                    }
                }
            }

            annotations[annotationName] = params
        }

        return annotations
    }

    private fun parseAnnotationValue(value: String): Any {
        return when {
            value == "true" -> true
            value == "false" -> false
            value.startsWith('"') && value.endsWith('"') ->
                value.substring(1, value.length - 1)
            value.matches(Regex("""^\d+$""")) -> value.toLong()
            value.matches(Regex("""^\d+\.\d+$""")) -> value.toDouble()
            else -> value
        }
    }

    private fun extractPropertyAnnotations(body: String, propertyName: String): Map<String, Map<String, Any>> {
        // Find the line with the property
        val lines = body.lines()
        for (line in lines) {
            if (line.contains("val $propertyName") || line.contains("var $propertyName")) {
                // Extract annotations from this line
                val beforeProp = line.substringBefore("val $propertyName").substringBefore("var $propertyName")
                return parseAnnotations(beforeProp)
            }
        }
        return emptyMap()
    }
}
