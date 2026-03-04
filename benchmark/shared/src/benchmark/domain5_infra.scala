package benchmark.domain5

import io.circe._
import io.circe.syntax._
import io.circe.parser._
import io.circe.generic.auto._

// API Gateway
case class RateLimit(requestsPerSecond: Int, burstSize: Int, windowMs: Int)
case class Timeout(connectMs: Int, readMs: Int, writeMs: Int, idleMs: Int)
case class RetryPolicy(maxRetries: Int, backoffMs: Int, backoffMultiplier: Double, retryOn: List[Int])
case class CircuitBreaker(failureThreshold: Int, resetTimeoutMs: Int, halfOpenRequests: Int, failureRatePct: Double)
case class HealthCheck(path: String, intervalMs: Int, timeoutMs: Int, healthyThreshold: Int, unhealthyThreshold: Int)

sealed trait AuthScheme
case class ApiKeyAuth(headerName: String, required: Boolean) extends AuthScheme
case class BearerTokenAuth(issuer: String, audience: String, algorithms: List[String]) extends AuthScheme
case class BasicAuth(realm: String) extends AuthScheme
case class OAuth2Auth(provider: String, scopes: List[String], callbackUrl: String, pkce: Boolean) extends AuthScheme
case class MtlsAuth(caBundle: String, requireClientCert: Boolean) extends AuthScheme

case class CorsConfig(allowedOrigins: List[String], allowedMethods: List[String], allowedHeaders: List[String], maxAgeSec: Int, credentials: Boolean)
case class RouteConfig(path: String, methods: List[String], auth: AuthScheme, rateLimit: RateLimit,
  timeout: Timeout, retry: RetryPolicy, circuitBreaker: CircuitBreaker, cors: CorsConfig)

// Deployment
sealed trait DeployStrategy
case class RollingDeploy(maxUnavailable: Int, maxSurge: Int) extends DeployStrategy
case class BlueGreenDeploy(activeColor: String, testEndpoint: String) extends DeployStrategy
case class CanaryDeploy(initialPct: Int, stepPct: Int, intervalSec: Int, maxPct: Int) extends DeployStrategy
case class RecreateDeploy(drainTimeoutSec: Int) extends DeployStrategy

case class ResourceLimits(cpuMillis: Int, memoryMb: Int, diskMb: Int)
case class ContainerPort(name: String, port: Int, protocol: String)
case class EnvVar(name: String, value: String, secret: Boolean)
case class VolumeMount(name: String, mountPath: String, readOnly: Boolean)
case class ContainerSpec(image: String, tag: String, ports: List[ContainerPort], env: List[EnvVar], resources: ResourceLimits, volumes: List[VolumeMount], command: List[String])
case class DeploymentSpec(name: String, replicas: Int, strategy: DeployStrategy, container: ContainerSpec, healthCheck: HealthCheck)

// DNS & Networking
case class DnsRecord(name: String, recordType: String, value: String, ttl: Int, priority: Int)
case class LoadBalancerConfig(algorithm: String, healthCheckPath: String, stickySession: Boolean, drainTimeout: Int)
case class TlsCert(domain: String, issuer: String, expiresAt: String, autoRenew: Boolean)
case class IngressRule(host: String, path: String, serviceName: String, servicePort: Int, tls: TlsCert)

// Service mesh
case class ServiceEndpoint(host: String, port: Int, weight: Int, zone: String)
case class ServiceRegistry(name: String, version: String, endpoints: List[ServiceEndpoint], tags: List[String])
case class TrafficPolicy(connectionPool: Int, outlierDetection: Boolean, loadBalancer: String, retries: Int)

object Domain5:
  def run(): Unit =
    val route = RouteConfig("/api/v1/users", List("GET", "POST"),
      BearerTokenAuth("auth.example.com", "api", List("RS256")),
      RateLimit(100, 200, 60000), Timeout(5000, 30000, 30000, 120000),
      RetryPolicy(3, 1000, 2.0, List(502, 503)),
      CircuitBreaker(5, 60000, 2, 50.0),
      CorsConfig(List("https://app.example.com"), List("GET", "POST"), List("Authorization"), 86400, true))
    assert(decode[RouteConfig](route.asJson.noSpaces) == Right(route))

    val container = ContainerSpec("myapp", "v2.1.0",
      List(ContainerPort("http", 8080, "TCP")),
      List(EnvVar("DB_URL", "postgres://...", false), EnvVar("API_KEY", "***", true)),
      ResourceLimits(1000, 512, 1024),
      List(VolumeMount("data", "/data", false)),
      List("java", "-jar", "app.jar"))
    val deploy = DeploymentSpec("myapp", 3, CanaryDeploy(10, 10, 300, 100), container,
      HealthCheck("/health", 10000, 3000, 3, 2))
    assert(decode[DeploymentSpec](deploy.asJson.noSpaces) == Right(deploy))

    val dns = DnsRecord("api.example.com", "A", "1.2.3.4", 300, 0)
    assert(decode[DnsRecord](dns.asJson.noSpaces) == Right(dns))

    val ingress = IngressRule("api.example.com", "/", "api-svc", 8080,
      TlsCert("*.example.com", "LetsEncrypt", "2025-06-01", true))
    assert(decode[IngressRule](ingress.asJson.noSpaces) == Right(ingress))

    val registry = ServiceRegistry("user-service", "3.0.0",
      List(ServiceEndpoint("10.0.0.1", 8080, 100, "us-east-1a"), ServiceEndpoint("10.0.0.2", 8080, 100, "us-east-1b")),
      List("production", "critical"))
    assert(decode[ServiceRegistry](registry.asJson.noSpaces) == Right(registry))

    val policy = TrafficPolicy(100, true, "round-robin", 3)
    assert(decode[TrafficPolicy](policy.asJson.noSpaces) == Right(policy))
