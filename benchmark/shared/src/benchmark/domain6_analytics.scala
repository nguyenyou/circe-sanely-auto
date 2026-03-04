package benchmark.domain6

import io.circe._
import io.circe.syntax._
import io.circe.parser._
import io.circe.generic.auto._

case class SessionId(value: String)
case class EventId(value: String)
case class DeviceInfo(deviceType: String, os: String, osVersion: String, browser: String, browserVersion: String, screenWidth: Int, screenHeight: Int, touchEnabled: Boolean)
case class GeoInfo(country: String, region: String, city: String, postalCode: String, lat: Double, lng: Double, isp: String)
case class UTMParams(source: String, medium: String, campaign: String, term: String, content: String)
case class ReferrerInfo(url: String, domain: String, medium: String, campaign: String)
case class UserSegment(id: String, name: String, rules: List[String])

sealed trait AnalyticsEvent
case class PageView(url: String, referrer: String, title: String, durationMs: Int) extends AnalyticsEvent
case class ButtonClick(elementId: String, label: String, page: String, x: Int, y: Int) extends AnalyticsEvent
case class FormSubmit(formId: String, fieldCount: Int, success: Boolean, errorFields: List[String]) extends AnalyticsEvent
case class SearchQuery(query: String, resultCount: Int, clickedResult: Boolean, position: Int) extends AnalyticsEvent
case class PurchaseEvent(orderId: String, amount: Double, itemCount: Int, currency: String) extends AnalyticsEvent
case class SignupEvent(method: String, referralCode: String, step: Int) extends AnalyticsEvent
case class ErrorEvent(errorType: String, message: String, stackTrace: String, page: String) extends AnalyticsEvent
case class VideoEvent(videoId: String, action: String, positionSec: Double, durationSec: Double) extends AnalyticsEvent

case class EventEnvelope(id: EventId, sessionId: SessionId, userId: String, timestamp: Long,
  device: DeviceInfo, geo: GeoInfo, utm: UTMParams, referrer: ReferrerInfo, event: AnalyticsEvent)

// Aggregation types
case class MetricAgg(name: String, value: Double, count: Int, min: Double, max: Double, avg: Double)
case class FunnelStep(name: String, count: Int, conversionRate: Double, dropoffRate: Double)
case class CohortBucket(period: String, size: Int, retained: List[Double])
case class SegmentBreakdown(segment: UserSegment, metrics: List[MetricAgg])
case class DailyStats(date: String, pageViews: Int, uniqueVisitors: Int, sessions: Int, bounceRate: Double, avgSessionDuration: Double)
case class PagePerformance(url: String, views: Int, avgDuration: Double, bounceRate: Double, exitRate: Double)
case class ConversionFunnel(name: String, steps: List[FunnelStep], overallRate: Double)
case class RetentionCohort(startDate: String, buckets: List[CohortBucket])

sealed trait ReportFormat
case class TableReport(columns: List[String], sortBy: String, limit: Int) extends ReportFormat
case class ChartReport(chartType: String, xAxis: String, yAxis: String, groupBy: String) extends ReportFormat
case class SummaryReport(metrics: List[String], comparison: String) extends ReportFormat

case class ReportConfig(name: String, dateRange: String, format: ReportFormat, segments: List[UserSegment], filters: List[String])

object Domain6:
  def run(): Unit =
    val device = DeviceInfo("desktop", "macOS", "14.0", "Chrome", "120.0", 1920, 1080, false)
    val geo = GeoInfo("US", "CA", "SF", "94102", 37.78, -122.41, "Comcast")
    val utm = UTMParams("google", "cpc", "q4_sale", "widgets", "banner_v2")
    val ref = ReferrerInfo("https://google.com/search?q=widgets", "google.com", "organic", "")
    val env = EventEnvelope(EventId("e1"), SessionId("s1"), "u1", 1709251200000L,
      device, geo, utm, ref, PageView("https://shop.com", "https://google.com", "Home", 5000))
    assert(decode[EventEnvelope](env.asJson.noSpaces) == Right(env))

    val env2 = env.copy(event = ErrorEvent("TypeError", "null is not an object", "at line 42", "/checkout"))
    assert(decode[EventEnvelope](env2.asJson.noSpaces) == Right(env2))

    val daily = DailyStats("2024-03-01", 15000, 8000, 10000, 35.5, 180.0)
    assert(decode[DailyStats](daily.asJson.noSpaces) == Right(daily))

    val funnel = ConversionFunnel("Purchase", List(
      FunnelStep("View Product", 1000, 100.0, 0.0),
      FunnelStep("Add to Cart", 400, 40.0, 60.0),
      FunnelStep("Checkout", 200, 50.0, 50.0),
      FunnelStep("Purchase", 150, 75.0, 25.0)), 15.0)
    assert(decode[ConversionFunnel](funnel.asJson.noSpaces) == Right(funnel))

    val segment = UserSegment("s1", "Power Users", List("sessions > 10", "purchases > 3"))
    val breakdown = SegmentBreakdown(segment, List(MetricAgg("revenue", 50000, 100, 50, 2000, 500)))
    assert(decode[SegmentBreakdown](breakdown.asJson.noSpaces) == Right(breakdown))

    val report = ReportConfig("Weekly KPIs", "2024-W10",
      ChartReport("line", "date", "pageViews", "segment"),
      List(segment), List("country:US"))
    assert(decode[ReportConfig](report.asJson.noSpaces) == Right(report))
