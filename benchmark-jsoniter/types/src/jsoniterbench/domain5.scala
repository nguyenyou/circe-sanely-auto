package jsoniterbench

// Domain 5: Analytics & Projects

case class EventId(value: String)
case class AnalyticsSessionId(value: String)
case class PageView(sessionId: AnalyticsSessionId, url: String, referrer: Option[String], duration: Int, timestamp: String)
case class ClickEvent(sessionId: AnalyticsSessionId, elementId: String, url: String, x: Int, y: Int, timestamp: String)
case class ConversionEvent(sessionId: AnalyticsSessionId, eventName: String, value: Option[Double], properties: Map[String, String], timestamp: String)

sealed trait AnalyticsEvent
case class PageViewAnalytics(url: String, title: String, referrer: Option[String]) extends AnalyticsEvent
case class ClickAnalytics(elementId: String, elementType: String, label: String) extends AnalyticsEvent
case class FormSubmitAnalytics(formId: String, fields: List[String], success: Boolean) extends AnalyticsEvent
case class PurchaseAnalytics(orderId: String, amount: Double, currency: String, items: Int) extends AnalyticsEvent
case class CustomAnalytics(name: String, properties: Map[String, String]) extends AnalyticsEvent

case class FunnelStep(name: String, count: Int, conversionRate: Double)
case class Funnel(id: String, name: String, steps: List[FunnelStep], totalConversion: Double)
case class Segment(id: String, name: String, description: String, filters: List[String], userCount: Int)
case class Cohort(id: String, name: String, startDate: String, endDate: String, size: Int, retentionRates: List[Double])
case class ABTestResult(testId: String, variant: String, sampleSize: Int, conversionRate: Double, confidence: Double, isWinner: Boolean)
case class DashboardWidget(id: String, widgetType: String, title: String, dataSource: String, config: Map[String, String], position: Int)
case class AnalyticsDashboard(id: String, name: String, description: Option[String], widgets: List[DashboardWidget], createdBy: String, isPublic: Boolean)
case class DataFilter(field: String, operator: String, value: String)
case class DataQuery(source: String, metrics: List[String], dimensions: List[String], filters: List[DataFilter], queryLimit: Int)
case class ReportSchedule(id: String, reportId: String, frequency: String, recipients: List[String], format: String, enabled: Boolean)

// Projects
case class ProjectId(value: String)
case class TaskId(value: String)
case class SprintInfo(id: String, name: String, startDate: String, endDate: String, goal: String)
case class EpicInfo(id: String, title: String, description: String, priority: Int, progress: Double)
case class StoryInfo(id: String, title: String, description: String, points: Int, epicId: Option[String])

sealed trait TaskPriority
case class P0Priority(reason: String) extends TaskPriority
case class P1Priority(deadline: Option[String]) extends TaskPriority
case class P2Priority(target: String) extends TaskPriority
case class P3Priority(notes: Option[String]) extends TaskPriority

sealed trait TaskStatus
case class TodoStatus(createdAt: String) extends TaskStatus
case class InProgressStatus(startedAt: String, assignee: String) extends TaskStatus
case class InReviewStatus(reviewer: String, submittedAt: String) extends TaskStatus
case class DoneStatus(completedAt: String, completedBy: String) extends TaskStatus
case class BlockedStatus(blockedBy: String, reason: String) extends TaskStatus

case class ProjectMilestone(id: String, name: String, dueDate: String, progress: Double, openTasks: Int, closedTasks: Int)
case class ReleaseInfo(version: String, date: String, changelog: List[String], breakingChanges: List[String])
case class TaskAttachment(id: String, name: String, url: String, mimeType: String, size: Long, uploadedBy: String, uploadedAt: String)
case class TeamMember(id: String, name: String, memberEmail: String, role: String, availability: Double)
case class Team(id: String, name: String, description: String, members: List[TeamMember], leadId: String)
case class WorkflowStep(name: String, assignee: Option[String], estimatedHours: Double, dependencies: List[String])
case class ProjectWorkflow(id: String, name: String, steps: List[WorkflowStep], isActive: Boolean)
