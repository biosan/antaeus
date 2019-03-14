package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InvoiceServiceTest {
    private val dal = mockk<AntaeusDal> {
        every { fetchInvoice(404) } returns null
        every { fetchInvoice(1) } returns Invoice(1, 1, Money(1.toBigDecimal(),
                Currency.EUR), InvoiceStatus.PENDING)
        every { setInvoiceStatus(1, InvoiceStatus.PAID) } returns 1
    }

    private val invoiceService = InvoiceService(dal = dal)

    @Test
    fun `will throw if customer is not found`() {
        assertThrows<InvoiceNotFoundException> {
            invoiceService.fetch(404)
        }
    }

    @Test
    fun `will set invoice status`() {
        assert(invoiceService.setStatus(1, InvoiceStatus.PAID) == Invoice(1, 1, Money(1.toBigDecimal(),
                Currency.EUR), InvoiceStatus.PAID))
    }
}