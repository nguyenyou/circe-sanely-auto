package benchmark

import io.circe._
import io.circe.syntax._
import io.circe.parser._
import io.circe.generic.auto._

// ═══════════════════════════════════════════════════════════════════════
// Wide case classes (stress field count × derivation)
// ═══════════════════════════════════════════════════════════════════════
case class Wide22A(f1: String, f2: Int, f3: Double, f4: Boolean, f5: String, f6: Int, f7: Double, f8: Boolean, f9: String, f10: Int, f11: String, f12: Int, f13: Double, f14: Boolean, f15: String, f16: Int, f17: Double, f18: Boolean, f19: String, f20: Int, f21: Double, f22: Boolean)
case class Wide22B(g1: String, g2: Int, g3: Double, g4: Boolean, g5: String, g6: Int, g7: Double, g8: Boolean, g9: String, g10: Int, g11: String, g12: Int, g13: Double, g14: Boolean, g15: String, g16: Int, g17: Double, g18: Boolean, g19: String, g20: Int, g21: Double, g22: Boolean)
case class Wide22C(h1: String, h2: Int, h3: Double, h4: Boolean, h5: String, h6: Int, h7: Double, h8: Boolean, h9: String, h10: Int, h11: String, h12: Int, h13: Double, h14: Boolean, h15: String, h16: Int, h17: Double, h18: Boolean, h19: String, h20: Int, h21: Double, h22: Boolean)
case class Wide22D(i1: String, i2: Int, i3: Double, i4: Boolean, i5: String, i6: Int, i7: Double, i8: Boolean, i9: String, i10: Int, i11: String, i12: Int, i13: Double, i14: Boolean, i15: String, i16: Int, i17: Double, i18: Boolean, i19: String, i20: Int, i21: Double, i22: Boolean)
case class Wide22E(j1: String, j2: Int, j3: Double, j4: Boolean, j5: String, j6: Int, j7: Double, j8: Boolean, j9: String, j10: Int, j11: String, j12: Int, j13: Double, j14: Boolean, j15: String, j16: Int, j17: Double, j18: Boolean, j19: String, j20: Int, j21: Double, j22: Boolean)
case class Wide22F(k1: String, k2: Int, k3: Double, k4: Boolean, k5: String, k6: Int, k7: Double, k8: Boolean, k9: String, k10: Int, k11: String, k12: Int, k13: Double, k14: Boolean, k15: String, k16: Int, k17: Double, k18: Boolean, k19: String, k20: Int, k21: Double, k22: Boolean)
case class Wide22G(l1: String, l2: Int, l3: Double, l4: Boolean, l5: String, l6: Int, l7: Double, l8: Boolean, l9: String, l10: Int, l11: String, l12: Int, l13: Double, l14: Boolean, l15: String, l16: Int, l17: Double, l18: Boolean, l19: String, l20: Int, l21: Double, l22: Boolean)
case class Wide22H(m1: String, m2: Int, m3: Double, m4: Boolean, m5: String, m6: Int, m7: Double, m8: Boolean, m9: String, m10: Int, m11: String, m12: Int, m13: Double, m14: Boolean, m15: String, m16: Int, m17: Double, m18: Boolean, m19: String, m20: Int, m21: Double, m22: Boolean)

