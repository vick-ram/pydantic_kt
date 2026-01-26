package com.pydantic.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.pydantic.annotation.Serializable
import com.pydantic.runtime.validation.ModelInfo
import com.pydantic.runtime.validation.PropertyInfo

class KspModelProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(Serializable::class.qualifiedName!!)
        val ret = symbols.filter { !it.validate() }.toList()

        symbols
            .filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(ModelVisitor(), Unit) }

        return ret
    }

    inner class ModelVisitor : KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            try {
                val modelInfo = extractModelInfo(classDeclaration)
                generateValidator(modelInfo)
            } catch (e: Exception) {
                logger.error("Failed to process ${classDeclaration.simpleName.asString()}: ${e.message}", e as KSNode?)
            }
        }

        private fun extractModelInfo(classDecl: KSClassDeclaration): ModelInfo {
            val serializableAnn = classDecl.annotations
                .firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                        Serializable::class.qualifiedName }

            if (serializableAnn == null) {
                logger.error(
                    "Class ${classDecl.simpleName.asString()} must be annotated with @Serializable",
                    classDecl
                )
                return ModelInfo(
                    name = classDecl.simpleName.asString(),
                    packageName = classDecl.containingFile?.packageName?.asString() ?: "",
                    properties = emptyList(),
                    annotations = emptyMap(),
                    isStrict = false
                )
            }

            val properties = classDecl.getAllProperties().map { prop ->
                PropertyInfo(
                    name = prop.simpleName.asString(),
                    type = prop.type.resolve().toString(),
                    annotations = prop.annotations.associate { ann ->
                        ann.shortName.asString() to ann.arguments
                            .mapNotNull { arg ->
                                val key = arg.name?.asString() ?: return@mapNotNull null
                                arg.value?.let { key to it }
                            }
                            .toMap()
                    },
                    isNullable = prop.type.resolve().isMarkedNullable
                )
            }

            val annotationArgs = mutableMapOf<String, String>()
            var isStrict = false

            serializableAnn.arguments.forEach { arg ->
                val name = arg.name?.asString() ?: ""
                val value = arg.value.toString()
                annotationArgs[name] = value

                if (name == "strict" && value == "true") {
                    isStrict = true
                }
            }

            return ModelInfo(
                name = classDecl.simpleName.asString(),
                packageName = classDecl.containingFile?.packageName?.asString() ?: "",
                properties = properties.toList(),
                annotations = annotationArgs,
                isStrict = isStrict
            )
        }

        private fun generateValidator(modelInfo: ModelInfo) {
            val generator = ValidatorGenerator()
            val code = generator.generateValidator(modelInfo, modelInfo.isStrict)

            val file = codeGenerator.createNewFile(
                Dependencies.ALL_FILES,
                modelInfo.packageName,
                "${modelInfo.name}Validator",
                ".kt"
            )

            file.write(code.toByteArray())
            file.close()

            logger.info("Generated validator for ${modelInfo.packageName}.${modelInfo.name}")
        }
    }

    class Provider : SymbolProcessorProvider {
        override fun create(
            environment: SymbolProcessorEnvironment
        ): SymbolProcessor {
            return KspModelProcessor(
                codeGenerator = environment.codeGenerator,
                logger = environment.logger
            )
        }
    }
}