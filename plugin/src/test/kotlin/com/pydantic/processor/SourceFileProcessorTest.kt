package com.pydantic.processor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

class SourceFileProcessorTest : StringSpec({

    val processor = SourceFileProcessor()
    val tempDir = createTempDir("pydantic-test")

    afterSpec {
        tempDir.deleteRecursively()
    }

    "should parse simple class with annotations" {
        val source = """
            package com.example
            
            import com.pydantic.annotation.Serializable
            import com.pydantic.annotation.Field
            
            @Serializable
            data class User(
                @Field(minLength = 2, maxLength = 100)
                val name: String,
                
                @Field(min = 0, max = 150)
                val age: Int
            )
        """.trimIndent()

        val file = File(tempDir, "User.kt").apply { writeText(source) }
        val models = processor.processFile(file)

        models shouldHaveSize 1
        val model = models.first()
        model.name shouldBe "User"
        model.packageName shouldBe "com.example"
        model.properties shouldHaveSize 2

        val nameProp = model.properties.find { it.name == "name" }
        nameProp shouldNotBe null
        nameProp?.type shouldBe "String"
        nameProp?.isNullable shouldBe false
        nameProp?.annotations?.get("Field")?.get("minLength") shouldBe 2

        val ageProp = model.properties.find { it.name == "age" }
        ageProp?.annotations?.get("Field")?.get("min") shouldBe 0
    }

    "should parse class with delegates" {
        val source = """
            package com.example
            
            import com.pydantic.runtime.delegates.Field
            
            class Product {
                var name: String by Field.string(minLength = 3, maxLength = 200)
                var price: Double by Field.double(min = 0.01)
                var quantity: Int by Field.int(min = 0)
            }
        """.trimIndent()

        val file = File(tempDir, "Product.kt").apply { writeText(source) }
        val models = processor.processFile(file)

        models shouldHaveSize 1
        val model = models.first()
        model.name shouldBe "Product"
        model.properties shouldHaveSize 3

        // Note: SourceFileProcessor needs to be updated to detect delegates
        // For now, it might not find these properties
    }
})