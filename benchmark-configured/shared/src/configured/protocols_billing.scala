package configured.billing

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import io.circe.derivation.Configuration
import io.circe.generic.semiauto.*

// ═══════════════════════════════════════════════════════════════════════
// Billing / Invoices / Payments (~25 types)
// withDefaults, withDiscriminator
// ═══════════════════════════════════════════════════════════════════════

private given Configuration = Configuration.default.withDefaults

final case class Money(amount: Double, currency: String = "USD")
object Money:
  given Codec.AsObject[Money] = deriveConfiguredCodec

final case class TaxRate(rate: Double, jurisdiction: String, category: String = "standard")
object TaxRate:
  given Codec.AsObject[TaxRate] = deriveConfiguredCodec

final case class TaxLine(description: String, rate: TaxRate, amount: Money)
object TaxLine:
  given Codec.AsObject[TaxLine] = deriveConfiguredCodec

final case class InvoiceLineItem(description: String, quantity: Int, unitPrice: Money, taxable: Boolean = true)
object InvoiceLineItem:
  given Codec.AsObject[InvoiceLineItem] = deriveConfiguredCodec

final case class Discount(code: String, description: String = "", amount: Money)
object Discount:
  given Codec.AsObject[Discount] = deriveConfiguredCodec

final case class BillingAddress(name: String, line1: String, line2: String = "", city: String, state: String, zip: String, country: String = "US")
object BillingAddress:
  given Codec.AsObject[BillingAddress] = deriveConfiguredCodec

final case class Invoice(
  id: String,
  customerId: String,
  lineItems: List[InvoiceLineItem],
  taxes: List[TaxLine],
  discounts: List[Discount],
  subtotal: Money,
  total: Money,
  billingAddress: BillingAddress,
  issuedAt: String,
  dueAt: String,
  status: String = "pending",
)
object Invoice:
  given Codec.AsObject[Invoice] = deriveConfiguredCodec

final case class PaymentRef(transactionId: String, gateway: String, timestamp: String)
object PaymentRef:
  given Codec.AsObject[PaymentRef] = deriveConfiguredCodec

final case class Refund(id: String, invoiceId: String, amount: Money, reason: String = "", processedAt: String)
object Refund:
  given Codec.AsObject[Refund] = deriveConfiguredCodec

final case class CreditNote(id: String, customerId: String, amount: Money, reason: String, validUntil: String = "")
object CreditNote:
  given Codec.AsObject[CreditNote] = deriveConfiguredCodec

final case class SubscriptionPlan(id: String, name: String, price: Money, interval: String = "monthly", trialDays: Int = 0)
object SubscriptionPlan:
  given Codec.AsObject[SubscriptionPlan] = deriveConfiguredCodec

final case class Subscription(id: String, customerId: String, plan: SubscriptionPlan, startDate: String, status: String = "active")
object Subscription:
  given Codec.AsObject[Subscription] = deriveConfiguredCodec

final case class UsageRecord(subscriptionId: String, metric: String, quantity: Double, recordedAt: String)
object UsageRecord:
  given Codec.AsObject[UsageRecord] = deriveConfiguredCodec

final case class BillingPeriod(start: String, end: String, invoiceId: String = "")
object BillingPeriod:
  given Codec.AsObject[BillingPeriod] = deriveConfiguredCodec

final case class PaymentRetry(attemptNumber: Int, scheduledAt: String, lastError: String = "")
object PaymentRetry:
  given Codec.AsObject[PaymentRetry] = deriveConfiguredCodec

final case class DunningRecord(customerId: String, invoiceId: String, retries: List[PaymentRetry], escalated: Boolean = false)
object DunningRecord:
  given Codec.AsObject[DunningRecord] = deriveConfiguredCodec

// ADTs
sealed trait PaymentMethod
object PaymentMethod:
  private given Configuration = Configuration.default.withDefaults.withDiscriminator("method")
  given Codec.AsObject[PaymentMethod] = deriveConfiguredCodec
final case class CardPayment(last4: String, brand: String, expiryMonth: Int, expiryYear: Int) extends PaymentMethod
object CardPayment:
  given Codec.AsObject[CardPayment] = deriveConfiguredCodec
final case class BankTransfer(bankName: String, reference: String) extends PaymentMethod
object BankTransfer:
  given Codec.AsObject[BankTransfer] = deriveConfiguredCodec
final case class WalletPayment(provider: String, accountId: String) extends PaymentMethod
object WalletPayment:
  given Codec.AsObject[WalletPayment] = deriveConfiguredCodec
final case class CheckPayment(checkNumber: String, bankName: String = "") extends PaymentMethod
object CheckPayment:
  given Codec.AsObject[CheckPayment] = deriveConfiguredCodec

sealed trait InvoiceEvent
object InvoiceEvent:
  private given Configuration = Configuration.default.withDefaults.withDiscriminator("event")
  given Codec.AsObject[InvoiceEvent] = deriveConfiguredCodec
final case class InvoiceCreated(invoiceId: String, createdAt: String) extends InvoiceEvent
object InvoiceCreated:
  given Codec.AsObject[InvoiceCreated] = deriveConfiguredCodec
final case class InvoiceSent(invoiceId: String, sentAt: String, channel: String = "email") extends InvoiceEvent
object InvoiceSent:
  given Codec.AsObject[InvoiceSent] = deriveConfiguredCodec
final case class InvoicePaid(invoiceId: String, paidAt: String, paymentRef: PaymentRef) extends InvoiceEvent
object InvoicePaid:
  given Codec.AsObject[InvoicePaid] = deriveConfiguredCodec
final case class InvoiceOverdue(invoiceId: String, dueAt: String, daysPastDue: Int) extends InvoiceEvent
object InvoiceOverdue:
  given Codec.AsObject[InvoiceOverdue] = deriveConfiguredCodec
final case class InvoiceVoided(invoiceId: String, voidedAt: String, reason: String = "") extends InvoiceEvent
object InvoiceVoided:
  given Codec.AsObject[InvoiceVoided] = deriveConfiguredCodec

object BillingDomain:
  def run(): Unit =
    val addr = BillingAddress("Acme Inc", "123 Main St", "", "NYC", "NY", "10001")
    val item = InvoiceLineItem("Widget x10", 10, Money(29.99))
    val tax = TaxLine("Sales Tax", TaxRate(0.08, "NY"), Money(24.0))
    val invoice = Invoice("inv1", "c1", List(item), List(tax), Nil, Money(299.90), Money(323.90), addr, "2024-01-15", "2024-02-15")
    assert(decode[Invoice](invoice.asJson.noSpaces) == Right(invoice))

    val pm: PaymentMethod = CardPayment("4242", "visa", 12, 2025)
    assert(decode[PaymentMethod](pm.asJson.noSpaces) == Right(pm))

    val event: InvoiceEvent = InvoicePaid("inv1", "2024-01-20", PaymentRef("tx1", "stripe", "2024-01-20"))
    assert(decode[InvoiceEvent](event.asJson.noSpaces) == Right(event))

    val sub = Subscription("s1", "c1", SubscriptionPlan("plan1", "Pro", Money(99.0)), "2024-01-01")
    assert(decode[Subscription](sub.asJson.noSpaces) == Right(sub))

    val refund = Refund("r1", "inv1", Money(50.0), "partial", "2024-02-01")
    assert(decode[Refund](refund.asJson.noSpaces) == Right(refund))
