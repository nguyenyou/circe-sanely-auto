package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.*

// Test types for auto derivation with container fields (prefixed to avoid collisions)
case class ACInner(x: Int, y: String)
case class ACWithList(name: String, items: List[ACInner])
case class ACWithOption(name: String, inner: Option[ACInner])
case class ACWithMap(name: String, lookup: Map[String, ACInner])
case class ACWithEither(name: String, value: Either[ACInner, ACInner])
case class ACWithVector(name: String, items: Vector[ACInner])
case class ACWithSeq(name: String, items: Seq[ACInner])
case class ACWithSet(name: String, items: Set[ACInner])
case class ACWithNested(name: String, nested: List[Option[ACInner]])

class AutoContainerTest extends munit.FunSuite:

  import sanely.jsoniter.auto.given


    test("auto - List[CaseClass] field") {
      val v = ACWithList("test", List(ACInner(1, "a"), ACInner(2, "b")))
      val json = writeToString(v)
      assert(json == """{"name":"test","items":[{"x":1,"y":"a"},{"x":2,"y":"b"}]}""")
      assert(readFromString[ACWithList](json) == v)
    }

    test("auto - Option[CaseClass] field - Some") {
      val v = ACWithOption("test", Some(ACInner(1, "a")))
      val json = writeToString(v)
      assert(json == """{"name":"test","inner":{"x":1,"y":"a"}}""")
      assert(readFromString[ACWithOption](json) == v)
    }

    test("auto - Option[CaseClass] field - None") {
      val v = ACWithOption("test", None)
      val json = writeToString(v)
      assert(json == """{"name":"test","inner":null}""")
      assert(readFromString[ACWithOption](json) == v)
    }

    test("auto - Map[String, CaseClass] field") {
      val v = ACWithMap("test", Map("k1" -> ACInner(1, "a"), "k2" -> ACInner(2, "b")))
      val json = writeToString(v)
      val decoded = readFromString[ACWithMap](json)
      assert(decoded.name == "test")
      assert(decoded.lookup == v.lookup)
    }

    test("auto - Either[CaseClass, CaseClass] field - Left") {
      val v = ACWithEither("test", Left(ACInner(1, "left")))
      val json = writeToString(v)
      assert(readFromString[ACWithEither](json) == v)
    }

    test("auto - Either[CaseClass, CaseClass] field - Right") {
      val v = ACWithEither("test", Right(ACInner(2, "right")))
      val json = writeToString(v)
      assert(readFromString[ACWithEither](json) == v)
    }

    test("auto - Vector[CaseClass] field") {
      val v = ACWithVector("test", Vector(ACInner(1, "a")))
      val json = writeToString(v)
      assert(readFromString[ACWithVector](json) == v)
    }

    test("auto - Seq[CaseClass] field") {
      val v = ACWithSeq("test", Seq(ACInner(1, "a")))
      val json = writeToString(v)
      assert(readFromString[ACWithSeq](json) == v)
    }

    test("auto - Set[CaseClass] field") {
      val v = ACWithSet("test", Set(ACInner(1, "a"), ACInner(2, "b")))
      val json = writeToString(v)
      assert(readFromString[ACWithSet](json) == v)
    }

    test("auto - nested container List[Option[CaseClass]]") {
      val v = ACWithNested("test", List(Some(ACInner(1, "a")), None, Some(ACInner(2, "b"))))
      val json = writeToString(v)
      assert(readFromString[ACWithNested](json) == v)
    }
