package configured.inventory

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import io.circe.derivation.Configuration
import io.circe.generic.semiauto.*

// ═══════════════════════════════════════════════════════════════════════
// Product / Inventory Management (~30 types)
// withDefaults, withDiscriminator
// ═══════════════════════════════════════════════════════════════════════

private given Configuration = Configuration.default.withDefaults

final case class Sku(value: String)
object Sku:
  given Codec.AsObject[Sku] = deriveConfiguredCodec

final case class ProductName(value: String, locale: String = "en")
object ProductName:
  given Codec.AsObject[ProductName] = deriveConfiguredCodec

final case class Price(amount: Double, currency: String = "USD")
object Price:
  given Codec.AsObject[Price] = deriveConfiguredCodec

final case class Weight(value: Double, unit: String = "kg")
object Weight:
  given Codec.AsObject[Weight] = deriveConfiguredCodec

final case class Dimensions(length: Double, width: Double, height: Double, unit: String = "cm")
object Dimensions:
  given Codec.AsObject[Dimensions] = deriveConfiguredCodec

final case class CategoryRef(id: String, name: String, parentId: String = "")
object CategoryRef:
  given Codec.AsObject[CategoryRef] = deriveConfiguredCodec

final case class Brand(name: String, logo: String = "", website: String = "")
object Brand:
  given Codec.AsObject[Brand] = deriveConfiguredCodec

final case class ProductImage(url: String, alt: String = "", primary: Boolean = false)
object ProductImage:
  given Codec.AsObject[ProductImage] = deriveConfiguredCodec

final case class VariantOption(name: String, value: String)
object VariantOption:
  given Codec.AsObject[VariantOption] = deriveConfiguredCodec

final case class ProductVariant(sku: Sku, options: List[VariantOption], price: Price, stock: Int = 0)
object ProductVariant:
  given Codec.AsObject[ProductVariant] = deriveConfiguredCodec

final case class InventoryLevel(sku: String, warehouseId: String, quantity: Int, reserved: Int = 0)
object InventoryLevel:
  given Codec.AsObject[InventoryLevel] = deriveConfiguredCodec

final case class Warehouse(id: String, name: String, location: String, active: Boolean = true)
object Warehouse:
  given Codec.AsObject[Warehouse] = deriveConfiguredCodec

final case class Supplier(id: String, name: String, contactEmail: String, leadTimeDays: Int = 7)
object Supplier:
  given Codec.AsObject[Supplier] = deriveConfiguredCodec

final case class PurchaseOrderLine(sku: String, quantity: Int, unitCost: Price)
object PurchaseOrderLine:
  given Codec.AsObject[PurchaseOrderLine] = deriveConfiguredCodec

final case class PurchaseOrder(id: String, supplierId: String, lines: List[PurchaseOrderLine], status: String = "draft")
object PurchaseOrder:
  given Codec.AsObject[PurchaseOrder] = deriveConfiguredCodec

final case class StockMovement(id: String, sku: String, fromWarehouse: String, toWarehouse: String, quantity: Int)
object StockMovement:
  given Codec.AsObject[StockMovement] = deriveConfiguredCodec

final case class RestockAlert(sku: String, currentQty: Int, threshold: Int, supplierId: String = "")
object RestockAlert:
  given Codec.AsObject[RestockAlert] = deriveConfiguredCodec

final case class ProductReview(id: String, productId: String, rating: Int, comment: String = "", verified: Boolean = false)
object ProductReview:
  given Codec.AsObject[ProductReview] = deriveConfiguredCodec

final case class PricingRule(id: String, name: String, discountPct: Double, minQuantity: Int = 1, active: Boolean = true)
object PricingRule:
  given Codec.AsObject[PricingRule] = deriveConfiguredCodec

final case class ProductBundle(id: String, name: String, skus: List[String], bundlePrice: Price)
object ProductBundle:
  given Codec.AsObject[ProductBundle] = deriveConfiguredCodec

