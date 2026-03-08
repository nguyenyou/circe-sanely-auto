package jsoniterbench

// Domain 2: E-commerce

case class ProductId(value: String)
case class Sku(code: String)
case class Price(amount: Double, currency: String)
case class Weight(value: Double, unit: String)
case class Dimensions(width: Double, height: Double, depth: Double, unit: String)
case class ProductImage(url: String, alt: String, imgWidth: Int, imgHeight: Int, isPrimary: Boolean)
case class ProductVariant(sku: Sku, name: String, price: Price, inventory: Int, attributes: Map[String, String])
case class ProductReview(userId: UserId, rating: Int, title: String, body: String, helpful: Int, createdAt: String)
case class ProductListing(id: ProductId, name: String, description: String, price: Price, weight: Option[Weight], dimensions: Option[Dimensions], images: List[ProductImage], variants: List[ProductVariant], tags: List[String])

case class CategoryId(value: String)
case class Category(id: CategoryId, name: String, slug: String, parentId: Option[CategoryId], sortOrder: Int)
case class Brand(id: String, name: String, logo: Option[String], website: Option[String])
case class InventoryRecord(sku: Sku, warehouse: String, quantity: Int, reserved: Int, reorderPoint: Int)
case class CartItem(productId: ProductId, variant: Option[Sku], quantity: Int, unitPrice: Price)
case class Cart(id: String, userId: Option[UserId], items: List[CartItem], createdAt: String, updatedAt: String)

case class OrderId(value: String)
case class OrderLine(productId: ProductId, name: String, quantity: Int, unitPrice: Price, totalPrice: Price)
case class ShippingAddress(name: String, line1: String, line2: Option[String], city: String, state: String, zip: String, country: String, phone: Option[String])
case class ShippingInfo(method: String, carrier: String, trackingNumber: Option[String], estimatedDelivery: Option[String], cost: Price)
case class Order(id: OrderId, userId: UserId, lines: List[OrderLine], shipping: ShippingAddress, shippingInfo: Option[ShippingInfo], subtotal: Price, tax: Price, total: Price, createdAt: String)

sealed trait PaymentMethod
case class CreditCard(last4: String, brand: String, expiryMonth: Int, expiryYear: Int) extends PaymentMethod
case class BankTransfer(bankName: String, accountLast4: String) extends PaymentMethod
case class PayPalPayment(email: String) extends PaymentMethod
case class CryptoPayment(wallet: String, coin: String) extends PaymentMethod
case class GiftCardPayment(code: String, balance: Price) extends PaymentMethod

sealed trait OrderStatus
case class PendingOrder(estimatedShip: String) extends OrderStatus
case class ProcessingOrder(startedAt: String) extends OrderStatus
case class ShippedOrder(shippedAt: String, trackingNumber: String) extends OrderStatus
case class DeliveredOrder(deliveredAt: String, signedBy: Option[String]) extends OrderStatus
case class CancelledOrder(cancelledAt: String, reason: String) extends OrderStatus
case class RefundedOrder(refundedAt: String, amount: Price) extends OrderStatus

case class Coupon(code: String, discountPct: Double, maxUses: Int, usedCount: Int, expiresAt: String)
case class TaxRate(country: String, state: Option[String], rate: Double, taxCategory: String)
case class InvoiceLine(description: String, quantity: Int, unitPrice: Price, total: Price)
case class Invoice(id: String, orderId: OrderId, lines: List[InvoiceLine], subtotal: Price, tax: Price, total: Price, issuedAt: String)
