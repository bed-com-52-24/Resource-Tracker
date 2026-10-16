package com.resourcetracker.request.service

import com.resourcetracker.request.dto.RequestLineItemDto
import com.resourcetracker.request.entity.RequestCategory
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class RequestValidationService {

    fun validateLineItems(items: List<RequestLineItemDto>) {
        require(items.isNotEmpty()) {
            "At least one line item is required"
        }

        items.forEachIndexed { index, item ->
            require(item.description.length in 3..255) {
                "Line item ${index + 1}: description must be 3-255 characters"
            }

            require(item.manufacturer.length in 2..120) {
                "Line item ${index + 1}: manufacturer must be 2-120 characters"
            }

            require(item.unitPrice > BigDecimal.ZERO) {
                "Line item ${index + 1}: unit price must be greater than zero"
            }

            require(item.unitPrice.scale() <= 2) {
                "Line item ${index + 1}: unit price cannot have more than 2 decimal places"
            }

            require(item.quantity in 1..100000) {
                "Line item ${index + 1}: quantity must be between 1 and 100000"
            }

            require(
                if (item.category == RequestCategory.CONSUMABLE) {
                    !item.batchNumber.isNullOrBlank()
                } else {
                    item.batchNumber == null
                }
            ) {
                "Line item ${index + 1}: batch number is required only for CONSUMABLE"
            }

            require((item.batchNumber?.length ?: 0) <= 50) {
                "Line item ${index + 1}: batch number cannot exceed 50 characters"
            }
        }
    }

    fun calculateTotal(
        items: List<RequestLineItemDto>
    ): BigDecimal =
        items.fold(BigDecimal.ZERO) { total, item ->
            total.add(
                item.unitPrice.multiply(
                    BigDecimal.valueOf(item.quantity.toLong())
                )
            )
        }
}