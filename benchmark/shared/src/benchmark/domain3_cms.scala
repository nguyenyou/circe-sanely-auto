package benchmark.domain3

import io.circe._
import io.circe.syntax._
import io.circe.parser._
import io.circe.generic.auto._

case class ContentId(value: String)
case class Slug(value: String)
case class TagInfo(name: String, slug: Slug, color: String)
case class CategoryInfo(name: String, slug: Slug, parentSlug: String, description: String)
case class AuthorInfo(name: String, email: String, avatar: String, bio: String, socialLinks: List[String])
case class SeoMeta(title: String, description: String, keywords: List[String], canonical: String, ogImage: String)
case class MediaAsset(url: String, mimeType: String, sizeBytes: Int, alt: String, width: Int, height: Int)

sealed trait ContentBlock
case class TextBlock(markdown: String) extends ContentBlock
case class HeadingBlock(level: Int, text: String, anchor: String) extends ContentBlock
case class ImageBlock(url: String, caption: String, width: Int, height: Int, alignment: String) extends ContentBlock
case class CodeBlock(language: String, source: String, filename: String, highlighted: Boolean) extends ContentBlock
case class QuoteBlock(text: String, attribution: String, source: String) extends ContentBlock
case class EmbedBlock(provider: String, url: String, width: Int, height: Int) extends ContentBlock
case class TableBlock(headers: List[String], rowCount: Int, caption: String) extends ContentBlock
case class CalloutBlock(style: String, title: String, body: String) extends ContentBlock

sealed trait PublishState
case class DraftState(lastEditedAt: String, wordCount: Int) extends PublishState
case class InReviewState(reviewer: String, submittedAt: String, notes: String) extends PublishState
case class ScheduledState(publishAt: String, timezone: String) extends PublishState
case class PublishedState(publishedAt: String, url: String, views: Int) extends PublishState
case class ArchivedState(archivedAt: String, reason: String) extends PublishState
case class UnpublishedState(unpublishedAt: String, previousUrl: String) extends PublishState

case class Article(id: ContentId, slug: Slug, title: String, subtitle: String, author: AuthorInfo,
  blocks: List[ContentBlock], tags: List[TagInfo], category: CategoryInfo, seo: SeoMeta,
  state: PublishState, readTimeMin: Int, featured: Boolean)

case class Comment2(id: String, author: String, body: String, createdAt: String, likes: Int, parentId: String)
case class Newsletter(id: String, subject: String, preheader: String, blocks: List[ContentBlock], sentAt: String, recipientCount: Int)
case class Redirect(fromPath: String, toPath: String, statusCode: Int, createdAt: String)
case class SiteConfig(name: String, domain: String, defaultLocale: String, supportedLocales: List[String], analyticsId: String)

object Domain3:
  def run(): Unit =
    val blocks: List[ContentBlock] = List(
      HeadingBlock(1, "Introduction", "intro"),
      TextBlock("# Hello world"),
      ImageBlock("img.png", "A photo", 800, 600, "center"),
      CodeBlock("scala", "val x = 1", "Main.scala", true),
      QuoteBlock("Be kind", "Unknown", "Twitter"),
      EmbedBlock("youtube", "https://youtube.com/watch?v=x", 560, 315),
      TableBlock(List("Name", "Age"), 10, "Users"),
      CalloutBlock("info", "Note", "This is important"))
    val author = AuthorInfo("Jane", "j@b.com", "av.png", "Writer", List("https://twitter.com/jane"))
    val article = Article(ContentId("a1"), Slug("hello"), "Hello", "World", author, blocks,
      List(TagInfo("scala", Slug("scala"), "#ff0000")),
      CategoryInfo("Tech", Slug("tech"), "", "Technology articles"),
      SeoMeta("Hello", "An intro", List("scala"), "https://blog.com/hello", "https://blog.com/og.png"),
      PublishedState("2024-03-01", "https://blog.com/hello", 1500), 5, true)
    assert(decode[Article](article.asJson.noSpaces) == Right(article))

    val comment = Comment2("c1", "reader", "Great article!", "2024-03-02", 5, "")
    assert(decode[Comment2](comment.asJson.noSpaces) == Right(comment))

    val newsletter = Newsletter("n1", "Weekly Digest", "Top stories this week", List(TextBlock("hello")), "2024-03-07", 5000)
    assert(decode[Newsletter](newsletter.asJson.noSpaces) == Right(newsletter))

    val redirect = Redirect("/old-path", "/new-path", 301, "2024-01-15")
    assert(decode[Redirect](redirect.asJson.noSpaces) == Right(redirect))

    val config = SiteConfig("My Blog", "blog.com", "en-US", List("en-US", "es", "fr"), "GA-12345")
    assert(decode[SiteConfig](config.asJson.noSpaces) == Right(config))
