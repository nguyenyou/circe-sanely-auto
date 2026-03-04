package configured

object Main:
  def main(args: Array[String]): Unit =
    configured.crm.CrmDomain.run()
    configured.inventory.InventoryDomain.run()
    configured.billing.BillingDomain.run()
    configured.messaging.MessagingDomain.run()
    configured.access.AccessDomain.run()
    configured.content.ContentDomain.run()
    configured.workflow.WorkflowDomain.run()
    configured.analytics.AnalyticsDomain.run()
    println("All configured domains passed!")
