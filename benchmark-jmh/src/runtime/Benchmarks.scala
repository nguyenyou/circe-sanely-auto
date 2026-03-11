package runtime

import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit
import java.nio.charset.StandardCharsets.UTF_8

// ═══════════════════════════════════════════════════════════════════════
// Data models — neutral, no library-specific annotations
// ═══════════════════════════════════════════════════════════════════════
case class Address(street: String, city: String, state: String, zip: String, country: String)
case class User(id: Long, name: String, email: String, age: Int, active: Boolean, address: Address, tags: List[String],
                phone: Option[String], bio: Option[String])
case class OrderItem(productId: Long, name: String, quantity: Int, price: Double)
sealed trait OrderStatus
case class Delivered(deliveredAt: String) extends OrderStatus
case class Shipped(carrier: String, tracking: Option[String]) extends OrderStatus
case class Processing(estimatedDays: Int) extends OrderStatus
case class Order(id: Long, userId: Long, items: List[OrderItem], total: Double, status: OrderStatus,
                 createdAt: String, note: Option[String])
case class ApiResponse(user: User, orders: List[Order], requestId: String, timestamp: Long)

// ═══════════════════════════════════════════════════════════════════════
// Circe codecs — derived via sanely-auto (semiauto for explicitness)
// ═══════════════════════════════════════════════════════════════════════
object CirceCodecs:
  import io.circe.*
  import io.circe.generic.semiauto.*
  given Codec[Address] = deriveCodec
  given Codec[User] = deriveCodec
  given Codec[OrderItem] = deriveCodec
  given Codec[Delivered] = deriveCodec
  given Codec[Shipped] = deriveCodec
  given Codec[Processing] = deriveCodec
  given Codec[OrderStatus] = deriveCodec
  given Codec[Order] = deriveCodec
  given Codec[ApiResponse] = deriveCodec
  val printer: Printer = Printer.noSpaces.copy(dropNullValues = true)

// ═══════════════════════════════════════════════════════════════════════
// jsoniter-scala codecs — derived via JsonCodecMaker.make
// ═══════════════════════════════════════════════════════════════════════
object JsoniterCodecs:
  import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
  import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
  given JsonValueCodec[ApiResponse] = JsonCodecMaker.make

// ═══════════════════════════════════════════════════════════════════════
// sanely-jsoniter codecs — circe-format-compatible direct streaming
// ═══════════════════════════════════════════════════════════════════════
object SanelyJsoniterCodecs:
  import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
  import sanely.jsoniter.semiauto.*
  given JsonValueCodec[Address] = deriveJsoniterCodec
  given JsonValueCodec[User] = deriveJsoniterCodec
  given JsonValueCodec[OrderItem] = deriveJsoniterCodec
  given JsonValueCodec[Delivered] = deriveJsoniterCodec
  given JsonValueCodec[Shipped] = deriveJsoniterCodec
  given JsonValueCodec[Processing] = deriveJsoniterCodec
  given JsonValueCodec[OrderStatus] = deriveJsoniterCodec
  given JsonValueCodec[Order] = deriveJsoniterCodec
  given JsonValueCodec[ApiResponse] = deriveJsoniterCodec

// ═══════════════════════════════════════════════════════════════════════
// JMH Benchmark State
// ═══════════════════════════════════════════════════════════════════════
@State(Scope.Thread)
class BenchmarkState {
  import scala.compiletime.uninitialized
  var obj: ApiResponse = uninitialized
  var circeJsonBytes: Array[Byte] = uninitialized
  var jsoniterBytes: Array[Byte] = uninitialized
  var sanelyBytes: Array[Byte] = uninitialized

  // jsoniter-scala-circe bridge
  val jsonCirceCodec = com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec.jsonC3c

