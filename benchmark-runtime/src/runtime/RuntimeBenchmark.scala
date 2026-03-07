package runtime

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
// Benchmark harness
// ═══════════════════════════════════════════════════════════════════════
object RuntimeBenchmark:
  import scala.compiletime.uninitialized
  import java.lang.management.ManagementFactory
  @volatile private var sink: Any = uninitialized

  private val threadMxBean =
    ManagementFactory.getThreadMXBean.asInstanceOf[com.sun.management.ThreadMXBean]

  private def threadAllocatedBytes(): Long =
    threadMxBean.getThreadAllocatedBytes(Thread.currentThread().threadId())

  case class Result(name: String, opsPerSec: Array[Double], bytesPerOp: Array[Double]):
    def median: Double =
      val s = opsPerSec.sorted
      s(s.length / 2)
    def min: Double = opsPerSec.min
    def max: Double = opsPerSec.max
    def medianAlloc: Double =
      val s = bytesPerOp.sorted
      s(s.length / 2)

  private def measure(name: String, warmupIters: Int, measureIters: Int)(op: => Any): Result =
    // Warmup
    for _ <- 1 to warmupIters do
      val end = System.nanoTime() + 1_000_000_000L
      while System.nanoTime() < end do sink = op

    // Measure: each iteration runs for 1 second
    val throughputs = new Array[Double](measureIters)
    val allocations = new Array[Double](measureIters)
    for i <- 0 until measureIters do
      var count = 0L
      val allocStart = threadAllocatedBytes()
      val start = System.nanoTime()
      val end = start + 1_000_000_000L
      while System.nanoTime() < end do
        sink = op
        count += 1
      val elapsed = (System.nanoTime() - start).toDouble / 1e9
      val allocEnd = threadAllocatedBytes()
      throughputs(i) = count / elapsed
      allocations(i) = (allocEnd - allocStart).toDouble / count

    Result(name, throughputs, allocations)

  private def formatAlloc(bytes: Double): String =
    if bytes >= 1024 * 1024 then f"${bytes / (1024 * 1024)}%.1f MB/op"
    else if bytes >= 1024 then f"${bytes / 1024}%.0f KB/op"
    else f"${bytes}%.0f B/op"

  private def printResult(r: Result): Unit =
    println(f"  ${r.name}%-24s ${r.median}%12.0f ops/sec  (min=${r.min}%.0f, max=${r.max}%.0f)  ${formatAlloc(r.medianAlloc)}%s")

  private def printComparison(baseline: Result, results: Seq[Result]): Unit =
    val baseMedian = baseline.median
    val baseAlloc = baseline.medianAlloc
    for r <- results do
      val ratio = r.median / baseMedian
      val allocRatio = if baseAlloc > 0 then f"  alloc ${r.medianAlloc / baseAlloc}%.2fx" else ""
      println(f"  ${r.name}%-24s ${ratio}%6.2fx vs ${baseline.name}$allocRatio")

  // ═════════════════════════════════════════════════════════════════════
  // Sample data
  // ═════════════════════════════════════════════════════════════════════
  private def sampleData: ApiResponse =
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
    ApiResponse(user, orders, "req-abc-123-def-456", 1709312400L)

  // ═════════════════════════════════════════════════════════════════════
  // Main
  // ═════════════════════════════════════════════════════════════════════
  def main(args: Array[String]): Unit =
    val warmup = args.headOption.flatMap(_.toIntOption).getOrElse(5)
    val iterations = args.lift(1).flatMap(_.toIntOption).getOrElse(5)

    val obj = sampleData

    // Pre-serialize with circe for reading benchmarks
    import CirceCodecs.given
    import io.circe.syntax.*
    val circeJsonBytes = CirceCodecs.printer.print(obj.asJson).getBytes(UTF_8)

    // Pre-serialize with jsoniter-scala native for reading benchmarks
    import JsoniterCodecs.given
    import com.github.plokhotnyuk.jsoniter_scala.core.*
    val jsoniterBytes = writeToArray(obj)(using JsoniterCodecs.given_JsonValueCodec_ApiResponse)

    // Pre-serialize with sanely-jsoniter for reading benchmarks (circe-compatible format)
    import SanelyJsoniterCodecs.given
    val sanelyBytes = writeToArray(obj)(using SanelyJsoniterCodecs.given_JsonValueCodec_ApiResponse)

    // Verify all approaches produce the same result
    val circeResult = io.circe.jawn.decodeByteArray[ApiResponse](circeJsonBytes)(using CirceCodecs.given_Codec_ApiResponse)
    assert(circeResult.isRight, s"circe decode failed: $circeResult")
    assert(circeResult.toOption.get == obj, "circe roundtrip mismatch")

    val jsoniterResult = readFromArray[ApiResponse](jsoniterBytes)(using JsoniterCodecs.given_JsonValueCodec_ApiResponse)
    assert(jsoniterResult == obj, "jsoniter roundtrip mismatch")

    val sanelyResult = readFromArray[ApiResponse](sanelyBytes)(using SanelyJsoniterCodecs.given_JsonValueCodec_ApiResponse)
    assert(sanelyResult == obj, "sanely-jsoniter roundtrip mismatch")

    // jsoniter-scala-circe bridge codec
    val jsonCirceCodec = com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec.jsonC3c

    val circeJsoniterResult = readFromArray[io.circe.Json](circeJsonBytes)(using jsonCirceCodec)
      .as[ApiResponse](using CirceCodecs.given_Codec_ApiResponse)
    assert(circeJsoniterResult.isRight, s"circe+jsoniter decode failed: $circeJsoniterResult")
    assert(circeJsoniterResult.toOption.get == obj, "circe+jsoniter roundtrip mismatch")

    // Cross-decode: sanely-jsoniter bytes decoded by circe (proves format compatibility)
    val crossResult = io.circe.jawn.decodeByteArray[ApiResponse](sanelyBytes)(using CirceCodecs.given_Codec_ApiResponse)
    assert(crossResult.isRight, s"cross-decode (sanely->circe) failed: $crossResult")
    assert(crossResult.toOption.get == obj, "cross-decode mismatch")

    println(s"Runtime benchmark: circe-jawn vs circe+jsoniter-bridge vs sanely-jsoniter vs jsoniter-scala")
    println(s"  warmup=$warmup iterations=$iterations (each 1 second)")
    println(s"  payload: ${circeJsonBytes.length} bytes (circe), ${sanelyBytes.length} bytes (sanely-jsoniter), ${jsoniterBytes.length} bytes (jsoniter-scala)")
    println()

    // ═══════════════════════════════════════════════════════════════════
    // READING benchmarks
    // ═══════════════════════════════════════════════════════════════════
    println("Reading (bytes -> case class):")
    println("-" * 70)

    val readCirceJawn = measure("circe-jawn", warmup, iterations) {
      io.circe.jawn.decodeByteArray[ApiResponse](circeJsonBytes)(using CirceCodecs.given_Codec_ApiResponse)
        .toOption.get
    }
    printResult(readCirceJawn)

    val readCirceJsoniter = measure("circe+jsoniter", warmup, iterations) {
      readFromArray[io.circe.Json](circeJsonBytes)(using jsonCirceCodec)
        .as[ApiResponse](using CirceCodecs.given_Codec_ApiResponse)
        .toOption.get
    }
    printResult(readCirceJsoniter)

    val readSanely = measure("sanely-jsoniter", warmup, iterations) {
      readFromArray[ApiResponse](sanelyBytes)(using SanelyJsoniterCodecs.given_JsonValueCodec_ApiResponse)
    }
    printResult(readSanely)

    val readJsoniter = measure("jsoniter-scala", warmup, iterations) {
      readFromArray[ApiResponse](jsoniterBytes)(using JsoniterCodecs.given_JsonValueCodec_ApiResponse)
    }
    printResult(readJsoniter)

    println()
    printComparison(readCirceJawn, Seq(readCirceJsoniter, readSanely, readJsoniter))

    println()

    // ═══════════════════════════════════════════════════════════════════
    // WRITING benchmarks
    // ═══════════════════════════════════════════════════════════════════
    println("Writing (case class -> bytes):")
    println("-" * 70)

    val writeCircePrinter = measure("circe-printer", warmup, iterations) {
      CirceCodecs.printer.print(obj.asJson(using CirceCodecs.given_Codec_ApiResponse)).getBytes(UTF_8)
    }
    printResult(writeCircePrinter)

    val writeCirceJsoniter = measure("circe+jsoniter", warmup, iterations) {
      writeToArray(obj.asJson(using CirceCodecs.given_Codec_ApiResponse))(using jsonCirceCodec)
    }
    printResult(writeCirceJsoniter)

    val writeSanely = measure("sanely-jsoniter", warmup, iterations) {
      writeToArray(obj)(using SanelyJsoniterCodecs.given_JsonValueCodec_ApiResponse)
    }
    printResult(writeSanely)

    val writeJsoniter = measure("jsoniter-scala", warmup, iterations) {
      writeToArray(obj)(using JsoniterCodecs.given_JsonValueCodec_ApiResponse)
    }
    printResult(writeJsoniter)

    println()
    printComparison(writeCircePrinter, Seq(writeCirceJsoniter, writeSanely, writeJsoniter))
