package configured.crm

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import io.circe.derivation.Configuration
import io.circe.generic.semiauto.*

// ═══════════════════════════════════════════════════════════════════════
// CRM / Customer Management (~30 types)
// withDefaults, withDiscriminator
// ═══════════════════════════════════════════════════════════════════════

private given Configuration = Configuration.default.withDefaults

final case class CustomerId(value: String)
object CustomerId:
  given Codec.AsObject[CustomerId] = deriveConfiguredCodec

final case class ContactEmail(address: String, verified: Boolean = false)
object ContactEmail:
  given Codec.AsObject[ContactEmail] = deriveConfiguredCodec

final case class ContactPhone(countryCode: String, number: String, primary: Boolean = true)
object ContactPhone:
  given Codec.AsObject[ContactPhone] = deriveConfiguredCodec

final case class ContactName(first: String, middle: String = "", last: String)
object ContactName:
  given Codec.AsObject[ContactName] = deriveConfiguredCodec

final case class PostalAddress(line1: String, line2: String = "", city: String, state: String, zip: String, country: String = "US")
object PostalAddress:
  given Codec.AsObject[PostalAddress] = deriveConfiguredCodec

final case class Company(name: String, domain: String = "", industry: String = "")
object Company:
  given Codec.AsObject[Company] = deriveConfiguredCodec

final case class ContactSource(channel: String, campaign: String = "", referrer: String = "")
object ContactSource:
  given Codec.AsObject[ContactSource] = deriveConfiguredCodec

final case class CustomerProfile(name: ContactName, email: ContactEmail, phone: ContactPhone, company: Company)
object CustomerProfile:
  given Codec.AsObject[CustomerProfile] = deriveConfiguredCodec

final case class LeadScore(value: Int, lastUpdated: String, source: String = "auto")
object LeadScore:
  given Codec.AsObject[LeadScore] = deriveConfiguredCodec

final case class DealStage(name: String, probability: Double, daysInStage: Int = 0)
object DealStage:
  given Codec.AsObject[DealStage] = deriveConfiguredCodec

final case class Deal(id: String, title: String, amount: Double, stage: DealStage, ownerId: String)
object Deal:
  given Codec.AsObject[Deal] = deriveConfiguredCodec

final case class ActivityNote(content: String, author: String, createdAt: String, pinned: Boolean = false)
object ActivityNote:
  given Codec.AsObject[ActivityNote] = deriveConfiguredCodec

final case class TaskReminder(taskId: String, dueAt: String, assignee: String, completed: Boolean = false)
object TaskReminder:
  given Codec.AsObject[TaskReminder] = deriveConfiguredCodec

final case class MeetingRecord(id: String, title: String, scheduledAt: String, duration: Int, attendees: List[String])
object MeetingRecord:
  given Codec.AsObject[MeetingRecord] = deriveConfiguredCodec

final case class Pipeline(id: String, name: String, stages: List[DealStage], active: Boolean = true)
object Pipeline:
  given Codec.AsObject[Pipeline] = deriveConfiguredCodec

final case class CustomField(key: String, label: String, value: String, fieldType: String = "text")
object CustomField:
  given Codec.AsObject[CustomField] = deriveConfiguredCodec

final case class Tag(name: String, color: String = "gray")
object Tag:
  given Codec.AsObject[Tag] = deriveConfiguredCodec

final case class CustomerSegment(id: String, name: String, criteria: String, memberCount: Int = 0)
object CustomerSegment:
  given Codec.AsObject[CustomerSegment] = deriveConfiguredCodec

final case class CampaignRef(id: String, name: String, channel: String, active: Boolean = true)
object CampaignRef:
  given Codec.AsObject[CampaignRef] = deriveConfiguredCodec

final case class InteractionLog(id: String, customerId: String, channel: String, summary: String, timestamp: String)
object InteractionLog:
  given Codec.AsObject[InteractionLog] = deriveConfiguredCodec

final case class SupportTicket(id: String, subject: String, priority: String = "medium", status: String = "open")
object SupportTicket:
  given Codec.AsObject[SupportTicket] = deriveConfiguredCodec

final case class CustomerRecord(
  id: CustomerId,
  profile: CustomerProfile,
  address: PostalAddress,
  source: ContactSource,
  score: LeadScore,
  tags: List[Tag],
  createdAt: String,
  updatedAt: String,
)
object CustomerRecord:
  given Codec.AsObject[CustomerRecord] = deriveConfiguredCodec

// ADTs with discriminator
sealed trait CustomerStatus
object CustomerStatus:
  private given Configuration = Configuration.default.withDefaults.withDiscriminator("type")
  given Codec.AsObject[CustomerStatus] = deriveConfiguredCodec
final case class ActiveCustomer(since: String, tier: String = "standard") extends CustomerStatus
object ActiveCustomer:
  given Codec.AsObject[ActiveCustomer] = deriveConfiguredCodec
final case class ChurnedCustomer(reason: String, churnedAt: String) extends CustomerStatus
object ChurnedCustomer:
  given Codec.AsObject[ChurnedCustomer] = deriveConfiguredCodec
final case class ProspectCustomer(leadScore: Int, source: String = "web") extends CustomerStatus
object ProspectCustomer:
  given Codec.AsObject[ProspectCustomer] = deriveConfiguredCodec
final case class TrialCustomer(trialEnd: String, convertedDeal: String = "") extends CustomerStatus
object TrialCustomer:
  given Codec.AsObject[TrialCustomer] = deriveConfiguredCodec

sealed trait ActivityType
object ActivityType:
  private given Configuration = Configuration.default.withDefaults.withDiscriminator("kind")
  given Codec.AsObject[ActivityType] = deriveConfiguredCodec
final case class CallActivity(duration: Int, outcome: String = "completed") extends ActivityType
object CallActivity:
  given Codec.AsObject[CallActivity] = deriveConfiguredCodec
final case class EmailActivity(subject: String, opened: Boolean = false) extends ActivityType
object EmailActivity:
  given Codec.AsObject[EmailActivity] = deriveConfiguredCodec
final case class MeetingActivity(location: String, attendeeCount: Int) extends ActivityType
object MeetingActivity:
  given Codec.AsObject[MeetingActivity] = deriveConfiguredCodec
final case class NoteActivity(content: String, pinned: Boolean = false) extends ActivityType
object NoteActivity:
  given Codec.AsObject[NoteActivity] = deriveConfiguredCodec

object CrmDomain:
  def run(): Unit =
    val name = ContactName("Alice", "M", "Smith")
    val profile = CustomerProfile(name, ContactEmail("a@b.com", true), ContactPhone("+1", "5551234"), Company("Acme"))
    val record = CustomerRecord(
      CustomerId("c1"), profile, PostalAddress("123 Main", "", "NYC", "NY", "10001"),
      ContactSource("web"), LeadScore(85, "2024-01-01"), List(Tag("vip", "gold")), "2024-01-01", "2024-06-01",
    )
    assert(decode[CustomerRecord](record.asJson.noSpaces) == Right(record))

    val status: CustomerStatus = ActiveCustomer("2024-01-01")
    assert(decode[CustomerStatus](status.asJson.noSpaces) == Right(status))

    val activity: ActivityType = CallActivity(300)
    assert(decode[ActivityType](activity.asJson.noSpaces) == Right(activity))

    val deal = Deal("d1", "Big Deal", 50000.0, DealStage("negotiation", 0.6), "rep1")
    assert(decode[Deal](deal.asJson.noSpaces) == Right(deal))

    val ticket = SupportTicket("t1", "Issue with login")
    assert(decode[SupportTicket](ticket.asJson.noSpaces) == Right(ticket))
