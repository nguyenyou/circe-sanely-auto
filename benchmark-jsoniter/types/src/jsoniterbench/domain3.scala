package jsoniterbench

// Domain 3: Content Management

case class ArticleId(value: String)
case class ContentSlug(value: String)
case class Author(id: String, name: String, email: Email, avatarUrl: Option[String])
case class ContentTag(name: String, slug: String, count: Int)
case class PublishInfo(publishedAt: String, updatedAt: String, author: Author, editor: Option[Author])
case class ArticleMeta(readTime: Int, wordCount: Int, language: String, canonical: Option[String])
case class Article(id: ArticleId, title: String, slug: ContentSlug, body: String, excerpt: String, meta: ArticleMeta, tags: List[ContentTag], publishInfo: PublishInfo)

case class CommentId(value: String)
case class ArticleComment(id: CommentId, articleId: ArticleId, authorId: String, body: String, parentId: Option[CommentId], likes: Int, createdAt: String)
case class Reaction(userId: String, targetId: String, emoji: String, createdAt: String)

sealed trait ContentBlock
case class TextBlock(text: String, format: String) extends ContentBlock
case class ImageBlock(url: String, alt: String, caption: Option[String], blockWidth: Int, blockHeight: Int) extends ContentBlock
case class VideoBlock(url: String, provider: String, thumbnail: Option[String], duration: Int) extends ContentBlock
case class CodeBlock(code: String, language: String, filename: Option[String]) extends ContentBlock
case class QuoteBlock(text: String, attribution: Option[String]) extends ContentBlock
case class EmbedBlock(url: String, html: String, providerName: String) extends ContentBlock

sealed trait MediaType
case class PhotoMedia(url: String, photoWidth: Int, photoHeight: Int, format: String) extends MediaType
case class VideoMedia(url: String, duration: Int, resolution: String, codec: String) extends MediaType
case class AudioMedia(url: String, audioDuration: Int, bitrate: Int, format: String) extends MediaType
case class DocumentMedia(url: String, pages: Int, format: String, size: Long) extends MediaType

case class MediaFile(id: String, name: String, mimeType: String, size: Long, url: String, metadata: Map[String, String], uploadedAt: String)
case class Gallery(id: String, title: String, description: Option[String], images: List[MediaFile], coverImage: Option[MediaFile])
case class ContentFolder(id: String, name: String, parentId: Option[String], path: String, fileCount: Int)
case class ContentTemplate(id: String, name: String, content: String, variables: List[String], createdAt: String)
case class PageMeta(title: String, description: String, keywords: List[String], ogImage: Option[String])
case class ContentPage(id: String, slug: String, templateId: String, meta: PageMeta, publishedAt: Option[String])
case class NavMenuItem(id: String, label: String, url: String, parentId: Option[String], sortOrder: Int)
case class Navigation(id: String, name: String, items: List[NavMenuItem])
case class SiteSettings(siteName: String, tagline: String, logo: Option[String], favicon: Option[String], language: String, timezone: String, analyticsId: Option[String])
