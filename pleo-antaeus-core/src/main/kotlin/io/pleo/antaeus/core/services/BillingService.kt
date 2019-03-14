package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.*
import java.sql.Date
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.concurrent.fixedRateTimer


const val DAY_OF_THE_MONTH = 1
const val TIMEOUT = 3000L
const val RETRIES = 5


class BillingService(
        private val paymentProvider: PaymentProvider,
        private val invoiceService: InvoiceService
) {

    fun start(): Timer {
        return fixedRateTimer("billing_scheduler",
                daemon = false, // Do not run as deamon
                // Start at tomorrow at midnight (local time)
                startAt = Date.valueOf(LocalDate.now().plusDays(1)),
                period = 216_000_000L   // Period is every day
        ) {
            // Run only if it's the right day of the month (first)
            if (LocalDateTime.now().dayOfMonth == DAY_OF_THE_MONTH) {
                payAll()
            }
        }
    }

    fun payAll(): Collection<Invoice> {
        val invoices = payInvoices( invoiceService.fetchAll() )
        invoices.map { invoiceService.setStatus(it.id, it.status) }
        return invoices
    }

    fun payInvoices(allInvoices: Collection<Invoice>): Collection<Invoice> {
        // Filter invoices and get only `PENDING` ones
        val invoices = allInvoices.filter { it.status == InvoiceStatus.PENDING }
        return runBlocking {
            // Pay every invoice with a coroutine
            invoices.map { async { payInvoiceRetry(it) } }
                    .map { it.await() }
                    .toList()
        }
    }


    suspend fun payInvoiceRetry(invoice: Invoice,
                                timeout: Long = TIMEOUT,
                                retries: Int = RETRIES): Invoice {
        var out: Invoice = invoice.copy(status = InvoiceStatus.UNKNOWN_ERROR)
        for (i in 1..retries) {
            try {
                out = withTimeout(timeout) {
                    async { payInvoice(invoice) }.await()   // TODO: Workaround
                }
                break
            } catch (e: TimeoutCancellationException) { continue }
        }
        return out
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
        } catch (exception: Exception) {
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