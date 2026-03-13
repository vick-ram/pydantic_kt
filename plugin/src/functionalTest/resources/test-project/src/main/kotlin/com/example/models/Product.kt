package com.example.models

import com.pydantic.runtime.delegates.Field
import com.pydantic.runtime.delegates.stringField
import com.pydantic.runtime.delegates.intField
import com.pydantic.runtime.delegates.doubleField

// Test delegation-based approach
class Product {
    var name: String by Field.string(
        minLength = 3,
        maxLength = 200,
        notBlank = true
    )

    var description: String? by Field.string(
        maxLength = 1000
    )

    var price: Double by Field.double(
        min = 0.01,
        max = 1000000.00
    )

    var quantity: Int by Field.int(
        min = 0,
        max = 10000
    )

    var sku: String by stringField {
        initial("")
        minLength(1)
        maxLength(50)
        pattern("^[A-Z0-9-]+\$")
    }
}

// Alternative with DSL
class Order {
    var id: String by stringField {
        initial("")
        minLength(1)
        pattern("^ORD-[A-Z0-9]+\$")
    }

    var amount: Double by doubleField {
        initial(0.0)
        min(0.01)
    }

    var status: String by stringField {
        initial("PENDING")
        pattern("^(PENDING|PROCESSING|COMPLETED|CANCELLED)\$")
    }
}