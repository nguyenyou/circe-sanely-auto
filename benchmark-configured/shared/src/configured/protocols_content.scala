package configured.content

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import io.circe.derivation.Configuration
import io.circe.generic.semiauto.*

// ═══════════════════════════════════════════════════════════════════════
// CMS / Articles / Media (~25 types)
// withDefaults, withDiscriminator
// ═══════════════════════════════════════════════════════════════════════

private given Configuration = Configuration.default.withDefaults

final case class ArticleId(value: String)
object ArticleId:
  given Codec.AsObject[ArticleId] = deriveConfiguredCodec

final case class Author(id: String, name: String, bio: String = "", avatarUrl: String = "")
object Author:
  given Codec.AsObject[Author] = deriveConfiguredCodec

final case class CategoryLabel(slug: String, name: String, parentSlug: String = "")
object CategoryLabel:
  given Codec.AsObject[CategoryLabel] = deriveConfiguredCodec

final case class TagLabel(slug: String, name: String)
object TagLabel:
  given Codec.AsObject[TagLabel] = deriveConfiguredCodec

final case class SeoMeta(title: String, description: String = "", keywords: List[String] = Nil, canonicalUrl: String = "")
object SeoMeta:
  given Codec.AsObject[SeoMeta] = deriveConfiguredCodec

final case class FeaturedImage(url: String, alt: String = "", width: Int = 0, height: Int = 0, caption: String = "")
object FeaturedImage:
  given Codec.AsObject[FeaturedImage] = deriveConfiguredCodec

final case class ArticleBody(format: String = "markdown", content: String, wordCount: Int = 0)
object ArticleBody:
  given Codec.AsObject[ArticleBody] = deriveConfiguredCodec

final case class Article(
  id: ArticleId,
  title: String,
  slug: String,
  author: Author,
  body: ArticleBody,
  category: CategoryLabel,
  tags: List[TagLabel],
  seo: SeoMeta,
  featuredImage: Option[FeaturedImage] = None,
  publishedAt: String = "",
  updatedAt: String = "",
)
object Article:
  given Codec.AsObject[Article] = deriveConfiguredCodec

final case class Comment(id: String, articleId: String, authorName: String, body: String, approved: Boolean = false, createdAt: String)
object Comment:
  given Codec.AsObject[Comment] = deriveConfiguredCodec

final case class CommentThread(parentId: String, replies: List[Comment], locked: Boolean = false)
object CommentThread:
  given Codec.AsObject[CommentThread] = deriveConfiguredCodec

final case class MediaAsset(id: String, filename: String, mimeType: String, sizeBytes: Long, url: String, uploadedAt: String)
object MediaAsset:
  given Codec.AsObject[MediaAsset] = deriveConfiguredCodec

final case class MediaFolder(id: String, name: String, parentId: String = "", assetCount: Int = 0)
object MediaFolder:
  given Codec.AsObject[MediaFolder] = deriveConfiguredCodec

final case class PageLayout(id: String, name: String, template: String, regions: List[String])
object PageLayout:
  given Codec.AsObject[PageLayout] = deriveConfiguredCodec

final case class NavigationItem(label: String, url: String, children: List[NavigationItem] = Nil, active: Boolean = true)
object NavigationItem:
  given Codec.AsObject[NavigationItem] = deriveConfiguredCodec

final case class NavigationMenu(id: String, name: String, items: List[NavigationItem], position: String = "header")
object NavigationMenu:
  given Codec.AsObject[NavigationMenu] = deriveConfiguredCodec

final case class Redirect(fromPath: String, toPath: String, statusCode: Int = 301, active: Boolean = true)
object Redirect:
  given Codec.AsObject[Redirect] = deriveConfiguredCodec

final case class SiteSettings(siteName: String, tagline: String = "", locale: String = "en", timezone: String = "UTC", analyticsId: String = "")
object SiteSettings:
  given Codec.AsObject[SiteSettings] = deriveConfiguredCodec

final case class ContentRevision(id: String, articleId: String, authorId: String, diff: String, createdAt: String, message: String = "")
object ContentRevision:
  given Codec.AsObject[ContentRevision] = deriveConfiguredCodec

// ADTs
sealed trait ContentStatus
object ContentStatus:
  private given Configuration = Configuration.default.withDefaults.withDiscriminator("status")
  given Codec.AsObject[ContentStatus] = deriveConfiguredCodec
final case class DraftContent(createdBy: String, lastEditedAt: String = "") extends ContentStatus
object DraftContent:
  given Codec.AsObject[DraftContent] = deriveConfiguredCodec
final case class InReview(reviewerId: String, submittedAt: String) extends ContentStatus
object InReview:
  given Codec.AsObject[InReview] = deriveConfiguredCodec
final case class PublishedContent(publishedAt: String, publishedBy: String) extends ContentStatus
object PublishedContent:
  given Codec.AsObject[PublishedContent] = deriveConfiguredCodec
final case class ScheduledContent(scheduledAt: String, scheduledBy: String) extends ContentStatus
object ScheduledContent:
  given Codec.AsObject[ScheduledContent] = deriveConfiguredCodec
final case class ArchivedContent(archivedAt: String, reason: String = "") extends ContentStatus
object ArchivedContent:
  given Codec.AsObject[ArchivedContent] = deriveConfiguredCodec

sealed trait MediaType
object MediaType:
  private given Configuration = Configuration.default.withDefaults.withDiscriminator("media_type")
  given Codec.AsObject[MediaType] = deriveConfiguredCodec
final case class ImageMedia(width: Int, height: Int, format: String = "jpeg") extends MediaType
object ImageMedia:
  given Codec.AsObject[ImageMedia] = deriveConfiguredCodec
final case class VideoMedia(duration: Int, resolution: String, codec: String = "h264") extends MediaType
object VideoMedia:
  given Codec.AsObject[VideoMedia] = deriveConfiguredCodec
final case class AudioMedia(duration: Int, bitrate: Int, format: String = "mp3") extends MediaType
object AudioMedia:
  given Codec.AsObject[AudioMedia] = deriveConfiguredCodec
final case class DocumentMedia(pageCount: Int, format: String = "pdf") extends MediaType
object DocumentMedia:
  given Codec.AsObject[DocumentMedia] = deriveConfiguredCodec

object ContentDomain:
  def run(): Unit =
    val article = Article(
      ArticleId("a1"), "Getting Started", "getting-started",
      Author("auth1", "Alice"), ArticleBody(content = "Hello world", wordCount = 2),
      CategoryLabel("tutorial", "Tutorials"), List(TagLabel("beginner", "Beginner")),
      SeoMeta("Getting Started Guide"), Some(FeaturedImage("img.jpg", "hero")),
    )
    assert(decode[Article](article.asJson.noSpaces) == Right(article))

    val status: ContentStatus = InReview("rev1", "2024-01-15")
    assert(decode[ContentStatus](status.asJson.noSpaces) == Right(status))

    val media: MediaType = VideoMedia(120, "1080p")
    assert(decode[MediaType](media.asJson.noSpaces) == Right(media))

    val nav = NavigationMenu("m1", "Main", List(NavigationItem("Home", "/"), NavigationItem("Blog", "/blog")))
    assert(decode[NavigationMenu](nav.asJson.noSpaces) == Right(nav))

    val settings = SiteSettings("My Blog", "A great blog")
    assert(decode[SiteSettings](settings.asJson.noSpaces) == Right(settings))
