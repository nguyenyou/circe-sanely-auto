package sanely

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong}
import scala.jdk.CollectionConverters.*

/** Compile-time profiling utility for macro expansion.
  * Enable with environment variable: SANELY_PROFILE=true
  * Prints per-expansion timing and a global summary at JVM shutdown.
  */
private[sanely] object MacroTimer:
  val enabled: Boolean = System.getenv("SANELY_PROFILE") == "true"

  private val globalTimings = new ConcurrentHashMap[String, AtomicLong]()
  private val globalCounts = new ConcurrentHashMap[String, AtomicLong]()
  private val totalExpansions = new AtomicInteger(0)
  private val hookRegistered = new AtomicBoolean(false)

  private def ensureShutdownHook(): Unit =
    if enabled && hookRegistered.compareAndSet(false, true) then
      Runtime.getRuntime.addShutdownHook(new Thread(() => printSummary(), "sanely-profile"))

  def create(typeName: String, kind: String): MacroTimer =
    ensureShutdownHook()
    new MacroTimer(typeName, kind)

  private[sanely] def addGlobal(category: String, nanos: Long, count: Int): Unit =
    globalTimings.computeIfAbsent(category, _ => new AtomicLong(0)).addAndGet(nanos)
    globalCounts.computeIfAbsent(category, _ => new AtomicLong(0)).addAndGet(count)

  private def printSummary(): Unit =
    if !enabled then return
    val n = totalExpansions.get
    System.err.println()
    System.err.println(s"[PROFILE] ══════════════════════════════════════════════════════")
    System.err.println(s"[PROFILE] SUMMARY ($n macro expansions)")
    System.err.println(s"[PROFILE] ──────────────────────────────────────────────────────")
    val sorted = globalTimings.asScala.toList.sortBy(_._1)
    val grouped = sorted.groupBy(_._1.split("\\.").head).toList.sortBy(_._1)
    for (group, entries) <- grouped do
      System.err.println(s"[PROFILE]")
      for (cat, nanos) <- entries.sortBy(-_._2.get) do
        val count = globalCounts.getOrDefault(cat, new AtomicLong(0)).get
        val ms = nanos.get / 1e6
        val avg = if count > 0 then ms / count else 0.0
        System.err.println(f"[PROFILE]   $cat%-40s ${ms}%8.1fms  ${count}%5d calls  avg ${avg}%.2fms")
    System.err.println(s"[PROFILE] ══════════════════════════════════════════════════════")

private[sanely] class MacroTimer(typeName: String, kind: String):
  private val categories = scala.collection.mutable.LinkedHashMap.empty[String, (Long, Int)]
  private val startTime = if MacroTimer.enabled then System.nanoTime() else 0L

  def time[T](category: String)(body: => T): T =
    if !MacroTimer.enabled then return body
    val start = System.nanoTime()
    val result = body
    val elapsed = System.nanoTime() - start
    val (prev, count) = categories.getOrElse(category, (0L, 0))
    categories(category) = (prev + elapsed, count + 1)
    result

  def count(category: String): Unit =
    if !MacroTimer.enabled then return
    val (prev, cnt) = categories.getOrElse(category, (0L, 0))
    categories(category) = (prev, cnt + 1)

  def report(): Unit =
    if !MacroTimer.enabled then return
    MacroTimer.totalExpansions.incrementAndGet()
    val total = System.nanoTime() - startTime
    MacroTimer.addGlobal(s"$kind.total", total, 1)
    for (cat, (ns, cnt)) <- categories do
      MacroTimer.addGlobal(s"$kind.$cat", ns, cnt)
    val parts = categories.map { case (cat, (ns, cnt)) =>
      f"$cat=${ns / 1e6}%.1fms(${cnt}x)"
    }.mkString(" ")
    System.err.println(f"[PROFILE] $kind[${typeName}]: total=${total / 1e6}%.1fms $parts")