object WideTypes:
  private val w = ("a",1,1.0,true,"b",2,2.0,false,"c",3,"d",4,4.0,true,"e",5,5.0,false,"f",6,6.0,true)
  def run(): Unit =
    val a = Wide22A(w._1,w._2,w._3,w._4,w._5,w._6,w._7,w._8,w._9,w._10,w._11,w._12,w._13,w._14,w._15,w._16,w._17,w._18,w._19,w._20,w._21,w._22)
    assert(decode[Wide22A](a.asJson.noSpaces) == Right(a))
    val b = Wide22B(w._1,w._2,w._3,w._4,w._5,w._6,w._7,w._8,w._9,w._10,w._11,w._12,w._13,w._14,w._15,w._16,w._17,w._18,w._19,w._20,w._21,w._22)
    assert(decode[Wide22B](b.asJson.noSpaces) == Right(b))
    val c = Wide22C(w._1,w._2,w._3,w._4,w._5,w._6,w._7,w._8,w._9,w._10,w._11,w._12,w._13,w._14,w._15,w._16,w._17,w._18,w._19,w._20,w._21,w._22)
    assert(decode[Wide22C](c.asJson.noSpaces) == Right(c))
    val d = Wide22D(w._1,w._2,w._3,w._4,w._5,w._6,w._7,w._8,w._9,w._10,w._11,w._12,w._13,w._14,w._15,w._16,w._17,w._18,w._19,w._20,w._21,w._22)
    assert(decode[Wide22D](d.asJson.noSpaces) == Right(d))
    val e = Wide22E(w._1,w._2,w._3,w._4,w._5,w._6,w._7,w._8,w._9,w._10,w._11,w._12,w._13,w._14,w._15,w._16,w._17,w._18,w._19,w._20,w._21,w._22)
    assert(decode[Wide22E](e.asJson.noSpaces) == Right(e))
    val f = Wide22F(w._1,w._2,w._3,w._4,w._5,w._6,w._7,w._8,w._9,w._10,w._11,w._12,w._13,w._14,w._15,w._16,w._17,w._18,w._19,w._20,w._21,w._22)
    assert(decode[Wide22F](f.asJson.noSpaces) == Right(f))
    val g = Wide22G(w._1,w._2,w._3,w._4,w._5,w._6,w._7,w._8,w._9,w._10,w._11,w._12,w._13,w._14,w._15,w._16,w._17,w._18,w._19,w._20,w._21,w._22)
    assert(decode[Wide22G](g.asJson.noSpaces) == Right(g))
    val h = Wide22H(w._1,w._2,w._3,w._4,w._5,w._6,w._7,w._8,w._9,w._10,w._11,w._12,w._13,w._14,w._15,w._16,w._17,w._18,w._19,w._20,w._21,w._22)
    assert(decode[Wide22H](h.asJson.noSpaces) == Right(h))

// ═══════════════════════════════════════════════════════════════════════
// Sum type variety (many distinct sealed hierarchies)
// ═══════════════════════════════════════════════════════════════════════
sealed trait HttpMethod
case class GetMethod(cached: Boolean) extends HttpMethod
case class PostMethod(idempotent: Boolean) extends HttpMethod
case class PutMethod(upsert: Boolean) extends HttpMethod
case class DeleteMethod(soft: Boolean) extends HttpMethod
case class PatchMethod(partial: Boolean) extends HttpMethod

sealed trait LogLevel
case class TraceLevel(verbose: Boolean) extends LogLevel
case class DebugLevel(module: String) extends LogLevel
case class InfoLevel(source: String) extends LogLevel
case class WarnLevel(stackTrace: Boolean) extends LogLevel
case class ErrorLevel(fatal: Boolean) extends LogLevel

sealed trait Shape
case class CircleShape(radius: Double) extends Shape
case class RectShape(width: Double, height: Double) extends Shape
case class TriShape(a: Double, b: Double, c: Double) extends Shape
case class EllipseShape(rx: Double, ry: Double) extends Shape
case class PolygonShape(sides: Int, radius: Double) extends Shape

sealed trait CurrencyType
case class UsdCurrency(cents: Int) extends CurrencyType
case class EurCurrency(cents: Int) extends CurrencyType
case class GbpCurrency(pence: Int) extends CurrencyType
case class JpyCurrency(yen: Int) extends CurrencyType
case class ChfCurrency(rappen: Int) extends CurrencyType

sealed trait Permission
case class ReadPerm(resource: String) extends Permission
case class WritePerm(resource: String) extends Permission
case class AdminPerm(scope: String) extends Permission
case class DeletePerm(resource: String, soft: Boolean) extends Permission
case class ExecutePerm(action: String) extends Permission

sealed trait NotifChannel
case class EmailChan(to: String, subject: String) extends NotifChannel
case class SmsChan(to: String) extends NotifChannel
case class PushChan(token: String, badge: Int) extends NotifChannel
case class SlackChan(channel: String, emoji: String) extends NotifChannel
case class WebhookChan(url: String, secret: String) extends NotifChannel

