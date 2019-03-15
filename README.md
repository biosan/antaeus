## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will pay those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

### Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── pleo-antaeus-app
|
|       Packages containing the main() application. 
|       This is where all the dependencies are instantiated.
|
├── pleo-antaeus-core
|
|       This is where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|
|       Module interfacing with the database. Contains the models, mappings and access layer.
|
├── pleo-antaeus-models
|
|       Definition of models used throughout the application.
|
├── pleo-antaeus-rest
|
|        Entry point for REST API. This is where the routes are defined.
└──
```

## Instructions
Fork this repo with your solution. We want to see your progression through commits (don’t commit the entire solution in 1 step) and don't forget to create a README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

Happy hacking 😁!

## How to run
```
./docker-start.sh
```

## Lifting up Antaeus

### Learn Kotlin

I used Java a couple of times in the past, this is the first time with Kotlin. I spent the first day of the challenge to learn basics and some a bit more advanced concept that I think will be useful for beating Antaues, like coroutines.

I love how concise and *"keystroke-efficent"* is compared to Java.


### Requirements and constraints

1. `BillingService` must be executed in background without interferring with HTTP APIs or other services
1. Invoices are charged through an external HTTP API calling `PaymentProvider.charge` and it's probable that the real API endpoint will vary depending on currency and clients.
1. Invoices are added to the database throught an external service, there is no need to implement it in Antaeus for now.
1. Since there must be a way to add invoices to the DB, the same system must be in charge of modify them if needed, and again Antaeus don't need to implement it now.
1. Due to the fact that invoices are charged using an HTTP API, it's very likely that some errors will happen (network, timeouts, etc.), and that means that Antaeus will have to retry to charge the invoice.

>Note: This are personal assumptions


### Architecture

I like simple and reliable systems, too much complexity is a bad thing and I'll design Antaeus following this principles and UNIX philosophy, so it will do one thing (paying invoices) and do it well.

#### Scheduling execution

The idea is to run Antaeus in background using a `Timer` that triggers every day at midnight, checks if it's the first day of the month and if it is, start paying every `PENDING` invoice.

#### Pay invoices

Paying functions signature

``` Kotlin
fun payInvoice(invoice: Invoice) -> Invoice
fun payInvoices(invoice: Collection<Invoice>) -> Collection<Invoice>
```

I'll follow a *functional* pattern as long as possible, so this two functions will output the input invoice with the updated status (`PAID`, `PENDING`, etc.).

This will allow me to easily compose them together using `map`, `filter`, etc. (i.e. filter invoices that had a network error after calling `payInvoices`)

> At this time timeouts and retries are not implemented.


#### New invoice statuses

After reading the existing code I decided to add other invoice statuses instead of using exceptions all the way up.

Execeptions are great and useful, but in this case the invoice status must be visible inside the database and through the API (and also from other services that could operate on the DB).

The existing statuses don't show if an invoice is invalid or has a different currency from the customer, or if Antaeus tried to pay it but failed due to customer's insufficent funds or some network errors.

A good solution could have been to only log errors and keep the original statuses, but this would have meant that to check the status of one invoice the user must had to search the system logs, and it's not so user friendly even for an engineer.

Also other services could not be aware of an error if they are only logged.

Another good solution could have been to add an enumerator subclass to `PENDING` and list error statuses here. Maybe in future improvments.


#### Timeout, retry, and coroutines

To retry payment and set a timeout for `charge` call I used coroutines.

```kotlin
// This constants inside `BillingService` set timeout and retries
const val TIMEOUT = 3000L   // Timeout in milliseconds
const val RETRIES = 5
```

`charge` is called inside `withTimeout` and if the call time exceed the timeout the function will try again to make the payment up to `RETRIES` times.


### Documentation

#### How to use `BillingService` 

```kotlin
// Instantiate
val billingService = BillingService(paymentService, invoiceService)

// Start background job that charge invoices
//  on every first day of the month
// Returns a `Timer` object
val backgroundJob = billingService.start()

// Stop the job
backgroundJob.cancel()

// Force the system to charge invoices (side-effect)
// Returns all the input invoices with the new status.
// NOTE: This function has side-effects, it fetch invoices
//        from DB and then set it's status back into the DB.
// NOTE: It retry to pay an invoice for 3 times and with
//        a timeout of 3s by default
billingService.payAll()

// Force the system to charge invoices (pure function)
// Returns all the input invoices with the new status.
// NOTE: This is a pure function, you must pass it the invoices
//        and make sure to update it's status in the DB.
// NOTE: It retry to pay an invoice for 3 times and with
//        a timeout of 3s by default
billingService.payInvoices(invoices)


// Pay a single invoice
billingService.payInvoice(invoice)

// Pay a single invoice with a timeout and retries
// Timeout in milliseconds : `Long` - default to 3000ms (3s)
// Retries : `Int` - default to 3
billingService.payInvoiceRetry(invoice, 4000L, 5)   // Timeout 4s and 5 retries
```

#### Invoice status

```kotlin
enum class InvoiceStatus {
    PAID,               // No action will be taken on this invoice
    PENDING,            // It will be charged
    PROCESSING,         // It is being charged
    INVALID,            // Invoice is invalid for unspecified reasons
    INVALID_CUSTOMER,   // Invoice is invalid. Customer doesn't exists
    CURRENCY_MISMATCH,  // Invoice is invalid. Invoice and customer currency doesn't match
    NETWORK_ERROR,      // Invoice has not been charged for some network error happened during charge (after 3 retries with 3s timeout)
    UNKNOWN_ERROR,      // Invoice has not been charged for some unknown reason
}
```


#### Settings

All settings for now are hard coded inside `BillingService` class.

```kotlin
// Day of the month to pay invoices
const val DAY_OF_THE_MONTH = 1

// Invoice charge call timeout in milliseconds
const val TIMEOUT = 3000L

// Number of times to retry to charge an invoice
const val RETRIES = 5
```


## Libraries currently in use
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
