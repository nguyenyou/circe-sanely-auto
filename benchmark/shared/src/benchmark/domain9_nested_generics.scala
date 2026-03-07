package benchmark.domain9

import io.circe._
import io.circe.syntax._
import io.circe.parser._
import io.circe.generic.auto._
import benchmark.domain8._

// Outer types with multiple fields using the SAME generic constructor.
// Exercises constructor-level negative cache: within a single macro expansion,
// the first Paginated[X] calls summonIgnoring (~5.5ms), subsequent
// Paginated[Y], Paginated[Z] skip it via constructorNegCache.

case class UserReport(
  emails: Paginated[Email2],
  finances: Paginated[Money2],
  metrics: Paginated[MetricVal],
  statuses: Paginated[StatusMsg],
  configs: Paginated[ConfigEntry],
  flags: Paginated[FeatureFlag]
)

case class AdminDashboard(
  userTag: Tagged[UserId2],
  emailTag: Tagged[Email2],
  skuTag: Tagged[Sku2],
  configTag: Tagged[ConfigEntry],
  flagTag: Tagged[FeatureFlag],
  metricTag: Tagged[MetricVal]
)

case class AuditLog(
  emailVer: Versioned[Email2],
  configVer: Versioned[ConfigEntry],
  flagVer: Versioned[FeatureFlag],
  rateLimitVer: Versioned[RateLimit2],
  moneyVer: Versioned[Money2],
  skuVer: Versioned[Sku2]
)

case class CacheReport(
  geoCached: Cached[GeoCoord2],
  versionCached: Cached[Version2],
  slugCached: Cached[Slug2],
  dimsCached: Cached[Dimensions2],
  moneyCached: Cached[Money2],
  emailCached: Cached[Email2]
)

case class InventoryView(
  boxedEmail: Box[Email2],
  boxedMoney: Box[Money2],
  boxedGeo: Box[GeoCoord2],
  boxedMetric: Box[MetricVal],
  boxedUserId: Box[UserId2],
  boxedSku: Box[Sku2]
)

case class ValidatedReport(
  moneyResult: Validated[Money2],
  statusResult: Validated[StatusMsg],
  dimsResult: Validated[Dimensions2],
  localeResult: Validated[Locale3],
  emailResult: Validated[Email2],
  geoResult: Validated[GeoCoord2]
)

case class MixedReport(
  p1: Paginated[Email2], p2: Paginated[Money2], p3: Paginated[MetricVal],
  t1: Tagged[UserId2], t2: Tagged[Sku2], t3: Tagged[Email2],
  v1: Versioned[ConfigEntry], v2: Versioned[FeatureFlag], v3: Versioned[Money2],
  c1: Cached[GeoCoord2], c2: Cached[Slug2], c3: Cached[Version2]
)

case class AnalyticsView(
  p1: Paginated[Sku2], p2: Paginated[Slug2], p3: Paginated[Locale3],
  p4: Paginated[Version2], p5: Paginated[Dimensions2], p6: Paginated[RateLimit2],
  t1: Tagged[Money2], t2: Tagged[GeoCoord2], t3: Tagged[DateRange2]
)

case class SystemSnapshot(
  b1: Box[StatusMsg], b2: Box[ConfigEntry], b3: Box[FeatureFlag],
  b4: Box[RateLimit2], b5: Box[Locale3], b6: Box[Version2],
  v1: Versioned[StatusMsg], v2: Versioned[MetricVal], v3: Versioned[GeoCoord2]
)

case class ComplianceReport(
  vd1: Validated[Money2], vd2: Validated[Email2], vd3: Validated[UserId2],
  vd4: Validated[Sku2], vd5: Validated[ConfigEntry], vd6: Validated[FeatureFlag],
  c1: Cached[MetricVal], c2: Cached[StatusMsg], c3: Cached[ConfigEntry]
)

case class FullDashboard(
  p1: Paginated[Email2], p2: Paginated[Money2],
  t1: Tagged[UserId2], t2: Tagged[Sku2],
  v1: Versioned[Email2], v2: Versioned[Money2],
  c1: Cached[GeoCoord2], c2: Cached[Slug2],
  b1: Box[MetricVal], b2: Box[StatusMsg],
  vd1: Validated[ConfigEntry], vd2: Validated[FeatureFlag]
)

