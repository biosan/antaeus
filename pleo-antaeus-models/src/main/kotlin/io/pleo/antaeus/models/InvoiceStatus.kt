package io.pleo.antaeus.models

enum class InvoiceStatus {
    PAID,
    PENDING,
    INVALID,
    INVALID_CUSTOMER,
    CURRENCY_MISMATCH,
    NETWORK_ERROR,
    UNKNOWN_ERROR,
}
