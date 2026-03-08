package jsoniterbench

import sanely.jsoniter.semiauto.*
import com.github.plokhotnyuk.jsoniter_scala.core.*

// Additional jsoniter codec derivation (on top of circe codecs in CirceCodecs.scala).
// Types ordered by dependency so the macro can resolve List[CaseClass] fields.
object JsoniterCodecs:

  // ── Batch 1: Leaf types (only primitives, String, Option[prim]) ──

  // Domain 1
  given JsonValueCodec[UserId] = deriveJsoniterCodec
  given JsonValueCodec[Email] = deriveJsoniterCodec
  given JsonValueCodec[PhoneNumber] = deriveJsoniterCodec
  given JsonValueCodec[UserName] = deriveJsoniterCodec
  given JsonValueCodec[StreetAddress] = deriveJsoniterCodec
  given JsonValueCodec[GeoCoord] = deriveJsoniterCodec
  given JsonValueCodec[UserBio] = deriveJsoniterCodec
  given JsonValueCodec[Avatar] = deriveJsoniterCodec
  given JsonValueCodec[SocialLinks] = deriveJsoniterCodec
  given JsonValueCodec[NotifPrefs] = deriveJsoniterCodec
  given JsonValueCodec[DisplayPrefs] = deriveJsoniterCodec
  given JsonValueCodec[PrivacyPrefs] = deriveJsoniterCodec
  given JsonValueCodec[PasswordPolicy] = deriveJsoniterCodec

  // Domain 2
  given JsonValueCodec[ProductId] = deriveJsoniterCodec
  given JsonValueCodec[Sku] = deriveJsoniterCodec
  given JsonValueCodec[Price] = deriveJsoniterCodec
  given JsonValueCodec[Weight] = deriveJsoniterCodec
  given JsonValueCodec[Dimensions] = deriveJsoniterCodec
  given JsonValueCodec[ProductImage] = deriveJsoniterCodec
  given JsonValueCodec[CategoryId] = deriveJsoniterCodec
  given JsonValueCodec[OrderId] = deriveJsoniterCodec
  given JsonValueCodec[Coupon] = deriveJsoniterCodec
  given JsonValueCodec[TaxRate] = deriveJsoniterCodec
  given JsonValueCodec[ShippingAddress] = deriveJsoniterCodec

  // Domain 3
  given JsonValueCodec[ArticleId] = deriveJsoniterCodec
  given JsonValueCodec[ContentSlug] = deriveJsoniterCodec
  given JsonValueCodec[CommentId] = deriveJsoniterCodec
  given JsonValueCodec[ArticleMeta] = deriveJsoniterCodec
  given JsonValueCodec[PageMeta] = deriveJsoniterCodec
  given JsonValueCodec[ContentFolder] = deriveJsoniterCodec
  given JsonValueCodec[Reaction] = deriveJsoniterCodec
  given JsonValueCodec[NavMenuItem] = deriveJsoniterCodec
  given JsonValueCodec[SiteSettings] = deriveJsoniterCodec

  // Domain 4
  given JsonValueCodec[MetricId] = deriveJsoniterCodec
  given JsonValueCodec[TimeSeriesPoint] = deriveJsoniterCodec
  given JsonValueCodec[AlertThreshold] = deriveJsoniterCodec
  given JsonValueCodec[AlertEvent] = deriveJsoniterCodec
  given JsonValueCodec[HealthCheck] = deriveJsoniterCodec
  given JsonValueCodec[ServiceInfo] = deriveJsoniterCodec
  given JsonValueCodec[ServiceEndpoint] = deriveJsoniterCodec
  given JsonValueCodec[ServerConfig] = deriveJsoniterCodec
  given JsonValueCodec[ContainerInfo] = deriveJsoniterCodec
  given JsonValueCodec[DeploymentInfo] = deriveJsoniterCodec
  given JsonValueCodec[ErrorReport] = deriveJsoniterCodec
  given JsonValueCodec[PerformanceMetric] = deriveJsoniterCodec
  given JsonValueCodec[CpuMetrics] = deriveJsoniterCodec
  given JsonValueCodec[MemoryMetrics] = deriveJsoniterCodec
  given JsonValueCodec[DiskMetrics] = deriveJsoniterCodec
  given JsonValueCodec[NetworkMetrics] = deriveJsoniterCodec

  // Domain 5
  given JsonValueCodec[EventId] = deriveJsoniterCodec
  given JsonValueCodec[AnalyticsSessionId] = deriveJsoniterCodec
  given JsonValueCodec[FunnelStep] = deriveJsoniterCodec
  given JsonValueCodec[DataFilter] = deriveJsoniterCodec
  given JsonValueCodec[ProjectId] = deriveJsoniterCodec
  given JsonValueCodec[TaskId] = deriveJsoniterCodec
  given JsonValueCodec[SprintInfo] = deriveJsoniterCodec
  given JsonValueCodec[EpicInfo] = deriveJsoniterCodec
  given JsonValueCodec[StoryInfo] = deriveJsoniterCodec
  given JsonValueCodec[ABTestResult] = deriveJsoniterCodec
  given JsonValueCodec[Segment] = deriveJsoniterCodec
  given JsonValueCodec[Cohort] = deriveJsoniterCodec
  given JsonValueCodec[ReportSchedule] = deriveJsoniterCodec
  given JsonValueCodec[ProjectMilestone] = deriveJsoniterCodec
  given JsonValueCodec[ReleaseInfo] = deriveJsoniterCodec
  given JsonValueCodec[TaskAttachment] = deriveJsoniterCodec
  given JsonValueCodec[TeamMember] = deriveJsoniterCodec
  given JsonValueCodec[WorkflowStep] = deriveJsoniterCodec

  // Wide & extras
  given JsonValueCodec[Wide16A] = deriveJsoniterCodec
  given JsonValueCodec[Wide16B] = deriveJsoniterCodec
  given JsonValueCodec[Wide22A] = deriveJsoniterCodec
  given JsonValueCodec[Wide22B] = deriveJsoniterCodec
  given JsonValueCodec[Config22] = deriveJsoniterCodec
  given JsonValueCodec[UserProfile22] = deriveJsoniterCodec
  given JsonValueCodec[OrderDetail22] = deriveJsoniterCodec
  given JsonValueCodec[EventLog22] = deriveJsoniterCodec
  given JsonValueCodec[Rgb] = deriveJsoniterCodec
  given JsonValueCodec[Hsl] = deriveJsoniterCodec
  given JsonValueCodec[Vec2] = deriveJsoniterCodec
  given JsonValueCodec[Vec3] = deriveJsoniterCodec
  given JsonValueCodec[Matrix2x2] = deriveJsoniterCodec
  given JsonValueCodec[DateRange] = deriveJsoniterCodec
  given JsonValueCodec[TimeSlot] = deriveJsoniterCodec
  given JsonValueCodec[SemVer] = deriveJsoniterCodec
  given JsonValueCodec[ConnPool] = deriveJsoniterCodec
  given JsonValueCodec[CacheConfig] = deriveJsoniterCodec

  // Leaf types with Map[String, String] fields
  given JsonValueCodec[MetricValue] = deriveJsoniterCodec
  given JsonValueCodec[LogEntry] = deriveJsoniterCodec
  given JsonValueCodec[TraceSpan] = deriveJsoniterCodec
  given JsonValueCodec[MediaFile] = deriveJsoniterCodec
  given JsonValueCodec[DashboardWidget] = deriveJsoniterCodec

  // ── Batch 2: Sealed trait subtypes ──

  given JsonValueCodec[AdminRole] = deriveJsoniterCodec
  given JsonValueCodec[EditorRole] = deriveJsoniterCodec
  given JsonValueCodec[ViewerRole] = deriveJsoniterCodec
  given JsonValueCodec[GuestRole] = deriveJsoniterCodec
  given JsonValueCodec[SuperAdminRole] = deriveJsoniterCodec
  given JsonValueCodec[ModeratorRole] = deriveJsoniterCodec
  given JsonValueCodec[ActiveAccount] = deriveJsoniterCodec
  given JsonValueCodec[SuspendedAccount] = deriveJsoniterCodec
  given JsonValueCodec[DeactivatedAccount] = deriveJsoniterCodec
  given JsonValueCodec[PendingVerification] = deriveJsoniterCodec
  given JsonValueCodec[LockedAccount] = deriveJsoniterCodec
  given JsonValueCodec[CreditCard] = deriveJsoniterCodec
  given JsonValueCodec[BankTransfer] = deriveJsoniterCodec
  given JsonValueCodec[PayPalPayment] = deriveJsoniterCodec
  given JsonValueCodec[CryptoPayment] = deriveJsoniterCodec
  given JsonValueCodec[GiftCardPayment] = deriveJsoniterCodec
  given JsonValueCodec[PendingOrder] = deriveJsoniterCodec
  given JsonValueCodec[ProcessingOrder] = deriveJsoniterCodec
  given JsonValueCodec[ShippedOrder] = deriveJsoniterCodec
  given JsonValueCodec[DeliveredOrder] = deriveJsoniterCodec
  given JsonValueCodec[CancelledOrder] = deriveJsoniterCodec
  given JsonValueCodec[RefundedOrder] = deriveJsoniterCodec
  given JsonValueCodec[TextBlock] = deriveJsoniterCodec
  given JsonValueCodec[ImageBlock] = deriveJsoniterCodec
  given JsonValueCodec[VideoBlock] = deriveJsoniterCodec
  given JsonValueCodec[CodeBlock] = deriveJsoniterCodec
  given JsonValueCodec[QuoteBlock] = deriveJsoniterCodec
  given JsonValueCodec[EmbedBlock] = deriveJsoniterCodec
  given JsonValueCodec[PhotoMedia] = deriveJsoniterCodec
  given JsonValueCodec[VideoMedia] = deriveJsoniterCodec
  given JsonValueCodec[AudioMedia] = deriveJsoniterCodec
  given JsonValueCodec[DocumentMedia] = deriveJsoniterCodec
  given JsonValueCodec[CriticalSev] = deriveJsoniterCodec
  given JsonValueCodec[HighSev] = deriveJsoniterCodec
  given JsonValueCodec[MediumSev] = deriveJsoniterCodec
  given JsonValueCodec[LowSev] = deriveJsoniterCodec
  given JsonValueCodec[InfoSev] = deriveJsoniterCodec
  given JsonValueCodec[HealthyStatus] = deriveJsoniterCodec
  given JsonValueCodec[DegradedStatus] = deriveJsoniterCodec
  given JsonValueCodec[DownStatus] = deriveJsoniterCodec
  given JsonValueCodec[UnknownHealthStatus] = deriveJsoniterCodec
  given JsonValueCodec[MaintenanceStatus] = deriveJsoniterCodec
  given JsonValueCodec[PageViewAnalytics] = deriveJsoniterCodec
  given JsonValueCodec[ClickAnalytics] = deriveJsoniterCodec
  given JsonValueCodec[FormSubmitAnalytics] = deriveJsoniterCodec
  given JsonValueCodec[PurchaseAnalytics] = deriveJsoniterCodec
  given JsonValueCodec[CustomAnalytics] = deriveJsoniterCodec
  given JsonValueCodec[P0Priority] = deriveJsoniterCodec
  given JsonValueCodec[P1Priority] = deriveJsoniterCodec
  given JsonValueCodec[P2Priority] = deriveJsoniterCodec
  given JsonValueCodec[P3Priority] = deriveJsoniterCodec
  given JsonValueCodec[TodoStatus] = deriveJsoniterCodec
  given JsonValueCodec[InProgressStatus] = deriveJsoniterCodec
  given JsonValueCodec[InReviewStatus] = deriveJsoniterCodec
  given JsonValueCodec[DoneStatus] = deriveJsoniterCodec
  given JsonValueCodec[BlockedStatus] = deriveJsoniterCodec

  // ── Batch 3: Sealed traits ──

  given JsonValueCodec[UserRole] = deriveJsoniterCodec
  given JsonValueCodec[AccountStatus] = deriveJsoniterCodec
  given JsonValueCodec[PaymentMethod] = deriveJsoniterCodec
  given JsonValueCodec[OrderStatus] = deriveJsoniterCodec
  given JsonValueCodec[ContentBlock] = deriveJsoniterCodec
  given JsonValueCodec[MediaType] = deriveJsoniterCodec
  given JsonValueCodec[Severity] = deriveJsoniterCodec
  given JsonValueCodec[HealthStatus] = deriveJsoniterCodec
  given JsonValueCodec[AnalyticsEvent] = deriveJsoniterCodec
  given JsonValueCodec[TaskPriority] = deriveJsoniterCodec
  given JsonValueCodec[TaskStatus] = deriveJsoniterCodec

  // ── Batch 4: Composites with case-class fields ──

  given JsonValueCodec[ProductVariant] = deriveJsoniterCodec
  given JsonValueCodec[ProductReview] = deriveJsoniterCodec
  given JsonValueCodec[Category] = deriveJsoniterCodec
  given JsonValueCodec[CartItem] = deriveJsoniterCodec
  given JsonValueCodec[InvoiceLine] = deriveJsoniterCodec
  given JsonValueCodec[ShippingInfo] = deriveJsoniterCodec
  given JsonValueCodec[OrderLine] = deriveJsoniterCodec
  given JsonValueCodec[Author] = deriveJsoniterCodec
  given JsonValueCodec[ContentTag] = deriveJsoniterCodec
  given JsonValueCodec[InventoryRecord] = deriveJsoniterCodec
  given JsonValueCodec[TimeSeries] = deriveJsoniterCodec
  given JsonValueCodec[AlertRule] = deriveJsoniterCodec
  given JsonValueCodec[PageView] = deriveJsoniterCodec
  given JsonValueCodec[ClickEvent] = deriveJsoniterCodec
  given JsonValueCodec[ConversionEvent] = deriveJsoniterCodec
  given JsonValueCodec[SemVerFull] = deriveJsoniterCodec
  given JsonValueCodec[DbConfig] = deriveJsoniterCodec

  // ── Batch 5: Higher-level composites ──

  given JsonValueCodec[UserProfile] = deriveJsoniterCodec
  given JsonValueCodec[Preferences] = deriveJsoniterCodec
  given JsonValueCodec[Session] = deriveJsoniterCodec
  given JsonValueCodec[LoginAttempt] = deriveJsoniterCodec
  given JsonValueCodec[AuditEntry] = deriveJsoniterCodec
  given JsonValueCodec[AuthToken] = deriveJsoniterCodec
  given JsonValueCodec[ApiKey] = deriveJsoniterCodec
  given JsonValueCodec[PublishInfo] = deriveJsoniterCodec
  given JsonValueCodec[Gallery] = deriveJsoniterCodec
  given JsonValueCodec[Navigation] = deriveJsoniterCodec
  given JsonValueCodec[ContentPage] = deriveJsoniterCodec
  given JsonValueCodec[ContentTemplate] = deriveJsoniterCodec
  given JsonValueCodec[Funnel] = deriveJsoniterCodec
  given JsonValueCodec[AnalyticsDashboard] = deriveJsoniterCodec
  given JsonValueCodec[DataQuery] = deriveJsoniterCodec
  given JsonValueCodec[Team] = deriveJsoniterCodec
  given JsonValueCodec[ProjectWorkflow] = deriveJsoniterCodec

  // ── Batch 6: Top-level composites ──

  given JsonValueCodec[UserAccount] = deriveJsoniterCodec
  given JsonValueCodec[ProductListing] = deriveJsoniterCodec
  given JsonValueCodec[Cart] = deriveJsoniterCodec
  given JsonValueCodec[Invoice] = deriveJsoniterCodec
  given JsonValueCodec[Article] = deriveJsoniterCodec
  given JsonValueCodec[ArticleComment] = deriveJsoniterCodec
  given JsonValueCodec[Order] = deriveJsoniterCodec
