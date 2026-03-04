package configured.access

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import io.circe.derivation.Configuration
import io.circe.generic.semiauto.*

// ═══════════════════════════════════════════════════════════════════════
// Roles / Permissions / Access Control (~25 types)
// withDefaults, withDiscriminator
// ═══════════════════════════════════════════════════════════════════════

private given Configuration = Configuration.default.withDefaults

final case class UserId(value: String)
object UserId:
  given Codec.AsObject[UserId] = deriveConfiguredCodec

final case class GroupId(value: String)
object GroupId:
  given Codec.AsObject[GroupId] = deriveConfiguredCodec

final case class ResourceId(value: String, resourceType: String = "document")
object ResourceId:
  given Codec.AsObject[ResourceId] = deriveConfiguredCodec

final case class Permission(action: String, resource: String, effect: String = "allow")
object Permission:
  given Codec.AsObject[Permission] = deriveConfiguredCodec

final case class Role(id: String, name: String, description: String = "", permissions: List[Permission])
object Role:
  given Codec.AsObject[Role] = deriveConfiguredCodec

final case class RoleAssignment(userId: String, roleId: String, scope: String = "global", assignedAt: String)
object RoleAssignment:
  given Codec.AsObject[RoleAssignment] = deriveConfiguredCodec

final case class Group(id: GroupId, name: String, description: String = "", memberCount: Int = 0)
object Group:
  given Codec.AsObject[Group] = deriveConfiguredCodec

final case class GroupMembership(groupId: String, userId: String, role: String = "member", addedAt: String)
object GroupMembership:
  given Codec.AsObject[GroupMembership] = deriveConfiguredCodec

final case class PolicyCondition(field: String, operator: String, value: String)
object PolicyCondition:
  given Codec.AsObject[PolicyCondition] = deriveConfiguredCodec

final case class AccessPolicy(id: String, name: String, conditions: List[PolicyCondition], effect: String = "allow", priority: Int = 0)
object AccessPolicy:
  given Codec.AsObject[AccessPolicy] = deriveConfiguredCodec

final case class ApiKey(id: String, name: String, prefix: String, scopes: List[String], expiresAt: String = "", active: Boolean = true)
object ApiKey:
  given Codec.AsObject[ApiKey] = deriveConfiguredCodec

final case class OAuthClient(clientId: String, clientName: String, redirectUris: List[String], grantTypes: List[String])
object OAuthClient:
  given Codec.AsObject[OAuthClient] = deriveConfiguredCodec

final case class TokenInfo(tokenId: String, userId: String, issuedAt: String, expiresAt: String, scopes: List[String])
object TokenInfo:
  given Codec.AsObject[TokenInfo] = deriveConfiguredCodec

final case class SessionInfo(sessionId: String, userId: String, ipAddress: String, userAgent: String = "", startedAt: String, lastActiveAt: String)
object SessionInfo:
  given Codec.AsObject[SessionInfo] = deriveConfiguredCodec

final case class AuditLogEntry(id: String, actor: String, action: String, resource: String, timestamp: String, result: String = "success")
object AuditLogEntry:
  given Codec.AsObject[AuditLogEntry] = deriveConfiguredCodec

final case class IpAllowRule(cidr: String, description: String = "", active: Boolean = true)
object IpAllowRule:
  given Codec.AsObject[IpAllowRule] = deriveConfiguredCodec

final case class RateLimitRule(endpoint: String, maxRequests: Int, windowSeconds: Int, burstLimit: Int = 0)
object RateLimitRule:
  given Codec.AsObject[RateLimitRule] = deriveConfiguredCodec

final case class SecurityConfig(mfaRequired: Boolean = false, passwordMinLength: Int = 8, sessionTimeoutMinutes: Int = 30, ipAllowList: List[IpAllowRule])
object SecurityConfig:
  given Codec.AsObject[SecurityConfig] = deriveConfiguredCodec

// ADTs
sealed trait AuthEvent
object AuthEvent:
  private given Configuration = Configuration.default.withDefaults.withDiscriminator("auth_event")
  given Codec.AsObject[AuthEvent] = deriveConfiguredCodec
final case class LoginSuccess(userId: String, method: String, ipAddress: String, mfaUsed: Boolean = false) extends AuthEvent
object LoginSuccess:
  given Codec.AsObject[LoginSuccess] = deriveConfiguredCodec
final case class LoginFailure(userId: String, reason: String, ipAddress: String, attemptNumber: Int = 1) extends AuthEvent
object LoginFailure:
  given Codec.AsObject[LoginFailure] = deriveConfiguredCodec
final case class TokenRefreshed(userId: String, tokenId: String, newExpiresAt: String) extends AuthEvent
object TokenRefreshed:
  given Codec.AsObject[TokenRefreshed] = deriveConfiguredCodec
final case class AccountLocked(userId: String, reason: String = "too_many_attempts", lockedUntil: String = "") extends AuthEvent
object AccountLocked:
  given Codec.AsObject[AccountLocked] = deriveConfiguredCodec
final case class PasswordChanged(userId: String, forced: Boolean = false) extends AuthEvent
object PasswordChanged:
  given Codec.AsObject[PasswordChanged] = deriveConfiguredCodec

sealed trait AccessDecision
object AccessDecision:
  private given Configuration = Configuration.default.withDefaults.withDiscriminator("decision")
  given Codec.AsObject[AccessDecision] = deriveConfiguredCodec
final case class Allowed(resource: String, action: String, matchedPolicy: String) extends AccessDecision
object Allowed:
  given Codec.AsObject[Allowed] = deriveConfiguredCodec
final case class Denied(resource: String, action: String, reason: String = "no matching policy") extends AccessDecision
object Denied:
  given Codec.AsObject[Denied] = deriveConfiguredCodec
final case class RequiresMfa(resource: String, action: String, mfaType: String = "totp") extends AccessDecision
object RequiresMfa:
  given Codec.AsObject[RequiresMfa] = deriveConfiguredCodec
final case class RateLimited(resource: String, retryAfterSeconds: Int) extends AccessDecision
object RateLimited:
  given Codec.AsObject[RateLimited] = deriveConfiguredCodec

object AccessDomain:
  def run(): Unit =
    val role = Role("r1", "Editor", "Can edit content", List(Permission("write", "articles"), Permission("read", "articles")))
    assert(decode[Role](role.asJson.noSpaces) == Right(role))

    val policy = AccessPolicy("p1", "Admin Only", List(PolicyCondition("role", "eq", "admin")))
    assert(decode[AccessPolicy](policy.asJson.noSpaces) == Right(policy))

    val event: AuthEvent = LoginSuccess("u1", "password", "1.2.3.4")
    assert(decode[AuthEvent](event.asJson.noSpaces) == Right(event))

    val decision: AccessDecision = Denied("secret-doc", "read", "insufficient permissions")
    assert(decode[AccessDecision](decision.asJson.noSpaces) == Right(decision))

    val security = SecurityConfig(true, 12, 60, List(IpAllowRule("10.0.0.0/8", "Internal")))
    assert(decode[SecurityConfig](security.asJson.noSpaces) == Right(security))

    val apiKey = ApiKey("k1", "CI Key", "sk_", List("read", "write"), "2025-01-01")
    assert(decode[ApiKey](apiKey.asJson.noSpaces) == Right(apiKey))