sealed trait CacheStrategy
case class NoCache(reason: String) extends CacheStrategy
case class TtlCache(seconds: Int) extends CacheStrategy
case class LruCache(maxEntries: Int) extends CacheStrategy
case class WriteThrough(batchSize: Int) extends CacheStrategy
case class WriteBehind(delayMs: Int) extends CacheStrategy

sealed trait ValidationError
case class RequiredField(field: String) extends ValidationError
case class InvalidFormat(field: String, expected: String) extends ValidationError
case class OutOfRange(field: String, min: Double, max: Double) extends ValidationError
case class TooLong(field: String, maxLen: Int) extends ValidationError
case class CustomError(field: String, message: String) extends ValidationError

object SumTypes:
  def run(): Unit =
    val m: HttpMethod = PostMethod(true); assert(decode[HttpMethod](m.asJson.noSpaces) == Right(m))
    val l: LogLevel = WarnLevel(true); assert(decode[LogLevel](l.asJson.noSpaces) == Right(l))
    val s: Shape = PolygonShape(6, 10.0); assert(decode[Shape](s.asJson.noSpaces) == Right(s))
    val c: CurrencyType = EurCurrency(1599); assert(decode[CurrencyType](c.asJson.noSpaces) == Right(c))
    val p: Permission = DeletePerm("users", true); assert(decode[Permission](p.asJson.noSpaces) == Right(p))
    val n: NotifChannel = SlackChan("#alerts", ":warning:"); assert(decode[NotifChannel](n.asJson.noSpaces) == Right(n))
    val cs: CacheStrategy = LruCache(1000); assert(decode[CacheStrategy](cs.asJson.noSpaces) == Right(cs))
    val v: ValidationError = OutOfRange("age", 0, 200); assert(decode[ValidationError](v.asJson.noSpaces) == Right(v))

// ═══════════════════════════════════════════════════════════════════════
// More flat products
// ═══════════════════════════════════════════════════════════════════════
case class Rgb(r: Int, g: Int, b: Int)
case class Hsl(h: Double, s: Double, l: Double)
case class Rgba(r: Int, g: Int, b: Int, a: Double)
case class Vec2(x: Double, y: Double)
case class Vec3(x: Double, y: Double, z: Double)
case class Vec4(x: Double, y: Double, z: Double, w: Double)
case class Matrix2x2(m00: Double, m01: Double, m10: Double, m11: Double)
case class Rect2(x: Double, y: Double, width: Double, height: Double)
case class Range2(min: Double, max: Double, step: Double)
case class DateRange3(start: String, end: String)
case class TimeSlot(day: String, startHour: Int, endHour: Int)
case class Interval2(from: Long, to: Long, unit: String)
case class Locale4(language: String, country: String, variant: String)
case class Version3(major: Int, minor: Int, patch: Int, preRelease: String)
case class Semver(version: Version3, buildMeta: String)
case class FeatureFlag2(name: String, enabled: Boolean, rolloutPct: Double, description: String)
case class Experiment2(id: String, name: String, variants: List[String], trafficPct: Double)
case class ABTest(experiment: Experiment2, winner: String, confidence: Double, sampleSize: Int)
case class ConnectionPool(minSize: Int, maxSize: Int, idleTimeoutMs: Int, acquireTimeoutMs: Int, validationQuery: String)
case class DatabaseConfig(host: String, port: Int, database: String, username: String, ssl: Boolean, pool: ConnectionPool)
case class RedisConfig(host: String, port: Int, database: Int, maxRetries: Int, timeoutMs: Int)
case class KafkaConfig(brokers: List[String], groupId: String, autoCommit: Boolean, maxPollRecords: Int)
case class S3Config(bucket: String, region: String, prefix: String, encryption: Boolean)
case class SmtpConfig(host: String, port: Int, username: String, tls: Boolean, fromAddress: String, fromName: String)

