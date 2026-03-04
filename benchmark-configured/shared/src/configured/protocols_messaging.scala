package configured.messaging

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import io.circe.derivation.Configuration
import io.circe.generic.semiauto.*

// ═══════════════════════════════════════════════════════════════════════
// Messaging / Notifications (~25 types)
// withSnakeCaseMemberNames, withDefaults, withDiscriminator
// ═══════════════════════════════════════════════════════════════════════

private given Configuration = Configuration.default.withSnakeCaseMemberNames.withDefaults

final case class MessageId(value: String)
object MessageId:
  given Codec.AsObject[MessageId] = deriveConfiguredCodec

final case class Sender(userId: String, displayName: String, avatarUrl: String = "")
object Sender:
  given Codec.AsObject[Sender] = deriveConfiguredCodec

final case class Recipient(userId: String, displayName: String)
object Recipient:
  given Codec.AsObject[Recipient] = deriveConfiguredCodec

final case class MessageBody(contentType: String = "text/plain", content: String, truncated: Boolean = false)
object MessageBody:
  given Codec.AsObject[MessageBody] = deriveConfiguredCodec

final case class Attachment(fileName: String, fileSize: Long, mimeType: String, downloadUrl: String = "")
object Attachment:
  given Codec.AsObject[Attachment] = deriveConfiguredCodec

final case class Reaction(emoji: String, userId: String, createdAt: String)
object Reaction:
  given Codec.AsObject[Reaction] = deriveConfiguredCodec

final case class ThreadRef(parentMessageId: String, replyCount: Int = 0, lastReplyAt: String = "")
object ThreadRef:
  given Codec.AsObject[ThreadRef] = deriveConfiguredCodec

final case class ChatMessage(
  messageId: MessageId,
  sender: Sender,
  recipients: List[Recipient],
  body: MessageBody,
  attachments: List[Attachment],
  threadRef: Option[ThreadRef] = None,
  sentAt: String,
  editedAt: String = "",
)
object ChatMessage:
  given Codec.AsObject[ChatMessage] = deriveConfiguredCodec

final case class Channel(channelId: String, channelName: String, description: String = "", memberCount: Int = 0, isPrivate: Boolean = false)
object Channel:
  given Codec.AsObject[Channel] = deriveConfiguredCodec

final case class ChannelMember(userId: String, channelId: String, role: String = "member", joinedAt: String)
object ChannelMember:
  given Codec.AsObject[ChannelMember] = deriveConfiguredCodec

final case class NotificationPref(channelType: String, enabled: Boolean = true, quietHoursStart: Int = 22, quietHoursEnd: Int = 8)
object NotificationPref:
  given Codec.AsObject[NotificationPref] = deriveConfiguredCodec

final case class EmailTemplate(templateId: String, templateName: String, subjectLine: String, bodyHtml: String = "", bodyText: String = "")
object EmailTemplate:
  given Codec.AsObject[EmailTemplate] = deriveConfiguredCodec

final case class PushConfig(deviceToken: String, platform: String, badgeCount: Int = 0, soundEnabled: Boolean = true)
object PushConfig:
  given Codec.AsObject[PushConfig] = deriveConfiguredCodec

final case class WebhookEndpoint(endpointUrl: String, secretKey: String = "", retryCount: Int = 3, timeoutMs: Int = 5000)
object WebhookEndpoint:
  given Codec.AsObject[WebhookEndpoint] = deriveConfiguredCodec

final case class MessageSearch(queryText: String, channelId: String = "", senderId: String = "", beforeDate: String = "", afterDate: String = "")
object MessageSearch:
  given Codec.AsObject[MessageSearch] = deriveConfiguredCodec

final case class ReadReceipt(messageId: String, userId: String, readAt: String)
object ReadReceipt:
  given Codec.AsObject[ReadReceipt] = deriveConfiguredCodec

final case class TypingIndicator(channelId: String, userId: String, isTyping: Boolean = true)
object TypingIndicator:
  given Codec.AsObject[TypingIndicator] = deriveConfiguredCodec

final case class MuteConfig(channelId: String, userId: String, mutedUntil: String = "", permanent: Boolean = false)
object MuteConfig:
  given Codec.AsObject[MuteConfig] = deriveConfiguredCodec

// ADTs
sealed trait NotificationEvent
object NotificationEvent:
  private given Configuration = Configuration.default.withSnakeCaseMemberNames.withDefaults.withDiscriminator("event_type")
  given Codec.AsObject[NotificationEvent] = deriveConfiguredCodec
final case class NewMessageNotif(messageId: String, channelId: String, previewText: String = "") extends NotificationEvent
object NewMessageNotif:
  given Codec.AsObject[NewMessageNotif] = deriveConfiguredCodec
final case class MentionNotif(messageId: String, mentionedBy: String) extends NotificationEvent
object MentionNotif:
  given Codec.AsObject[MentionNotif] = deriveConfiguredCodec
final case class ReactionNotif(messageId: String, emoji: String, reactedBy: String) extends NotificationEvent
object ReactionNotif:
  given Codec.AsObject[ReactionNotif] = deriveConfiguredCodec
final case class ChannelInviteNotif(channelId: String, invitedBy: String, channelName: String = "") extends NotificationEvent
object ChannelInviteNotif:
  given Codec.AsObject[ChannelInviteNotif] = deriveConfiguredCodec

sealed trait DeliveryStatus
object DeliveryStatus:
  private given Configuration = Configuration.default.withSnakeCaseMemberNames.withDefaults.withDiscriminator("delivery_status")
  given Codec.AsObject[DeliveryStatus] = deriveConfiguredCodec
final case class Queued(queuedAt: String, priority: Int = 0) extends DeliveryStatus
object Queued:
  given Codec.AsObject[Queued] = deriveConfiguredCodec
final case class Delivered(deliveredAt: String, channel: String) extends DeliveryStatus
object Delivered:
  given Codec.AsObject[Delivered] = deriveConfiguredCodec
final case class Bounced(bouncedAt: String, errorCode: String, retryable: Boolean = true) extends DeliveryStatus
object Bounced:
  given Codec.AsObject[Bounced] = deriveConfiguredCodec
final case class Suppressed(reason: String, suppressedAt: String) extends DeliveryStatus
object Suppressed:
  given Codec.AsObject[Suppressed] = deriveConfiguredCodec

object MessagingDomain:
  def run(): Unit =
    val msg = ChatMessage(
      MessageId("m1"), Sender("u1", "Alice"), List(Recipient("u2", "Bob")),
      MessageBody(content = "Hello!"), List(Attachment("doc.pdf", 1024, "application/pdf")),
      None, "2024-01-15T10:30:00Z",
    )
    assert(decode[ChatMessage](msg.asJson.noSpaces) == Right(msg))

    val notif: NotificationEvent = MentionNotif("m1", "u1")
    assert(decode[NotificationEvent](notif.asJson.noSpaces) == Right(notif))

    val status: DeliveryStatus = Delivered("2024-01-15T10:30:01Z", "push")
    assert(decode[DeliveryStatus](status.asJson.noSpaces) == Right(status))

    val channel = Channel("ch1", "general", "General chat")
    assert(decode[Channel](channel.asJson.noSpaces) == Right(channel))

    val webhook = WebhookEndpoint("https://example.com/hook")
    assert(decode[WebhookEndpoint](webhook.asJson.noSpaces) == Right(webhook))
