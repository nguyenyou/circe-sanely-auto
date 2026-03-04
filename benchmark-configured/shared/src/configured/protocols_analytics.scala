package configured.analytics

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import io.circe.derivation.Configuration
import io.circe.generic.semiauto.*

// ═══════════════════════════════════════════════════════════════════════
// Analytics / Metrics / Reports (~20 types)
// withDefaults, withDiscriminator, nested structures
// ═══════════════════════════════════════════════════════════════════════

private given Configuration = Configuration.default.withDefaults

final case class MetricId(value: String)
object MetricId:
  given Codec.AsObject[MetricId] = deriveConfiguredCodec

final case class TimeBucket(start: String, end: String, granularity: String = "hour")
object TimeBucket:
  given Codec.AsObject[TimeBucket] = deriveConfiguredCodec

final case class MetricValue(name: String, value: Double, unit: String = "", tags: Map[String, String] = Map.empty)
object MetricValue:
  given Codec.AsObject[MetricValue] = deriveConfiguredCodec

final case class MetricSeries(metricId: MetricId, buckets: List[TimeBucket], values: List[Double], aggregation: String = "sum")
object MetricSeries:
  given Codec.AsObject[MetricSeries] = deriveConfiguredCodec

final case class DimensionFilter(dimension: String, operator: String = "eq", value: String)
object DimensionFilter:
  given Codec.AsObject[DimensionFilter] = deriveConfiguredCodec

final case class ReportQuery(metrics: List[String], dimensions: List[String] = Nil, filters: List[DimensionFilter] = Nil, limit: Int = 100)
object ReportQuery:
  given Codec.AsObject[ReportQuery] = deriveConfiguredCodec

final case class ReportRow(dimensions: Map[String, String], values: Map[String, Double])
object ReportRow:
  given Codec.AsObject[ReportRow] = deriveConfiguredCodec

final case class Report(id: String, name: String, query: ReportQuery, rows: List[ReportRow], generatedAt: String, cached: Boolean = false)
object Report:
  given Codec.AsObject[Report] = deriveConfiguredCodec

final case class Dashboard(id: String, name: String, widgets: List[String], owner: String, shared: Boolean = false)
object Dashboard:
  given Codec.AsObject[Dashboard] = deriveConfiguredCodec

final case class AlertThreshold(metric: String, operator: String, value: Double, windowMinutes: Int = 5)
object AlertThreshold:
  given Codec.AsObject[AlertThreshold] = deriveConfiguredCodec

final case class AlertRule(id: String, name: String, threshold: AlertThreshold, channels: List[String], enabled: Boolean = true, cooldownMinutes: Int = 15)
object AlertRule:
  given Codec.AsObject[AlertRule] = deriveConfiguredCodec

final case class AlertIncident(id: String, ruleId: String, triggeredAt: String, resolvedAt: String = "", currentValue: Double)
object AlertIncident:
  given Codec.AsObject[AlertIncident] = deriveConfiguredCodec

final case class FunnelStep(name: String, count: Int, conversionRate: Double = 0.0)
object FunnelStep:
  given Codec.AsObject[FunnelStep] = deriveConfiguredCodec

final case class FunnelReport(id: String, name: String, steps: List[FunnelStep], period: TimeBucket)
object FunnelReport:
  given Codec.AsObject[FunnelReport] = deriveConfiguredCodec

final case class CohortGroup(label: String, size: Int, retentionRates: List[Double])
object CohortGroup:
  given Codec.AsObject[CohortGroup] = deriveConfiguredCodec

final case class CohortAnalysis(id: String, name: String, cohorts: List[CohortGroup], metric: String = "retention")
object CohortAnalysis:
  given Codec.AsObject[CohortAnalysis] = deriveConfiguredCodec

final case class ExperimentResult(experimentId: String, variantName: String, sampleSize: Int, conversionRate: Double, confidence: Double = 0.0)
object ExperimentResult:
  given Codec.AsObject[ExperimentResult] = deriveConfiguredCodec

// ADTs
sealed trait WidgetType
object WidgetType:
  private given Configuration = Configuration.default.withDefaults.withDiscriminator("widget")
  given Codec.AsObject[WidgetType] = deriveConfiguredCodec
final case class LineChart(metrics: List[String], timeRange: String = "7d", stacked: Boolean = false) extends WidgetType
object LineChart:
  given Codec.AsObject[LineChart] = deriveConfiguredCodec
final case class BarChart(metric: String, dimension: String, limit: Int = 10) extends WidgetType
object BarChart:
  given Codec.AsObject[BarChart] = deriveConfiguredCodec
final case class PieChart(metric: String, dimension: String, showLegend: Boolean = true) extends WidgetType
object PieChart:
  given Codec.AsObject[PieChart] = deriveConfiguredCodec
final case class ScalarWidget(metric: String, aggregation: String = "sum", comparisonPeriod: String = "") extends WidgetType
object ScalarWidget:
  given Codec.AsObject[ScalarWidget] = deriveConfiguredCodec
final case class TableWidget(query: ReportQuery, sortBy: String = "", sortDir: String = "desc") extends WidgetType
object TableWidget:
  given Codec.AsObject[TableWidget] = deriveConfiguredCodec

sealed trait DataSource
object DataSource:
  private given Configuration = Configuration.default.withDefaults.withDiscriminator("source_type")
  given Codec.AsObject[DataSource] = deriveConfiguredCodec
final case class DatabaseSource(connectionString: String, query: String, refreshMinutes: Int = 60) extends DataSource
object DatabaseSource:
  given Codec.AsObject[DatabaseSource] = deriveConfiguredCodec
final case class ApiSource(endpoint: String, method: String = "GET", headers: Map[String, String] = Map.empty) extends DataSource
object ApiSource:
  given Codec.AsObject[ApiSource] = deriveConfiguredCodec
final case class FileSource(path: String, format: String = "csv", delimiter: String = ",") extends DataSource
object FileSource:
  given Codec.AsObject[FileSource] = deriveConfiguredCodec

object AnalyticsDomain:
  def run(): Unit =
    val query = ReportQuery(List("pageviews", "sessions"), List("country"), List(DimensionFilter("country", "eq", "US")))
    val row = ReportRow(Map("country" -> "US"), Map("pageviews" -> 10000.0, "sessions" -> 5000.0))
    val report = Report("r1", "Traffic Report", query, List(row), "2024-01-15T12:00:00Z")
    assert(decode[Report](report.asJson.noSpaces) == Right(report))

    val widget: WidgetType = LineChart(List("revenue", "orders"), "30d", true)
    assert(decode[WidgetType](widget.asJson.noSpaces) == Right(widget))

    val source: DataSource = DatabaseSource("jdbc:postgresql://localhost/analytics", "SELECT * FROM events")
    assert(decode[DataSource](source.asJson.noSpaces) == Right(source))

    val funnel = FunnelReport("f1", "Signup Funnel",
      List(FunnelStep("Visit", 10000, 1.0), FunnelStep("Signup", 3000, 0.3), FunnelStep("Activate", 1500, 0.5)),
      TimeBucket("2024-01-01", "2024-01-31", "day"),
    )
    assert(decode[FunnelReport](funnel.asJson.noSpaces) == Right(funnel))

    val alert = AlertRule("a1", "High Error Rate", AlertThreshold("error_rate", "gt", 0.05), List("slack", "email"))
    assert(decode[AlertRule](alert.asJson.noSpaces) == Right(alert))