final case class Product(
  id: String,
  name: ProductName,
  brand: Brand,
  category: CategoryRef,
  price: Price,
  weight: Weight,
  dimensions: Dimensions,
  images: List[ProductImage],
  variants: List[ProductVariant],
  active: Boolean = true,
)
object Product:
  given Codec.AsObject[Product] = deriveConfiguredCodec

// ADTs
sealed trait InventoryAction
object InventoryAction:
  private given Configuration = Configuration.default.withDefaults.withDiscriminator("action")
  given Codec.AsObject[InventoryAction] = deriveConfiguredCodec
final case class ReceiveStock(sku: String, quantity: Int, warehouseId: String) extends InventoryAction
object ReceiveStock:
  given Codec.AsObject[ReceiveStock] = deriveConfiguredCodec
final case class ShipStock(sku: String, quantity: Int, orderId: String) extends InventoryAction
object ShipStock:
  given Codec.AsObject[ShipStock] = deriveConfiguredCodec
final case class AdjustStock(sku: String, delta: Int, reason: String = "correction") extends InventoryAction
object AdjustStock:
  given Codec.AsObject[AdjustStock] = deriveConfiguredCodec
final case class TransferStock(sku: String, quantity: Int, fromId: String, toId: String) extends InventoryAction
object TransferStock:
  given Codec.AsObject[TransferStock] = deriveConfiguredCodec
final case class ReserveStock(sku: String, quantity: Int, orderId: String, expiresAt: String = "") extends InventoryAction
object ReserveStock:
  given Codec.AsObject[ReserveStock] = deriveConfiguredCodec

sealed trait ProductStatus
object ProductStatus:
  private given Configuration = Configuration.default.withDefaults.withDiscriminator("status")
  given Codec.AsObject[ProductStatus] = deriveConfiguredCodec
final case class DraftProduct(createdBy: String) extends ProductStatus
object DraftProduct:
  given Codec.AsObject[DraftProduct] = deriveConfiguredCodec
final case class PublishedProduct(publishedAt: String, channel: String = "web") extends ProductStatus
object PublishedProduct:
  given Codec.AsObject[PublishedProduct] = deriveConfiguredCodec
final case class ArchivedProduct(archivedAt: String, reason: String = "") extends ProductStatus
object ArchivedProduct:
  given Codec.AsObject[ArchivedProduct] = deriveConfiguredCodec
final case class DiscontinuedProduct(discontinuedAt: String, replacementSku: String = "") extends ProductStatus
object DiscontinuedProduct:
  given Codec.AsObject[DiscontinuedProduct] = deriveConfiguredCodec

object InventoryDomain:
  def run(): Unit =
    val product = Product(
      "p1", ProductName("Widget"), Brand("Acme"), CategoryRef("c1", "Gadgets"),
      Price(29.99), Weight(0.5), Dimensions(10, 5, 3),
      List(ProductImage("img.png", "widget", true)),
      List(ProductVariant(Sku("W-RED"), List(VariantOption("color", "red")), Price(29.99), 100)),
    )
    assert(decode[Product](product.asJson.noSpaces) == Right(product))

    val action: InventoryAction = ReceiveStock("W-RED", 50, "wh1")
    assert(decode[InventoryAction](action.asJson.noSpaces) == Right(action))

    val status: ProductStatus = PublishedProduct("2024-01-15")
    assert(decode[ProductStatus](status.asJson.noSpaces) == Right(status))

    val order = PurchaseOrder("po1", "sup1", List(PurchaseOrderLine("W-RED", 100, Price(15.0))))
    assert(decode[PurchaseOrder](order.asJson.noSpaces) == Right(order))

    val bundle = ProductBundle("b1", "Starter Kit", List("W-RED", "W-BLUE"), Price(49.99))
    assert(decode[ProductBundle](bundle.asJson.noSpaces) == Right(bundle))
