package com.pydantic.processor

import com.pydantic.plugin.utils.generatePropValidation
import com.pydantic.runtime.validation.ModelInfo
import com.pydantic.runtime.validation.PropertyInfo

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

class ValidatorGenerator {

    fun generateValidator(model: ModelInfo, strict: Boolean): FileSpec {
        val runtimePkg = "com.pydantic.runtime.validation"
        val modelClass = ClassName(model.packageName, model.name)
        val validationResult = ClassName(runtimePkg, "ValidationResult")
        val baseValidator = ClassName(runtimePkg, "BaseValidator").parameterizedBy(modelClass)

        val validatorClassName = ClassName(model.packageName, "${model.name}Validator")
        val validatorClass = TypeSpec.classBuilder(validatorClassName)
            .superclass(baseValidator)
            .addInitializerBlock(generateValidations(model))
            .addFunction(generateSchemaFunction(model))
            .build()

        val validateFun = FunSpec.builder("validate")
            .receiver(modelClass)
            .returns(validationResult)
            .addStatement("return %T().validate(this)", validatorClassName)
            .build()

        val validatePartialFun = FunSpec.builder("validatePartial")
            .receiver(modelClass)
            .returns(ClassName("com.pydantic.runtime.validation", "ValidationResult"))
            .addStatement("return %T().validatePartial(this)", validatorClassName)
            .build()

        return FileSpec.builder(model.packageName, "${model.name}Validator")
//            .addImport("com.pydantic.runtime.validation", "*")
//            .addImport("com.pydantic.runtime.validation.constraints", "*")
//            .addImport("java.time", "*")
            .addType(validatorClass)
            .addFunction(validateFun)
            .addFunction(validatePartialFun)
            .build()
    }

    private fun generateValidations(model: ModelInfo): CodeBlock {

        val code = CodeBlock.builder()

        model.properties.forEach { prop ->
            code.add(generatePropertyValidation(prop, model))
            code.add("\n")
        }

        return code.build()
    }

    private fun generatePropertyValidation(
        prop: PropertyInfo,
        model: ModelInfo
    ): CodeBlock {
        return CodeBlock.of(generatePropValidation(prop, model))
    }

    private fun generateSchemaFunction(model: ModelInfo): FunSpec {

        val mapType = Map::class.asClassName()
            .parameterizedBy(
                String::class.asTypeName(),
                ANY
            )

        return FunSpec.builder("getSchema")
            .addModifiers(KModifier.OVERRIDE)
            .returns(mapType)
            .addCode(buildSchema(model))
            .build()
    }

    private fun buildSchema(model: ModelInfo): CodeBlock {

        val code = CodeBlock.builder()

        code.add("return mapOf(\n")

        model.properties.forEachIndexed { index, prop ->

            code.add(
                "%S to mapOf(\n",
                prop.name
            )

            code.add(
                "%S to %S,\n",
                "type",
                getTypeName(prop.type)
            )

            code.add(
                "%S to %L,\n",
                "required",
                !prop.isNullable
            )

            code.add(
                "%S to %L,\n",
                "nullable",
                prop.isNullable
            )

            code.add(
                "%S to mapOf<String, Any>(\n",
                "constraints"
            )

            code.add(generatePropertySchema(prop))

            code.add(")\n")
            code.add(")")

            if (index < model.properties.lastIndex) {
                code.add(",")
            }

            code.add("\n")
        }

        code.add(")\n")

        return code.build()
    }

    private fun generatePropertySchema(prop: PropertyInfo): CodeBlock {

        val code = CodeBlock.builder()

        val entries = mutableListOf<Pair<String, Any?>>()

        prop.annotations.forEach { (_, params) ->
            params.forEach { (key, value) ->
                entries.add(key to value)
            }
        }

        entries.forEachIndexed { index, (key, value) ->

            when (value) {

                is String -> code.add("%S to %S", key, value)

                is List<*> -> {
                    val formatted = value.joinToString(", ") {
                        if (it is String) "\"$it\"" else it.toString()
                    }
                    code.add("%S to listOf($formatted)", key)
                }

                else -> code.add("%S to %L", key, value)
            }

            if (index < entries.lastIndex) {
                code.add(",\n")
            }
        }

        return code.build()
    }

    private fun getTypeName(type: String): String {

        val t = type.replace("?", "").trim()

        return when {

            t == "String" -> "string"

            t in listOf(
                "Int", "Long", "Short", "Byte", "BigInteger"
            ) -> "integer"

            t in listOf(
                "Float", "Double", "BigDecimal"
            ) -> "number"

            t == "Boolean" -> "boolean"

            t in listOf(
                "LocalDate",
                "LocalDateTime",
                "LocalTime",
                "Instant",
                "ZonedDateTime"
            ) -> "datetime"

            t.startsWith("List")
                    || t.startsWith("Set")
                    || t.contains("Array") -> "array"

            t.startsWith("Map") -> "map"

            else -> "object"
        }
    }
}
