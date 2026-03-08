package jsoniterbench

// Domain 4: Infrastructure & Monitoring

case class MetricId(value: String)
case class MetricValue(name: String, value: Double, timestamp: Long, tags: Map[String, String])
case class TimeSeriesPoint(timestamp: Long, value: Double)
case class TimeSeries(metricId: MetricId, points: List[TimeSeriesPoint], aggregation: String, interval: String)
case class AlertThreshold(warning: Double, critical: Double, direction: String)
case class AlertRule(id: String, name: String, metricId: MetricId, threshold: AlertThreshold, cooldown: Int, enabled: Boolean)
case class AlertEvent(id: String, ruleId: String, severity: String, message: String, triggeredAt: String, resolvedAt: Option[String], acknowledged: Boolean)

sealed trait Severity
case class CriticalSev(escalate: Boolean) extends Severity
case class HighSev(shouldNotify: Boolean) extends Severity
case class MediumSev(autoResolve: Boolean) extends Severity
case class LowSev(suppress: Boolean) extends Severity
case class InfoSev(logOnly: Boolean) extends Severity

sealed trait HealthStatus
case class HealthyStatus(checkedAt: String) extends HealthStatus
case class DegradedStatus(reason: String, since: String) extends HealthStatus
case class DownStatus(reason: String, since: String, lastHealthy: String) extends HealthStatus
case class UnknownHealthStatus(lastCheck: Option[String]) extends HealthStatus
case class MaintenanceStatus(scheduledUntil: String, reason: String) extends HealthStatus

case class HealthCheck(name: String, url: String, interval: Int, timeout: Int, status: String, lastCheck: String)
case class ServiceInfo(name: String, version: String, environment: String, host: String, port: Int, uptime: Long)
case class ServiceEndpoint(path: String, method: String, latencyP50: Double, latencyP99: Double, errorRate: Double, requestsPerSec: Double)
case class ServerConfig(hostname: String, cores: Int, memoryGb: Int, diskGb: Int, os: String, region: String)
case class ContainerInfo(id: String, image: String, tag: String, status: String, cpuLimit: Double, memoryLimit: Int, restarts: Int)
case class DeploymentInfo(id: String, service: String, version: String, replicas: Int, strategy: String, startedAt: String, completedAt: Option[String])
case class LogEntry(timestamp: String, level: String, logger: String, message: String, threadName: String, context: Map[String, String])
case class TraceSpan(traceId: String, spanId: String, parentId: Option[String], operationName: String, service: String, startTime: Long, duration: Long, spanTags: Map[String, String])
case class ErrorReport(id: String, error: String, stackTrace: String, service: String, version: String, environment: String, count: Int, firstSeen: String, lastSeen: String)
case class PerformanceMetric(endpoint: String, method: String, p50: Double, p75: Double, p90: Double, p95: Double, p99: Double, max: Double, count: Long)
case class CpuMetrics(user: Double, system: Double, idle: Double, iowait: Double, steal: Double)
case class MemoryMetrics(total: Long, used: Long, free: Long, cached: Long, buffers: Long, swapTotal: Long, swapUsed: Long)
case class DiskMetrics(device: String, readOps: Long, writeOps: Long, readBytes: Long, writeBytes: Long, utilization: Double)
case class NetworkMetrics(iface: String, rxBytes: Long, txBytes: Long, rxPackets: Long, txPackets: Long, rxErrors: Long, txErrors: Long)
