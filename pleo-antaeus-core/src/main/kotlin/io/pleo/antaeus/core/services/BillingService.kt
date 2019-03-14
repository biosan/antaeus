package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus


class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService
) {

    fun payAll(): Collection<Invoice> {
        val invoices = payInvoices( invoiceService.fetchAll() )
        invoices.map { invoiceService.setStatus(it.id, it.status) }
        return invoices
    }

    fun payInvoices(allInvoices:Collection<Invoice>): Collection<Invoice> {
        // Filter invoices and get only `PENDING` ones
        val invoices = allInvoices.filter { it.status == InvoiceStatus.PENDING }
        // Pay every invoice
        return invoices.map { payInvoice(it) }.toList()
    }

    fun payInvoice(invoice: Invoice): Invoice {
        val paid: Boolean
        var status: InvoiceStatus

        try {
            // Try to charge the invoice amount
            paid = paymentProvider.charge(invoice)
            status = when (paid) {
                true -> InvoiceStatus.PAID       // Correctly payed invoice
                false -> InvoiceStatus.PENDING   // Insufficient amount
            }
        }
        catch (exception: Exception) {
            status = when (exception) {
                is CustomerNotFoundException -> InvoiceStatus.INVALID_CUSTOMER
                is CurrencyMismatchException -> InvoiceStatus.CURRENCY_MISMATCH
                is NetworkException -> InvoiceStatus.NETWORK_ERROR
                else -> InvoiceStatus.UNKNOWN_ERROR
            }
        }
        return invoice.copy(status = status)
    }
}