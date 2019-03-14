package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.*
import org.junit.jupiter.api.Test

class BillingServiceTest {

    // Test data
    val thousandEuros = Money(1000.toBigDecimal(), Currency.EUR)
    val customerA = Customer(1, Currency.EUR)
    val customerB = Customer(2, Currency.USD)
    val customerC = Customer(3, Currency.EUR)
    val invoiceA = Invoice(1, customerA.id, thousandEuros, InvoiceStatus.PENDING)
    val invoiceB = Invoice(2, customerC.id, thousandEuros, InvoiceStatus.PENDING)
    val invoiceC = Invoice(3, customerA.id, thousandEuros, InvoiceStatus.PENDING)
    val invoiceD = Invoice(4, customerB.id, thousandEuros, InvoiceStatus.PENDING)
    val invoiceE = Invoice(5, customerA.id, thousandEuros, InvoiceStatus.PENDING)

    // Mock PaymentProvider
    private val paymentProvider = mockk<PaymentProvider> {
        every { charge(invoiceA) } returns true
        every { charge(invoiceB) } returns false
        every { charge(invoiceC) } throws CustomerNotFoundException(3)
        every { charge(invoiceD) } throws CurrencyMismatchException(4, 2)
        every { charge(invoiceE) } throws  NetworkException()
    }
    val invoices = listOf(invoiceA, invoiceB, invoiceC, invoiceD, invoiceE)
    // Mock AntaeusDal
    private val dal = mockk<AntaeusDal> {
        every { fetchCustomer(customerA.id) } returns customerA
        every { fetchCustomer(customerB.id) } returns customerB
        every { fetchCustomer(customerC.id) } returns customerC
        for (invoice in invoices) {
            every { fetchInvoice(invoice.id) } returns invoice
        }
        every { fetchCustomers() } returns listOf(customerA, customerB, customerC)
        every { fetchInvoices() } returns invoices
    }
    // Instantiate required services
    private val customerService = CustomerService(dal = dal)
    private val invoiceService = InvoiceService(dal = dal)
    private val billingService = BillingService(paymentProvider, invoiceService)


    @Test
    fun `will pay a good invoice and reject others`() {
        assert(billingService.payInvoice(invoiceA).status == InvoiceStatus.PAID)
        assert(billingService.payInvoice(invoiceB).status == InvoiceStatus.PENDING)
        assert(billingService.payInvoice(invoiceC).status == InvoiceStatus.INVALID_CUSTOMER)
        assert(billingService.payInvoice(invoiceD).status == InvoiceStatus.CURRENCY_MISMATCH)
        assert(billingService.payInvoice(invoiceE).status == InvoiceStatus.NETWORK_ERROR)
    }


    @Test
    fun `pay all invoices and must return true`() {
        val output = billingService.payInvoices(listOf(invoiceA, invoiceB, invoiceC, invoiceD, invoiceE)).toList()
        assert( output[0].status == InvoiceStatus.PAID)
        assert( output[1].status == InvoiceStatus.PENDING)
        assert( output[2].status == InvoiceStatus.INVALID_CUSTOMER)
        assert( output[3].status == InvoiceStatus.CURRENCY_MISMATCH)
        assert( output[4].status == InvoiceStatus.NETWORK_ERROR)
    }

}