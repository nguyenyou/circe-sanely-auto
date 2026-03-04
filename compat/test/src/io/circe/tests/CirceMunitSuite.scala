package io.circe.tests

import cats.kernel.Eq
import io.circe.testing.{ ArbitraryInstances, EqInstances }
import munit.DisciplineSuite
import org.scalacheck.Arbitrary

trait CirceMunitSuite extends DisciplineSuite with ArbitraryInstances with EqInstances {

  // Tuple1 instances (circe's MissingInstances uses Shapeless, not available in Scala 3)
  implicit def arbitraryTuple1[A](implicit A: Arbitrary[A]): Arbitrary[Tuple1[A]] =
    Arbitrary(A.arbitrary.map(Tuple1(_)))
  implicit def eqTuple1[A: Eq]: Eq[Tuple1[A]] = Eq.by(_._1)

  // Seq Eq instance
  implicit def eqSeq[A: Eq]: Eq[Seq[A]] = Eq.by((_: Seq[A]).toVector)(
    cats.kernel.instances.vector.catsKernelStdEqForVector[A]
  )

  protected def group(name: String)(thunk: => Unit): Unit = {
    val countBefore = munitTestsBuffer.size
    val _ = thunk
    val countAfter = munitTestsBuffer.size
    val countRegistered = countAfter - countBefore
    val registered = munitTestsBuffer.toList.drop(countBefore)
    (0 until countRegistered).foreach(_ => munitTestsBuffer.remove(countBefore))
    registered.foreach(t => munitTestsBuffer += t.withName(s"$name - ${t.name}"))
  }
}
