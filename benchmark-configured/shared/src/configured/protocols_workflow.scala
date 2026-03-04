package configured.workflow

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import io.circe.derivation.Configuration
import io.circe.generic.semiauto.*

// ═══════════════════════════════════════════════════════════════════════
// Workflow / Pipelines (~20 types)
// withSnakeCaseMemberNames, withDefaults, withDiscriminator
// ═══════════════════════════════════════════════════════════════════════

private given Configuration = Configuration.default.withSnakeCaseMemberNames.withDefaults

final case class WorkflowId(value: String)
object WorkflowId:
  given Codec.AsObject[WorkflowId] = deriveConfiguredCodec

final case class StepId(value: String)
object StepId:
  given Codec.AsObject[StepId] = deriveConfiguredCodec

final case class StepInput(name: String, inputType: String = "string", required: Boolean = true, defaultValue: String = "")
object StepInput:
  given Codec.AsObject[StepInput] = deriveConfiguredCodec

final case class StepOutput(name: String, outputType: String = "string")
object StepOutput:
  given Codec.AsObject[StepOutput] = deriveConfiguredCodec

final case class RetryPolicy(maxAttempts: Int = 3, backoffMs: Int = 1000, maxBackoffMs: Int = 30000)
object RetryPolicy:
  given Codec.AsObject[RetryPolicy] = deriveConfiguredCodec

final case class TimeoutConfig(stepTimeoutMs: Int = 60000, workflowTimeoutMs: Int = 3600000)
object TimeoutConfig:
  given Codec.AsObject[TimeoutConfig] = deriveConfiguredCodec

final case class WorkflowStep(
  stepId: StepId,
  stepName: String,
  stepType: String,
  inputs: List[StepInput],
  outputs: List[StepOutput],
  retryPolicy: RetryPolicy = RetryPolicy(),
  dependsOn: List[String] = Nil,
)
object WorkflowStep:
  given Codec.AsObject[WorkflowStep] = deriveConfiguredCodec

final case class WorkflowDefinition(
  workflowId: WorkflowId,
  workflowName: String,
  description: String = "",
  steps: List[WorkflowStep],
  timeoutConfig: TimeoutConfig = TimeoutConfig(),
  enabled: Boolean = true,
)
object WorkflowDefinition:
  given Codec.AsObject[WorkflowDefinition] = deriveConfiguredCodec

final case class ExecutionContext(executionId: String, workflowId: String, triggeredBy: String, startedAt: String, variables: Map[String, String] = Map.empty)
object ExecutionContext:
  given Codec.AsObject[ExecutionContext] = deriveConfiguredCodec

final case class StepExecution(stepId: String, executionId: String, startedAt: String, completedAt: String = "", outputData: String = "")
object StepExecution:
  given Codec.AsObject[StepExecution] = deriveConfiguredCodec

final case class PipelineStage(stageName: String, stageOrder: Int, parallelSteps: List[String], gateApproval: Boolean = false)
object PipelineStage:
  given Codec.AsObject[PipelineStage] = deriveConfiguredCodec

final case class Pipeline(pipelineId: String, pipelineName: String, stages: List[PipelineStage], triggerCron: String = "")
object Pipeline:
  given Codec.AsObject[Pipeline] = deriveConfiguredCodec

final case class ApprovalRequest(requestId: String, stepId: String, requestedBy: String, approvers: List[String], expiresAt: String = "")
object ApprovalRequest:
  given Codec.AsObject[ApprovalRequest] = deriveConfiguredCodec

final case class WebhookTrigger(triggerId: String, endpointPath: String, workflowId: String, secretToken: String = "", active: Boolean = true)
object WebhookTrigger:
  given Codec.AsObject[WebhookTrigger] = deriveConfiguredCodec

final case class ScheduleTrigger(triggerId: String, cronExpression: String, workflowId: String, timezone: String = "UTC", enabled: Boolean = true)
object ScheduleTrigger:
  given Codec.AsObject[ScheduleTrigger] = deriveConfiguredCodec

// ADTs
sealed trait StepStatus
object StepStatus:
  private given Configuration = Configuration.default.withSnakeCaseMemberNames.withDefaults.withDiscriminator("step_status")
  given Codec.AsObject[StepStatus] = deriveConfiguredCodec
final case class PendingStep(scheduledAt: String = "") extends StepStatus
object PendingStep:
  given Codec.AsObject[PendingStep] = deriveConfiguredCodec
final case class RunningStep(startedAt: String, progressPct: Int = 0) extends StepStatus
object RunningStep:
  given Codec.AsObject[RunningStep] = deriveConfiguredCodec
final case class CompletedStep(completedAt: String, durationMs: Long) extends StepStatus
object CompletedStep:
  given Codec.AsObject[CompletedStep] = deriveConfiguredCodec
final case class FailedStep(failedAt: String, errorMessage: String, retryCount: Int = 0) extends StepStatus
object FailedStep:
  given Codec.AsObject[FailedStep] = deriveConfiguredCodec
final case class SkippedStep(reason: String = "condition not met") extends StepStatus
object SkippedStep:
  given Codec.AsObject[SkippedStep] = deriveConfiguredCodec

sealed trait WorkflowEvent
object WorkflowEvent:
  private given Configuration = Configuration.default.withSnakeCaseMemberNames.withDefaults.withDiscriminator("workflow_event")
  given Codec.AsObject[WorkflowEvent] = deriveConfiguredCodec
final case class WorkflowStarted(executionId: String, workflowId: String, startedAt: String) extends WorkflowEvent
object WorkflowStarted:
  given Codec.AsObject[WorkflowStarted] = deriveConfiguredCodec
final case class WorkflowCompleted(executionId: String, completedAt: String, totalDurationMs: Long) extends WorkflowEvent
object WorkflowCompleted:
  given Codec.AsObject[WorkflowCompleted] = deriveConfiguredCodec
final case class WorkflowFailed(executionId: String, failedAt: String, failedStep: String, errorMessage: String) extends WorkflowEvent
object WorkflowFailed:
  given Codec.AsObject[WorkflowFailed] = deriveConfiguredCodec
final case class WorkflowCancelled(executionId: String, cancelledAt: String, cancelledBy: String = "") extends WorkflowEvent
object WorkflowCancelled:
  given Codec.AsObject[WorkflowCancelled] = deriveConfiguredCodec

object WorkflowDomain:
  def run(): Unit =
    val step = WorkflowStep(
      StepId("s1"), "Extract Data", "transform",
      List(StepInput("source", "string")), List(StepOutput("result")),
    )
    val wf = WorkflowDefinition(WorkflowId("wf1"), "ETL Pipeline", "Daily ETL", List(step))
    assert(decode[WorkflowDefinition](wf.asJson.noSpaces) == Right(wf))

    val status: StepStatus = RunningStep("2024-01-15T10:00:00Z", 45)
    assert(decode[StepStatus](status.asJson.noSpaces) == Right(status))

    val event: WorkflowEvent = WorkflowCompleted("ex1", "2024-01-15T11:00:00Z", 3600000)
    assert(decode[WorkflowEvent](event.asJson.noSpaces) == Right(event))

    val pipeline = Pipeline("p1", "Deploy", List(PipelineStage("build", 1, List("compile", "test")), PipelineStage("deploy", 2, List("release"), true)))
    assert(decode[Pipeline](pipeline.asJson.noSpaces) == Right(pipeline))

    val trigger = ScheduleTrigger("t1", "0 0 * * *", "wf1")
    assert(decode[ScheduleTrigger](trigger.asJson.noSpaces) == Right(trigger))
