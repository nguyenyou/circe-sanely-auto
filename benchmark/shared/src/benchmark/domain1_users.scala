package benchmark.domain1

import io.circe._
import io.circe.syntax._
import io.circe.parser._
import io.circe.generic.auto._

case class UserId(value: String)
case class Email(address: String)
case class PhoneNumber(countryCode: String, number: String)
case class UserName(first: String, middle: String, last: String)
case class StreetAddress(line1: String, line2: String, city: String, state: String, zip: String, country: String)
case class GeoCoord(lat: Double, lng: Double)
case class UserBio(summary: String, website: String, company: String)
case class UserProfile(name: UserName, email: Email, phone: PhoneNumber, bio: UserBio)
case class NotifPrefs(email: Boolean, sms: Boolean, push: Boolean, digest: String)
case class DisplayPrefs(theme: String, language: String, timezone: String, fontSize: Int)
case class PrivacyPrefs(profilePublic: Boolean, showEmail: Boolean, showPhone: Boolean)
case class Preferences(notif: NotifPrefs, display: DisplayPrefs, privacy: PrivacyPrefs)
case class UserAccount(id: UserId, profile: UserProfile, preferences: Preferences, createdAt: String, updatedAt: String)

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
case class AuditEntry(action: String, userId: UserId, targetId: String, timestamp: String, details: String)

object Domain1:
  def run(): Unit =
    val name = UserName("Alice", "M", "Smith")
    val profile = UserProfile(name, Email("a@b.com"), PhoneNumber("+1", "5551234"), UserBio("dev", "x.com", "Acme"))
    val prefs = Preferences(NotifPrefs(true, false, true, "weekly"), DisplayPrefs("dark", "en", "UTC", 14), PrivacyPrefs(true, false, false))
    val account = UserAccount(UserId("u1"), profile, prefs, "2024-01-01", "2024-06-01")
    assert(decode[UserAccount](account.asJson.noSpaces) == Right(account))

    val role: UserRole = ModeratorRole(List("general", "help"), true)
    assert(decode[UserRole](role.asJson.noSpaces) == Right(role))

    val status: AccountStatus = SuspendedAccount("spam", "2024-12-01")
    assert(decode[AccountStatus](status.asJson.noSpaces) == Right(status))

    val session = Session("s1", UserId("u1"), "2024-03-01T10:00:00Z", "2024-03-01T11:30:00Z", "1.2.3.4", "Chrome/120")
    assert(decode[Session](session.asJson.noSpaces) == Right(session))

    val login = LoginAttempt(UserId("u1"), true, "2024-03-01T10:00:00Z", "1.2.3.4", "password")
    assert(decode[LoginAttempt](login.asJson.noSpaces) == Right(login))

    val audit = AuditEntry("login", UserId("u1"), "u1", "2024-03-01T10:00:00Z", "successful login")
    assert(decode[AuditEntry](audit.asJson.noSpaces) == Right(audit))
