package benchmark.domain8

import io.circe._
import io.circe.syntax._
import io.circe.parser._
import io.circe.generic.auto._

// Generic wrapper types
case class Box[A](value: A)
case class Pair[A, B](first: A, second: B)
case class Triple[A, B, C](first: A, second: B, third: C)
case class Tagged[A](tag: String, payload: A)
case class Validated[A](result: A, errors: List[String], warnings: List[String])
case class Versioned[A](data: A, version: Int, updatedAt: String, updatedBy: String)
case class Paginated[A](items: List[A], page: Int, pageSize: Int, totalItems: Int, totalPages: Int)
case class Cached[A](value: A, cachedAt: Long, ttlMs: Long, stale: Boolean)

// Value types used across instantiations
case class Email2(address: String)
case class UserId2(value: String)
case class Money2(amount: Double, currency: String)
case class GeoCoord2(lat: Double, lng: Double)
case class DateRange2(start: String, end: String)
case class Sku2(code: String)
case class Slug2(value: String)
case class MetricVal(name: String, value: Double, timestamp: Long)
case class StatusMsg(code: Int, message: String, details: String)
case class ConfigEntry(key: String, value: String, description: String, secret: Boolean)
case class FeatureFlag(name: String, enabled: Boolean, rolloutPct: Double)
case class RateLimit2(limit: Int, remaining: Int, resetAt: Long)
case class Locale3(lang: String, country: String)
case class Version2(major: Int, minor: Int, patch: Int)
case class Dimensions2(width: Double, height: Double, depth: Double)

