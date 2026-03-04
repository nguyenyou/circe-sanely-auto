package benchmark.domain2

import io.circe._
import io.circe.syntax._
import io.circe.parser._
import io.circe.generic.auto._

case class Money(amount: Double, currency: String)
case class ProductId(value: String)
case class Sku(code: String)
case class Barcode(format: String, value: String)
case class Dimensions(width: Double, height: Double, depth: Double, unit: String)
case class Weight(value: Double, unit: String)
case class ProductImage(url: String, alt: String, width: Int, height: Int, isPrimary: Boolean)
case class ProductVariant(sku: Sku, barcode: Barcode, color: String, size: String, price: Money, compareAtPrice: Money, stock: Int)
case class ProductMeta(brand: String, manufacturer: String, countryOfOrigin: String, material: String)
case class ProductInfo(id: ProductId, name: String, description: String, meta: ProductMeta, dimensions: Dimensions, weight: Weight, images: List[ProductImage], variants: List[ProductVariant], tags: List[String])

case class CartItem(productId: ProductId, variantSku: Sku, quantity: Int, unitPrice: Money)
case class Discount(code: String, description: String, amount: Money, percentage: Double)
case class Cart(items: List[CartItem], discounts: List[Discount], subtotal: Money, tax: Money, shipping: Money, total: Money)

sealed trait PaymentMethod
case class CreditCardPay(last4: String, brand: String, expMonth: Int, expYear: Int) extends PaymentMethod
case class BankTransferPay(bankName: String, accountLast4: String, routingLast4: String) extends PaymentMethod
case class DigitalWalletPay(provider: String, email: String) extends PaymentMethod
case class GiftCardPay(code: String, pin: String, balance: Money) extends PaymentMethod
case class CryptoPay(wallet: String, network: String, txHash: String) extends PaymentMethod
case class BuyNowPayLater(provider: String, installments: Int, monthlyAmount: Money) extends PaymentMethod

sealed trait OrderStatus
case class OrderPending(estimatedShip: String) extends OrderStatus
case class OrderConfirmed(confirmedAt: String, estimatedDelivery: String) extends OrderStatus
case class OrderProcessing(startedAt: String, warehouseId: String) extends OrderStatus
case class OrderShipped(carrier: String, tracking: String, shippedAt: String) extends OrderStatus
case class OrderOutForDelivery(estimatedArrival: String, driverName: String) extends OrderStatus
case class OrderDelivered(deliveredAt: String, signature: String, photoUrl: String) extends OrderStatus
case class OrderCancelled(reason: String, cancelledAt: String, refundAmount: Double) extends OrderStatus
case class OrderReturned(returnReason: String, returnedAt: String, refundStatus: String) extends OrderStatus

case class ShippingInfo(recipientName: String, street: String, city: String, state: String, zip: String, phone: String, instructions: String)
case class OrderSummary(id: String, itemCount: Int, total: Money, payment: PaymentMethod, status: OrderStatus, shipping: ShippingInfo)

case class Review(productId: ProductId, userId: String, rating: Int, title: String, body: String, helpful: Int, verified: Boolean, createdAt: String)
case class Wishlist(id: String, name: String, items: List[ProductId], isPublic: Boolean, createdAt: String)

object Domain2:
  def run(): Unit =
    val img = ProductImage("http://x.com/img.jpg", "widget", 800, 600, true)
    val variant = ProductVariant(Sku("SKU-001"), Barcode("EAN13", "5901234123457"), "red", "L", Money(29.99, "USD"), Money(39.99, "USD"), 42)
    val product = ProductInfo(ProductId("p1"), "Widget", "A fine widget", ProductMeta("Acme", "Acme Corp", "US", "steel"),
      Dimensions(10, 5, 3, "cm"), Weight(0.5, "kg"), List(img), List(variant), List("tools", "sale"))
    assert(decode[ProductInfo](product.asJson.noSpaces) == Right(product))

    val discount = Discount("SAVE10", "10% off", Money(6.00, "USD"), 10.0)
    val cart = Cart(List(CartItem(ProductId("p1"), Sku("SKU-001"), 2, Money(29.99, "USD"))),
      List(discount), Money(59.98, "USD"), Money(5.40, "USD"), Money(5.00, "USD"), Money(64.38, "USD"))
    assert(decode[Cart](cart.asJson.noSpaces) == Right(cart))

    val order = OrderSummary("ord-1", 3, Money(65.38, "USD"), CreditCardPay("4242", "visa", 12, 2026),
      OrderShipped("UPS", "1Z999", "2024-03-01"), ShippingInfo("Bob", "456 Oak", "LA", "CA", "90001", "555-0123", "Leave at door"))
    assert(decode[OrderSummary](order.asJson.noSpaces) == Right(order))

    val review = Review(ProductId("p1"), "u1", 5, "Great!", "Love it", 12, true, "2024-03-15")
    assert(decode[Review](review.asJson.noSpaces) == Right(review))

    val wishlist = Wishlist("w1", "Birthday", List(ProductId("p1"), ProductId("p2")), false, "2024-01-01")
    assert(decode[Wishlist](wishlist.asJson.noSpaces) == Right(wishlist))
