package demo

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import sanely.auto.given

case class Address(street: String, city: String)
case class Person(name: String, age: Int, address: Address)

enum Shape:
  case Circle(radius: Double)
  case Rectangle(width: Double, height: Double)

@main def run(): Unit =
  // Product round-trip
  val person = Person("Alice", 30, Address("123 Main St", "Springfield"))
  val json = person.asJson
  println(s"Encoded: ${json.spaces2}")

  val decoded = decode[Person](json.noSpaces)
  println(s"Decoded: $decoded")
  assert(decoded == Right(person), s"Round-trip failed! Got $decoded")
  println("Product round-trip OK")

  // Sum round-trip
  val shapes: List[Shape] = List(Shape.Circle(5.0), Shape.Rectangle(3.0, 4.0))
  for shape <- shapes do
    val sJson = shape.asJson
    println(s"Shape encoded: ${sJson.noSpaces}")
    val sDecoded = decode[Shape](sJson.noSpaces)
    println(s"Shape decoded: $sDecoded")
    assert(sDecoded == Right(shape), s"Sum round-trip failed! Got $sDecoded")
  println("Sum round-trip OK")

  println("\nAll tests passed!")
