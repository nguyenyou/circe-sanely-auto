package benchmark.domain7

import io.circe._
import io.circe.syntax._
import io.circe.parser._
import io.circe.generic.auto._

case class Milestone(name: String, dueDate: String, completed: Boolean, description: String)
case class Label(name: String, color: String, description: String)

sealed trait Priority
case class UrgentPri(deadline: String, escalated: Boolean) extends Priority
case class HighPri(reason: String) extends Priority
case class MediumPri(notes: String) extends Priority
case class LowPri(deferUntil: String) extends Priority
case class TrivialPri(autoClose: Boolean) extends Priority

sealed trait TicketType
case class BugTicket(severity: String, stepsToReproduce: List[String], environment: String) extends TicketType
case class FeatureTicket(businessValue: String, effort: Int, requester: String) extends TicketType
case class ChoreTicket(category: String, recurring: Boolean) extends TicketType
case class SpikeTicket(question: String, timebox: Int, findings: String) extends TicketType
case class EpicTicket(description: String, childCount: Int) extends TicketType
case class SubtaskTicket(parentId: String, description: String) extends TicketType

sealed trait TicketState
case class Backlog(addedAt: String) extends TicketState
case class Todo(orderedAt: String) extends TicketState
case class InProgress(startedAt: String, assignee: String) extends TicketState
case class InReview(reviewerId: String, submittedAt: String) extends TicketState
case class Testing(testerId: String, startedAt: String) extends TicketState
case class Done(completedAt: String, resolution: String) extends TicketState
case class Wontfix(reason: String, closedAt: String) extends TicketState

case class TicketComment(id: String, author: String, body: String, createdAt: String, editedAt: String)
case class TimeEntry(id: String, userId: String, hours: Double, date: String, note: String)
case class Attachment(id: String, filename: String, url: String, sizeBytes: Int, mimeType: String)
case class TicketRelation(relationType: String, targetId: String)
case class Ticket(id: String, title: String, ticketType: TicketType, priority: Priority, state: TicketState,
  labels: List[Label], comments: List[TicketComment], timeEntries: List[TimeEntry],
  attachments: List[Attachment], relations: List[TicketRelation], milestone: Milestone)

case class Sprint(name: String, goal: String, startDate: String, endDate: String, tickets: List[Ticket], velocity: Int)
case class Retrospective(sprintName: String, wentWell: List[String], needsImprovement: List[String], actionItems: List[String])
case class TeamMember(name: String, role: String, capacity: Double, skills: List[String])
case class Team(name: String, members: List[TeamMember], lead: String)
case class RoadmapItem(title: String, quarter: String, status: String, milestones: List[Milestone], teams: List[String])

object Domain7:
  def run(): Unit =
    val comment = TicketComment("c1", "Dev", "Fixed in branch feat/fix", "2024-03-01", "")
    val time = TimeEntry("t1", "u1", 2.5, "2024-03-01", "debugging")
    val attach = Attachment("a1", "screenshot.png", "https://s3/screenshot.png", 524288, "image/png")
    val ticket = Ticket("T-1", "Fix login timeout",
      BugTicket("critical", List("Go to /login", "Wait 30s", "See timeout"), "production"),
      UrgentPri("2024-03-05", true), InProgress("2024-03-02", "dev1"),
      List(Label("bug", "#ff0000", ""), Label("auth", "#00ff00", "")),
      List(comment), List(time), List(attach),
      List(TicketRelation("blocks", "T-2")),
      Milestone("v1.0", "2024-04-01", false, "First release"))
    assert(decode[Ticket](ticket.asJson.noSpaces) == Right(ticket))

    val sprint = Sprint("Sprint 7", "Fix critical bugs", "2024-03-01", "2024-03-14", List(ticket), 21)
    assert(decode[Sprint](sprint.asJson.noSpaces) == Right(sprint))

    val retro = Retrospective("Sprint 7", List("Good collaboration"), List("Too many bugs"), List("Add more tests"))
    assert(decode[Retrospective](retro.asJson.noSpaces) == Right(retro))

    val team = Team("Backend", List(TeamMember("Alice", "Senior", 1.0, List("Scala", "Kafka")), TeamMember("Bob", "Junior", 0.8, List("Java"))), "Alice")
    assert(decode[Team](team.asJson.noSpaces) == Right(team))

    val roadmap = RoadmapItem("Auth Rewrite", "Q2 2024", "in-progress",
      List(Milestone("Design", "2024-04-01", true, ""), Milestone("Impl", "2024-05-15", false, "")),
      List("Backend", "Security"))
    assert(decode[RoadmapItem](roadmap.asJson.noSpaces) == Right(roadmap))
