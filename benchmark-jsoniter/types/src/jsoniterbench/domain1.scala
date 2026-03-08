package jsoniterbench

// Domain 1: Users & Authentication

case class UserId(value: String)
case class Email(address: String)
case class PhoneNumber(countryCode: String, number: String)
case class UserName(first: String, middle: Option[String], last: String)
case class StreetAddress(line1: String, line2: Option[String], city: String, state: String, zip: String, country: String)
case class GeoCoord(lat: Double, lng: Double)
case class UserBio(summary: String, website: Option[String], company: Option[String])
case class Avatar(url: String, width: Int, height: Int)
case class SocialLinks(twitter: Option[String], github: Option[String], linkedin: Option[String], website: Option[String])
case class UserProfile(name: UserName, email: Email, phone: Option[PhoneNumber], bio: UserBio, avatar: Option[Avatar], social: SocialLinks)
case class NotifPrefs(emailEnabled: Boolean, sms: Boolean, push: Boolean, digest: String)
case class DisplayPrefs(theme: String, language: String, timezone: String, fontSize: Int)
case class PrivacyPrefs(profilePublic: Boolean, showEmail: Boolean, showPhone: Boolean, searchable: Boolean)
case class Preferences(notif: NotifPrefs, display: DisplayPrefs, privacy: PrivacyPrefs)
case class UserAccount(id: UserId, profile: UserProfile, preferences: Preferences, tags: List[String], createdAt: String, updatedAt: String)

sealed trait UserRole
case class AdminRole(level: Int, permissions: List[String]) extends UserRole
case class EditorRole(sections: List[String], canPublish: Boolean) extends UserRole
case class ViewerRole(restricted: Boolean) extends UserRole
case class GuestRole(expiresAt: String) extends UserRole
case class SuperAdminRole(region: String, canDelegate: Boolean) extends UserRole
case class ModeratorRole(forums: List[String], canBan: Boolean) extends UserRole

sealed trait AccountStatus
case class ActiveAccount(since: String) extends AccountStatus
case class SuspendedAccount(reason: String, until: String) extends AccountStatus
case class DeactivatedAccount(deactivatedAt: String) extends AccountStatus
case class PendingVerification(token: String, expiresAt: String) extends AccountStatus
case class LockedAccount(failedAttempts: Int, lockedAt: String) extends AccountStatus

case class Session(id: String, userId: UserId, startedAt: String, lastActiveAt: String, ipAddress: String, userAgent: String)
case class LoginAttempt(userId: UserId, success: Boolean, timestamp: String, ipAddress: String, method: String)
case class AuditEntry(action: String, userId: UserId, targetId: String, timestamp: String, details: Map[String, String])
case class AuthToken(token: String, userId: UserId, scopes: List[String], expiresAt: String, issuedAt: String)
case class ApiKey(id: String, name: String, key: String, userId: UserId, rateLimit: Int, createdAt: String)
case class PasswordPolicy(minLength: Int, requireUpper: Boolean, requireDigit: Boolean, requireSpecial: Boolean, maxAge: Int)