object FlatProducts:
  def run(): Unit =
    val rgb = Rgb(255, 128, 0); assert(decode[Rgb](rgb.asJson.noSpaces) == Right(rgb))
    val hsl = Hsl(180.0, 0.5, 0.6); assert(decode[Hsl](hsl.asJson.noSpaces) == Right(hsl))
    val rgba = Rgba(0, 0, 0, 0.5); assert(decode[Rgba](rgba.asJson.noSpaces) == Right(rgba))
    val v2 = Vec2(1, 2); assert(decode[Vec2](v2.asJson.noSpaces) == Right(v2))
    val v3 = Vec3(1, 2, 3); assert(decode[Vec3](v3.asJson.noSpaces) == Right(v3))
    val v4 = Vec4(1, 2, 3, 4); assert(decode[Vec4](v4.asJson.noSpaces) == Right(v4))
    val mat = Matrix2x2(1, 0, 0, 1); assert(decode[Matrix2x2](mat.asJson.noSpaces) == Right(mat))
    val rect = Rect2(0, 0, 100, 50); assert(decode[Rect2](rect.asJson.noSpaces) == Right(rect))
    val rng = Range2(0, 100, 5); assert(decode[Range2](rng.asJson.noSpaces) == Right(rng))
    val dr = DateRange3("2024-01", "2024-12"); assert(decode[DateRange3](dr.asJson.noSpaces) == Right(dr))
    val ts = TimeSlot("Mon", 9, 17); assert(decode[TimeSlot](ts.asJson.noSpaces) == Right(ts))
    val iv = Interval2(0, 3600, "s"); assert(decode[Interval2](iv.asJson.noSpaces) == Right(iv))
    val loc = Locale4("en", "US", ""); assert(decode[Locale4](loc.asJson.noSpaces) == Right(loc))
    val ver = Version3(1, 2, 3, "beta"); assert(decode[Version3](ver.asJson.noSpaces) == Right(ver))
    val sv = Semver(ver, "abc123"); assert(decode[Semver](sv.asJson.noSpaces) == Right(sv))
    val feat = FeatureFlag2("dark", true, 50.0, "Dark theme"); assert(decode[FeatureFlag2](feat.asJson.noSpaces) == Right(feat))
    val exp = Experiment2("e1", "btn", List("red", "blue"), 10.0); assert(decode[Experiment2](exp.asJson.noSpaces) == Right(exp))
    val ab = ABTest(exp, "blue", 0.95, 10000); assert(decode[ABTest](ab.asJson.noSpaces) == Right(ab))
    val pool = ConnectionPool(5, 20, 60000, 5000, "SELECT 1")
    val db = DatabaseConfig("localhost", 5432, "mydb", "admin", true, pool); assert(decode[DatabaseConfig](db.asJson.noSpaces) == Right(db))
    val redis = RedisConfig("localhost", 6379, 0, 3, 5000); assert(decode[RedisConfig](redis.asJson.noSpaces) == Right(redis))
    val kafka = KafkaConfig(List("broker1:9092", "broker2:9092"), "my-group", false, 500); assert(decode[KafkaConfig](kafka.asJson.noSpaces) == Right(kafka))
    val s3 = S3Config("my-bucket", "us-east-1", "data/", true); assert(decode[S3Config](s3.asJson.noSpaces) == Right(s3))
    val smtp = SmtpConfig("smtp.example.com", 587, "noreply", true, "noreply@example.com", "My App"); assert(decode[SmtpConfig](smtp.asJson.noSpaces) == Right(smtp))

// ═══════════════════════════════════════════════════════════════════════
// Main
// ═══════════════════════════════════════════════════════════════════════
object Main:
  def main(args: Array[String]): Unit =
    benchmark.domain1.Domain1.run()
    benchmark.domain2.Domain2.run()
    benchmark.domain3.Domain3.run()
    benchmark.domain4.Domain4.run()
    benchmark.domain5.Domain5.run()
    benchmark.domain6.Domain6.run()
    benchmark.domain7.Domain7.run()
    benchmark.domain8.Domain8.run()
    benchmark.domain9.Domain9.run()
    WideTypes.run()
    SumTypes.run()
    FlatProducts.run()
    println("All domains passed!")
