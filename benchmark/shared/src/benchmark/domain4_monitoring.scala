package benchmark.domain4

import io.circe._
import io.circe.syntax._
import io.circe.parser._
import io.circe.generic.auto._

case class MetricName(value: String)
case class LabelPair(key: String, value: String)
case class Timestamp(epochMs: Long)
case class DataPoint(timestamp: Timestamp, value: Double)
case class Histogram(buckets: List[Double], counts: List[Int], sum: Double, count: Int)
case class Summary(quantiles: List[Double], values: List[Double], sum: Double, count: Int)
case class TimeSeries(name: MetricName, labels: List[LabelPair], points: List[DataPoint])

sealed trait MetricType
case class CounterMetric(total: Double, created: Timestamp) extends MetricType
case class GaugeMetric(value: Double) extends MetricType
case class HistogramMetric(histogram: Histogram) extends MetricType
case class SummaryMetric(summary: Summary) extends MetricType

sealed trait AlertSeverity
case class CriticalSev(escalateAfterMin: Int, pageOnCall: Boolean) extends AlertSeverity
case class WarningSev(autoResolveAfterMin: Int) extends AlertSeverity
case class InfoSev(logOnly: Boolean) extends AlertSeverity

sealed trait AlertCondition
case class ThresholdAbove(metric: MetricName, threshold: Double, durationSec: Int) extends AlertCondition
case class ThresholdBelow(metric: MetricName, threshold: Double, durationSec: Int) extends AlertCondition
case class RateOfChange(metric: MetricName, maxDelta: Double, windowSec: Int) extends AlertCondition
case class Absence(metric: MetricName, timeoutSec: Int) extends AlertCondition
case class AnomalyDetection(metric: MetricName, sensitivity: Double, seasonality: String) extends AlertCondition

sealed trait AlertState
case class AlertFiring(since: Timestamp, value: Double) extends AlertState
case class AlertPending(since: Timestamp, value: Double) extends AlertState
case class AlertResolved(resolvedAt: Timestamp, duration: Long) extends AlertState
case class AlertSilenced(silencedBy: String, until: Timestamp, reason: String) extends AlertState
case class AlertAcked(ackedBy: String, ackedAt: Timestamp, note: String) extends AlertState

case class AlertRule(name: String, condition: AlertCondition, severity: AlertSeverity, notifyChannels: List[String], cooldownMin: Int)
case class AlertInstance(rule: AlertRule, state: AlertState, labels: List[LabelPair])

case class ServiceHealth(name: String, version: String, healthy: Boolean, uptime: Double, latencyP50: Double, latencyP95: Double, latencyP99: Double, errorRate: Double, requestRate: Double)
case class DependencyHealth(name: String, healthy: Boolean, latencyMs: Double, errorRate: Double)
case class HealthReport(service: ServiceHealth, dependencies: List[DependencyHealth], checkedAt: Timestamp)

case class LogEntry(timestamp: Timestamp, level: String, logger: String, message: String, traceId: String, spanId: String)
case class SpanInfo(traceId: String, spanId: String, parentId: String, operation: String, service: String, durationMs: Double, status: String, tags: List[LabelPair])

object Domain4:
  def run(): Unit =
    val ts = Timestamp(1709251200000L)
    val series = TimeSeries(MetricName("cpu"), List(LabelPair("host", "n1")), List(DataPoint(ts, 42.0), DataPoint(Timestamp(ts.epochMs + 60000), 43.5)))
    assert(decode[TimeSeries](series.asJson.noSpaces) == Right(series))

    val mt: MetricType = HistogramMetric(Histogram(List(0.1, 0.5, 1.0), List(10, 25, 50), 75.5, 50))
    assert(decode[MetricType](mt.asJson.noSpaces) == Right(mt))

    val rule = AlertRule("high-cpu", ThresholdAbove(MetricName("cpu"), 90.0, 300), CriticalSev(15, true), List("slack", "pagerduty"), 60)
    val alert = AlertInstance(rule, AlertFiring(ts, 95.0), List(LabelPair("host", "n1")))
    assert(decode[AlertInstance](alert.asJson.noSpaces) == Right(alert))

    val health = ServiceHealth("api", "2.1.0", true, 99.99, 12.5, 45.0, 150.0, 0.01, 1500.0)
    val depHealth = DependencyHealth("postgres", true, 2.5, 0.001)
    val report = HealthReport(health, List(depHealth), ts)
    assert(decode[HealthReport](report.asJson.noSpaces) == Right(report))

    val log = LogEntry(ts, "ERROR", "com.app.Api", "Connection refused", "trace-123", "span-456")
    assert(decode[LogEntry](log.asJson.noSpaces) == Right(log))

    val span = SpanInfo("trace-123", "span-456", "span-000", "GET /api/users", "api-gateway", 45.2, "OK", List(LabelPair("http.method", "GET")))
    assert(decode[SpanInfo](span.asJson.noSpaces) == Right(span))
