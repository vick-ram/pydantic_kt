package com.pydantic.processor

import com.pydantic.plugin.utils.generatePropValidation
import com.pydantic.runtime.validation.ModelInfo
import com.pydantic.runtime.validation.PropertyInfo
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName

class DelegationGenerator {

    fun generateDelegates(model: ModelInfo): FileSpec {

        val className = ClassName(model.packageName, model.name)

        val fileBuilder = FileSpec.builder(
            model.packageName,
            "${model.name}Delegates"
        ).addImport("com.pydantic.runtime.delegates", "*")

        val validateDelegatesFun = FunSpec.builder("validateDelegates")
            .receiver(className)
            .returns(Boolean::class)
            .addCode(generateValidationChecks(model))
            .addStatement("return true")
            .build()

        fileBuilder.addFunction(validateDelegatesFun)

        val validateFun = generateValidateFunction(model)
        fileBuilder.addFunction(validateFun)

        generatePropertyExtensions(model).forEach {
            fileBuilder.addProperty(it)
        }

        return fileBuilder.build()
    }

    private fun generateValidationChecks(model: ModelInfo): CodeBlock {

        val code = CodeBlock.builder()

        model.properties.forEach { prop ->
            if (!prop.isNullable) {
                code.addStatement("// validation check for %L", prop.name)
            }
        }

        return code.build()
    }

    private fun generateValidateFunction(model: ModelInfo): FunSpec {

        val validationError = ClassName("com.pydantic.runtime.delegates", "ValidationError")

        val funBuilder = FunSpec.builder("validate")
            .receiver(ClassName(model.packageName, model.name))
            .returns(List::class.asClassName().parameterizedBy(validationError))
            .addStatement("val errors = mutableListOf<%T>()", validationError)

        model.properties.forEach { prop ->
            if (!prop.isNullable) {
                funBuilder.addCode(
                    """
                    if (this.%L == null) {
                        errors.add(
                            %T(
                                field = %S,
                                message = %S,
                                code = "REQUIRED"
                            )
                        )
                    }
                    
                    """.trimIndent(),
                    prop.name,
                    validationError,
                    prop.name,
                    "${prop.name} is required"
                )
            }
        }

        funBuilder.addStatement("return errors")

        return funBuilder.build()
    }

    private fun generatePropertyExtensions(model: ModelInfo): List<PropertySpec> {

        val receiver = ClassName(model.packageName, model.name)

        return model.properties.map { prop ->
            val paramType = ClassName.bestGuess(prop.type)
                .copy(nullable = true)

            val lambdaType = LambdaTypeName.get(
                parameters = arrayOf(paramType),
                returnType = Boolean::class.asTypeName()
            )

            PropertySpec.builder("${prop.name}Validator", lambdaType)
                .receiver(receiver)
                .getter(
                    FunSpec.getterBuilder()
                        .addCode(
                            """
                            return { value ->
                                %L
                                true
                            }
                            """.trimIndent(),
                            generateSinglePropertyValidation(prop, model)
                        )
                        .build()
                )
                .build()
        }
    }

    private fun generateSinglePropertyValidation(
        prop: PropertyInfo,
        model: ModelInfo
    ): CodeBlock {
        return CodeBlock.of(generatePropValidation(prop, model))
    }
}