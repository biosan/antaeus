/*
    Implements endpoints related to invoices.
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus

class InvoiceService(private val dal: AntaeusDal) {
    fun fetchAll(): List<Invoice> {
       return dal.fetchInvoices()
    }

    fun fetch(id: Int): Invoice {
        return dal.fetchInvoice(id) ?: throw InvoiceNotFoundException(id)
    }

    fun setStatus(id: Int, status: InvoiceStatus): Invoice {
        // Little workaround to throw an exception when invoice doesn't exists
        // Even if it make another DB call, for the moment is the best and
        //  simplest solution
        // TODO: Write a more elegant solution (and maybe faster)
        //       when you will know WTF `Table.update` returns
        val invoice = dal.fetchInvoice(id) ?: throw InvoiceNotFoundException(id)
        dal.setInvoiceStatus(id, status)
        return invoice.copy(status = status)
    }
}