  @Setup(Level.Trial)
  def setup(): Unit = {
    val address = Address("123 Main St", "Springfield", "IL", "62701", "US")
    val user = User(42L, "Alice Johnson", "alice@example.com", 30, true, address,
      List("premium", "early-adopter", "beta-tester"),
      Some("+1-555-0123"), None)
    val items1 = List(
      OrderItem(101L, "Wireless Mouse", 2, 29.99),
      OrderItem(102L, "USB-C Cable", 5, 12.49),
      OrderItem(103L, "Mechanical Keyboard", 1, 149.99),
    )
    val items2 = List(
      OrderItem(201L, "Monitor Stand", 1, 79.99),
      OrderItem(202L, "Desk Lamp", 2, 34.99),
    )
    val items3 = List(
      OrderItem(301L, "Notebook Pack", 3, 8.99),
      OrderItem(302L, "Pen Set", 1, 15.99),
      OrderItem(303L, "Sticky Notes", 10, 3.49),
      OrderItem(304L, "Binder Clips", 4, 5.99),
    )
    val orders = List(
      Order(1001L, 42L, items1, 234.95, Delivered("2024-01-20T16:00:00Z"),
        "2024-01-15T10:30:00Z", Some("Leave at door")),
      Order(1002L, 42L, items2, 149.97, Shipped("FedEx", Some("FX123456789")),
        "2024-02-20T14:15:00Z", None),
      Order(1003L, 42L, items3, 86.89, Processing(3),
        "2024-03-01T09:00:00Z", None),
    )
    obj = ApiResponse(user, orders, "req-abc-123-def-456", 1709312400L)

    import CirceCodecs.given
    import io.circe.syntax.*
    circeJsonBytes = CirceCodecs.printer.print(obj.asJson).getBytes(UTF_8)

    import com.github.plokhotnyuk.jsoniter_scala.core.*
    jsoniterBytes = writeToArray(obj)(using JsoniterCodecs.given_JsonValueCodec_ApiResponse)
    sanelyBytes = writeToArray(obj)(using SanelyJsoniterCodecs.given_JsonValueCodec_ApiResponse)
  }
}

// ═══════════════════════════════════════════════════════════════════════
// Reading benchmarks (bytes -> case class)
// ═══════════════════════════════════════════════════════════════════════
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgs = Array("-Xms512m", "-Xmx512m"))
class ReadBenchmark {

  @Benchmark
  def circeJawn(state: BenchmarkState): ApiResponse = {
    import CirceCodecs.given
    io.circe.jawn.decodeByteArray[ApiResponse](state.circeJsonBytes).toOption.get
  }

  @Benchmark
  def circeJsoniterBridge(state: BenchmarkState): ApiResponse = {
    import CirceCodecs.given
    import com.github.plokhotnyuk.jsoniter_scala.core.*
    readFromArray[io.circe.Json](state.circeJsonBytes)(using state.jsonCirceCodec)
      .as[ApiResponse].toOption.get
  }

  @Benchmark
  def sanelyJsoniter(state: BenchmarkState): ApiResponse = {
    import com.github.plokhotnyuk.jsoniter_scala.core.*
    readFromArray[ApiResponse](state.sanelyBytes)(using SanelyJsoniterCodecs.given_JsonValueCodec_ApiResponse)
  }

  @Benchmark
  def jsoniterScala(state: BenchmarkState): ApiResponse = {
    import com.github.plokhotnyuk.jsoniter_scala.core.*
    readFromArray[ApiResponse](state.jsoniterBytes)(using JsoniterCodecs.given_JsonValueCodec_ApiResponse)
  }
}

// ═══════════════════════════════════════════════════════════════════════
// Writing benchmarks (case class -> bytes)
// ═══════════════════════════════════════════════════════════════════════
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgs = Array("-Xms512m", "-Xmx512m"))
class WriteBenchmark {

  @Benchmark
  def circePrinter(state: BenchmarkState): Array[Byte] = {
    import CirceCodecs.given
    import io.circe.syntax.*
    CirceCodecs.printer.print(state.obj.asJson).getBytes(UTF_8)
  }

  @Benchmark
  def circeJsoniterBridge(state: BenchmarkState): Array[Byte] = {
    import CirceCodecs.given
    import io.circe.syntax.*
    import com.github.plokhotnyuk.jsoniter_scala.core.*
    writeToArray(state.obj.asJson)(using state.jsonCirceCodec)
  }

  @Benchmark
  def sanelyJsoniter(state: BenchmarkState): Array[Byte] = {
    import com.github.plokhotnyuk.jsoniter_scala.core.*
    writeToArray(state.obj)(using SanelyJsoniterCodecs.given_JsonValueCodec_ApiResponse)
  }

  @Benchmark
  def jsoniterScala(state: BenchmarkState): Array[Byte] = {
    import com.github.plokhotnyuk.jsoniter_scala.core.*
    writeToArray(state.obj)(using JsoniterCodecs.given_JsonValueCodec_ApiResponse)
  }
}