object Domain8:
  def run(): Unit =
    // Box instantiations
    val b1 = Box(42); assert(decode[Box[Int]](b1.asJson.noSpaces) == Right(b1))
    val b2 = Box("hi"); assert(decode[Box[String]](b2.asJson.noSpaces) == Right(b2))
    val b3 = Box(true); assert(decode[Box[Boolean]](b3.asJson.noSpaces) == Right(b3))
    val b4 = Box(3.14); assert(decode[Box[Double]](b4.asJson.noSpaces) == Right(b4))
    val b5 = Box(Money2(9.99, "USD")); assert(decode[Box[Money2]](b5.asJson.noSpaces) == Right(b5))
    val b6 = Box(Email2("a@b")); assert(decode[Box[Email2]](b6.asJson.noSpaces) == Right(b6))
    val b7 = Box(GeoCoord2(0, 0)); assert(decode[Box[GeoCoord2]](b7.asJson.noSpaces) == Right(b7))
    val b8 = Box(Version2(1, 2, 3)); assert(decode[Box[Version2]](b8.asJson.noSpaces) == Right(b8))

    // Pair instantiations
    val p1 = Pair("x", 1); assert(decode[Pair[String, Int]](p1.asJson.noSpaces) == Right(p1))
    val p2 = Pair(Money2(1.0, "EUR"), GeoCoord2(0, 0)); assert(decode[Pair[Money2, GeoCoord2]](p2.asJson.noSpaces) == Right(p2))
    val p3 = Pair(true, 3.14); assert(decode[Pair[Boolean, Double]](p3.asJson.noSpaces) == Right(p3))
    val p4 = Pair(Email2("x"), UserId2("u")); assert(decode[Pair[Email2, UserId2]](p4.asJson.noSpaces) == Right(p4))
    val p5 = Pair(Sku2("s"), Money2(5, "GBP")); assert(decode[Pair[Sku2, Money2]](p5.asJson.noSpaces) == Right(p5))
    val p6 = Pair(Version2(1,0,0), DateRange2("a","b")); assert(decode[Pair[Version2, DateRange2]](p6.asJson.noSpaces) == Right(p6))

    // Triple instantiations
    val t1 = Triple("a", 1, true); assert(decode[Triple[String, Int, Boolean]](t1.asJson.noSpaces) == Right(t1))
    val t2 = Triple(Money2(1,"X"), Email2("e"), GeoCoord2(1,2)); assert(decode[Triple[Money2, Email2, GeoCoord2]](t2.asJson.noSpaces) == Right(t2))
    val t3 = Triple(Sku2("s"), Slug2("x"), Version2(2,0,1)); assert(decode[Triple[Sku2, Slug2, Version2]](t3.asJson.noSpaces) == Right(t3))

    // Tagged instantiations
    val tg1 = Tagged("u", UserId2("u1")); assert(decode[Tagged[UserId2]](tg1.asJson.noSpaces) == Right(tg1))
    val tg2 = Tagged("m", MetricVal("cpu", 42.0, 0L)); assert(decode[Tagged[MetricVal]](tg2.asJson.noSpaces) == Right(tg2))
    val tg3 = Tagged("s", Sku2("S1")); assert(decode[Tagged[Sku2]](tg3.asJson.noSpaces) == Right(tg3))
    val tg4 = Tagged("e", Email2("x@y")); assert(decode[Tagged[Email2]](tg4.asJson.noSpaces) == Right(tg4))
    val tg5 = Tagged("c", ConfigEntry("k","v","d",false)); assert(decode[Tagged[ConfigEntry]](tg5.asJson.noSpaces) == Right(tg5))
    val tg6 = Tagged("f", FeatureFlag("x", true, 50.0)); assert(decode[Tagged[FeatureFlag]](tg6.asJson.noSpaces) == Right(tg6))

    // Validated instantiations
    val v1 = Validated(Money2(100,"USD"), Nil, List("rounded")); assert(decode[Validated[Money2]](v1.asJson.noSpaces) == Right(v1))
    val v2 = Validated(StatusMsg(200,"OK",""), Nil, Nil); assert(decode[Validated[StatusMsg]](v2.asJson.noSpaces) == Right(v2))
    val v3 = Validated(Dimensions2(1,2,3), List("too small"), Nil); assert(decode[Validated[Dimensions2]](v3.asJson.noSpaces) == Right(v3))
    val v4 = Validated(Locale3("en","US"), Nil, Nil); assert(decode[Validated[Locale3]](v4.asJson.noSpaces) == Right(v4))

    // Versioned instantiations
    val vr1 = Versioned(Email2("a@b"), 3, "2024-01", "admin"); assert(decode[Versioned[Email2]](vr1.asJson.noSpaces) == Right(vr1))
    val vr2 = Versioned(RateLimit2(100, 50, 0L), 1, "2024-06", "system"); assert(decode[Versioned[RateLimit2]](vr2.asJson.noSpaces) == Right(vr2))
    val vr3 = Versioned(ConfigEntry("k","v","d",false), 5, "2024-09", "ops"); assert(decode[Versioned[ConfigEntry]](vr3.asJson.noSpaces) == Right(vr3))
    val vr4 = Versioned(FeatureFlag("dark", true, 100.0), 2, "2024-11", "pm"); assert(decode[Versioned[FeatureFlag]](vr4.asJson.noSpaces) == Right(vr4))

    // Paginated instantiations
    val pg1 = Paginated(List(Email2("a"), Email2("b")), 1, 10, 2, 1); assert(decode[Paginated[Email2]](pg1.asJson.noSpaces) == Right(pg1))
    val pg2 = Paginated(List(Money2(1,"X")), 1, 20, 1, 1); assert(decode[Paginated[Money2]](pg2.asJson.noSpaces) == Right(pg2))
    val pg3 = Paginated(List(MetricVal("x", 1.0, 0L)), 2, 50, 100, 2); assert(decode[Paginated[MetricVal]](pg3.asJson.noSpaces) == Right(pg3))
    val pg4 = Paginated(List(StatusMsg(200,"OK","")), 1, 10, 1, 1); assert(decode[Paginated[StatusMsg]](pg4.asJson.noSpaces) == Right(pg4))

    // Cached instantiations
    val c1 = Cached(GeoCoord2(37.7, -122.4), 1709251200000L, 60000, false); assert(decode[Cached[GeoCoord2]](c1.asJson.noSpaces) == Right(c1))
    val c2 = Cached(Version2(1,0,0), 0L, 3600000, true); assert(decode[Cached[Version2]](c2.asJson.noSpaces) == Right(c2))
    val c3 = Cached(Slug2("hello"), 0L, 300000, false); assert(decode[Cached[Slug2]](c3.asJson.noSpaces) == Right(c3))
    val c4 = Cached(Dimensions2(10,20,30), 0L, 600000, false); assert(decode[Cached[Dimensions2]](c4.asJson.noSpaces) == Right(c4))
