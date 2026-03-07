package demo

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import sanely.auto.given
import sanely.jsoniter.auto.given
import com.github.plokhotnyuk.jsoniter_scala.core.*

case class Address(street: String, city: String)
case class Person(name: String, age: Int, address: Address)

enum Shape:
  case Circle(radius: Double)
  case Rectangle(width: Double, height: Double)

@main def run(): Unit =
  val person = Person("Alice", 30, Address("123 Main St", "Springfield"))

  // === circe (via sanely-auto) ===
  println("=== circe (sanely-auto) ===")
  val json = person.asJson
  println(s"Encoded: ${json.spaces2}")

  val decoded = decode[Person](json.noSpaces)
  println(s"Decoded: $decoded")
  assert(decoded == Right(person), s"Round-trip failed! Got $decoded")
  println("Product round-trip OK")

  val shapes: List[Shape] = List(Shape.Circle(5.0), Shape.Rectangle(3.0, 4.0))
  for shape <- shapes do
    val sJson = shape.asJson
    println(s"Shape encoded: ${sJson.noSpaces}")
    val sDecoded = decode[Shape](sJson.noSpaces)
    assert(sDecoded == Right(shape), s"Sum round-trip failed! Got $sDecoded")
  println("Sum round-trip OK")

  // === jsoniter-scala (via sanely-jsoniter) ===
  println("\n=== jsoniter-scala (sanely-jsoniter) ===")
  val bytes = writeToArray(person)
  val jsoniterStr = new String(bytes)
  println(s"Encoded: $jsoniterStr")

  val decoded2 = readFromArray[Person](bytes)
  println(s"Decoded: $decoded2")
  assert(decoded2 == person, s"Round-trip failed! Got $decoded2")
  println("Product round-trip OK")

  for shape <- shapes do
    val sBytes = writeToArray(shape)
    val sStr = new String(sBytes)
    println(s"Shape encoded: $sStr")
    val sDecoded = readFromArray[Shape](sBytes)
    assert(sDecoded == shape, s"Sum round-trip failed! Got $sDecoded")
  println("Sum round-trip OK")

  // === Cross-format compatibility ===
  println("\n=== Cross-format compatibility ===")
  val circeJson = person.asJson.noSpaces
  val jsoniterJson = new String(writeToArray(person))
  println(s"circe output:    $circeJson")
  println(s"jsoniter output: $jsoniterJson")
  assert(circeJson == jsoniterJson, "Format mismatch!")
  println("Format match OK — circe and jsoniter produce identical JSON")

  // Decode jsoniter output with circe
  val crossDecoded = decode[Person](jsoniterJson)
  assert(crossDecoded == Right(person), s"Cross-decode failed! Got $crossDecoded")
  println("Cross-decode OK — jsoniter output decoded by circe")

  println("\nAll tests passed!")