case class RegionalReport(
  p1: Paginated[GeoCoord2], p2: Paginated[DateRange2], p3: Paginated[Locale3],
  p4: Paginated[Dimensions2],
  t1: Tagged[Locale3], t2: Tagged[GeoCoord2], t3: Tagged[DateRange2],
  t4: Tagged[Dimensions2]
)

object Domain9:
  def run(): Unit =
    val ur = UserReport(
      Paginated(List(Email2("a")), 1, 10, 1, 1),
      Paginated(List(Money2(1, "USD")), 1, 10, 1, 1),
      Paginated(List(MetricVal("x", 1.0, 0L)), 1, 10, 1, 1),
      Paginated(List(StatusMsg(200, "OK", "")), 1, 10, 1, 1),
      Paginated(List(ConfigEntry("k", "v", "d", false)), 1, 10, 1, 1),
      Paginated(List(FeatureFlag("f", true, 50.0)), 1, 10, 1, 1)
    )
    assert(decode[UserReport](ur.asJson.noSpaces) == Right(ur))

    val ad = AdminDashboard(
      Tagged("u", UserId2("u1")), Tagged("e", Email2("a@b")),
      Tagged("s", Sku2("S1")), Tagged("c", ConfigEntry("k","v","d",false)),
      Tagged("f", FeatureFlag("x", true, 50.0)), Tagged("m", MetricVal("cpu", 42.0, 0L))
    )
    assert(decode[AdminDashboard](ad.asJson.noSpaces) == Right(ad))

    val al = AuditLog(
      Versioned(Email2("a@b"), 1, "2024-01", "admin"),
      Versioned(ConfigEntry("k","v","d",false), 2, "2024-02", "ops"),
      Versioned(FeatureFlag("x", true, 50.0), 3, "2024-03", "pm"),
      Versioned(RateLimit2(100, 50, 0L), 4, "2024-04", "system"),
      Versioned(Money2(9.99, "USD"), 5, "2024-05", "billing"),
      Versioned(Sku2("S1"), 6, "2024-06", "warehouse")
    )
    assert(decode[AuditLog](al.asJson.noSpaces) == Right(al))

    val cr = CacheReport(
      Cached(GeoCoord2(37.7, -122.4), 0L, 60000, false),
      Cached(Version2(1, 0, 0), 0L, 3600000, true),
      Cached(Slug2("hello"), 0L, 300000, false),
      Cached(Dimensions2(10, 20, 30), 0L, 600000, false),
      Cached(Money2(9.99, "USD"), 0L, 120000, false),
      Cached(Email2("a@b"), 0L, 60000, true)
    )
    assert(decode[CacheReport](cr.asJson.noSpaces) == Right(cr))

    val iv = InventoryView(
      Box(Email2("a")), Box(Money2(1, "USD")), Box(GeoCoord2(0, 0)),
      Box(MetricVal("x", 1.0, 0L)), Box(UserId2("u1")), Box(Sku2("S1"))
    )
    assert(decode[InventoryView](iv.asJson.noSpaces) == Right(iv))

    val vr = ValidatedReport(
      Validated(Money2(100, "USD"), Nil, List("rounded")),
      Validated(StatusMsg(200, "OK", ""), Nil, Nil),
      Validated(Dimensions2(1, 2, 3), List("too small"), Nil),
      Validated(Locale3("en", "US"), Nil, Nil),
      Validated(Email2("a@b"), Nil, Nil),
      Validated(GeoCoord2(0, 0), Nil, Nil)
    )
    assert(decode[ValidatedReport](vr.asJson.noSpaces) == Right(vr))

    val mr = MixedReport(
      Paginated(List(Email2("a")), 1, 10, 1, 1),
      Paginated(List(Money2(1, "USD")), 1, 10, 1, 1),
      Paginated(List(MetricVal("x", 1.0, 0L)), 1, 10, 1, 1),
      Tagged("u", UserId2("u1")), Tagged("s", Sku2("S1")), Tagged("e", Email2("a@b")),
      Versioned(ConfigEntry("k","v","d",false), 1, "2024-01", "admin"),
      Versioned(FeatureFlag("x", true, 50.0), 2, "2024-02", "pm"),
      Versioned(Money2(9.99, "USD"), 3, "2024-03", "billing"),
      Cached(GeoCoord2(37.7, -122.4), 0L, 60000, false),
      Cached(Slug2("hello"), 0L, 300000, false),
      Cached(Version2(1, 0, 0), 0L, 3600000, true)
    )
    assert(decode[MixedReport](mr.asJson.noSpaces) == Right(mr))

    val av = AnalyticsView(
      Paginated(List(Sku2("S1")), 1, 10, 1, 1),
      Paginated(List(Slug2("hi")), 1, 10, 1, 1),
      Paginated(List(Locale3("en", "US")), 1, 10, 1, 1),
      Paginated(List(Version2(1, 0, 0)), 1, 10, 1, 1),
      Paginated(List(Dimensions2(1, 2, 3)), 1, 10, 1, 1),
      Paginated(List(RateLimit2(100, 50, 0L)), 1, 10, 1, 1),
      Tagged("m", Money2(1, "USD")), Tagged("g", GeoCoord2(0, 0)),
      Tagged("d", DateRange2("2024-01", "2024-12"))
    )
    assert(decode[AnalyticsView](av.asJson.noSpaces) == Right(av))

    val ss = SystemSnapshot(
      Box(StatusMsg(200, "OK", "")), Box(ConfigEntry("k","v","d",false)),
      Box(FeatureFlag("x", true, 50.0)), Box(RateLimit2(100, 50, 0L)),
      Box(Locale3("en", "US")), Box(Version2(1, 0, 0)),
      Versioned(StatusMsg(200, "OK", ""), 1, "2024-01", "admin"),
      Versioned(MetricVal("cpu", 42.0, 0L), 2, "2024-02", "mon"),
      Versioned(GeoCoord2(37.7, -122.4), 3, "2024-03", "geo")
    )
    assert(decode[SystemSnapshot](ss.asJson.noSpaces) == Right(ss))

    val cpr = ComplianceReport(
      Validated(Money2(100, "USD"), Nil, Nil),
      Validated(Email2("a@b"), Nil, Nil),
      Validated(UserId2("u1"), Nil, Nil),
      Validated(Sku2("S1"), Nil, Nil),
      Validated(ConfigEntry("k","v","d",false), Nil, Nil),
      Validated(FeatureFlag("x", true, 50.0), Nil, Nil),
      Cached(MetricVal("cpu", 42.0, 0L), 0L, 60000, false),
      Cached(StatusMsg(200, "OK", ""), 0L, 60000, false),
      Cached(ConfigEntry("k","v","d",false), 0L, 60000, false)
    )
    assert(decode[ComplianceReport](cpr.asJson.noSpaces) == Right(cpr))

    val fd = FullDashboard(
      Paginated(List(Email2("a")), 1, 10, 1, 1),
      Paginated(List(Money2(1, "USD")), 1, 10, 1, 1),
      Tagged("u", UserId2("u1")), Tagged("s", Sku2("S1")),
      Versioned(Email2("a@b"), 1, "2024-01", "admin"),
      Versioned(Money2(9.99, "USD"), 2, "2024-02", "billing"),
      Cached(GeoCoord2(37.7, -122.4), 0L, 60000, false),
      Cached(Slug2("hello"), 0L, 300000, false),
      Box(MetricVal("cpu", 42.0, 0L)), Box(StatusMsg(200, "OK", "")),
      Validated(ConfigEntry("k","v","d",false), Nil, Nil),
      Validated(FeatureFlag("x", true, 50.0), Nil, Nil)
    )
    assert(decode[FullDashboard](fd.asJson.noSpaces) == Right(fd))

    val rr = RegionalReport(
      Paginated(List(GeoCoord2(0, 0)), 1, 10, 1, 1),
      Paginated(List(DateRange2("2024-01", "2024-12")), 1, 10, 1, 1),
      Paginated(List(Locale3("en", "US")), 1, 10, 1, 1),
      Paginated(List(Dimensions2(1, 2, 3)), 1, 10, 1, 1),
      Tagged("l", Locale3("en", "US")), Tagged("g", GeoCoord2(0, 0)),
      Tagged("d", DateRange2("2024-01", "2024-12")),
      Tagged("dim", Dimensions2(1, 2, 3))
    )
    assert(decode[RegionalReport](rr.asJson.noSpaces) == Right(rr))
