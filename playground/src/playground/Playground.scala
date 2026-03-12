package playground

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import sanely.auto.given
import org.scalajs.dom
import org.scalajs.dom.{document, html}
import scala.scalajs.js.annotation.JSExportTopLevel

// --- Example types ---

case class Address(street: String, city: String, zipCode: String)
case class Person(name: String, age: Int, email: String, address: Address)

enum Color:
  case Red, Green, Blue

enum Shape:
  case Circle(radius: Double)
  case Rectangle(width: Double, height: Double)
  case Triangle(base: Double, height: Double)

case class Order(id: Int, item: String, quantity: Int, price: Double)
case class ApiResponse(status: String, data: List[Order], total: Int)

// --- Example definitions ---

case class Example(
    name: String,
    description: String,
    scalaCode: String,
    defaultJson: String,
    encode: () => String,
    decode: String => String
)

object Examples:
  val person = Example(
    name = "Person (nested product)",
    description = "A case class with a nested case class field — shows how circe handles product types recursively.",
    scalaCode = """case class Address(street: String, city: String, zipCode: String)
case class Person(name: String, age: Int, email: String, address: Address)

import sanely.auto.given
val json = person.asJson.spaces2
val decoded = decode[Person](jsonString)""",
    defaultJson = Person("Alice", 30, "alice@example.com", Address("123 Main St", "Springfield", "62704")).asJson.spaces2,
    encode = () => Person("Alice", 30, "alice@example.com", Address("123 Main St", "Springfield", "62704")).asJson.spaces2,
    decode = input =>
      io.circe.parser.decode[Person](input) match
        case Right(p) => s"✓ Decoded successfully:\n  Person(\n    name = \"${p.name}\",\n    age = ${p.age},\n    email = \"${p.email}\",\n    address = Address(\n      street = \"${p.address.street}\",\n      city = \"${p.address.city}\",\n      zipCode = \"${p.address.zipCode}\"\n    )\n  )"
        case Left(err) => s"✗ Decode error:\n  ${err.getMessage}"
  )

  val shape = Example(
    name = "Shape (sum type / enum)",
    description = "A sealed enum with multiple variants — shows circe's external tagging: {\"VariantName\": {...}}",
    scalaCode = """enum Shape:
  case Circle(radius: Double)
  case Rectangle(width: Double, height: Double)
  case Triangle(base: Double, height: Double)

import sanely.auto.given
val json = shape.asJson.spaces2
val decoded = decode[Shape](jsonString)""",
    defaultJson = (Shape.Circle(5.0): Shape).asJson.spaces2,
    encode = () =>
      val shapes: List[Shape] = List(Shape.Circle(5.0), Shape.Rectangle(3.0, 4.0), Shape.Triangle(6.0, 2.5))
      shapes.map(s => s"${s.getClass.getSimpleName}:\n${s.asJson.spaces2}").mkString("\n\n"),
    decode = input =>
      io.circe.parser.decode[Shape](input) match
        case Right(s) => s"✓ Decoded successfully:\n  $s"
        case Left(err) => s"✗ Decode error:\n  ${err.getMessage}"
  )

  val color = Example(
    name = "Color (simple enum)",
    description = "A simple enum without fields — encoded as a JSON string.",
    scalaCode = """enum Color:
  case Red, Green, Blue

import sanely.auto.given
val json = color.asJson
val decoded = decode[Color](jsonString)""",
    defaultJson = (Color.Green: Color).asJson.spaces2,
    encode = () =>
      val colors: List[Color] = List(Color.Red, Color.Green, Color.Blue)
      colors.map(c => s"${c}: ${c.asJson.noSpaces}").mkString("\n"),
    decode = input =>
      io.circe.parser.decode[Color](input) match
        case Right(c) => s"✓ Decoded successfully:\n  $c"
        case Left(err) => s"✗ Decode error:\n  ${err.getMessage}"
  )

  val apiResponse = Example(
    name = "ApiResponse (nested list)",
    description = "A response type containing a List of case classes — shows collection handling.",
    scalaCode = """case class Order(id: Int, item: String, quantity: Int, price: Double)
case class ApiResponse(status: String, data: List[Order], total: Int)

import sanely.auto.given
val json = response.asJson.spaces2
val decoded = decode[ApiResponse](jsonString)""",
    defaultJson = ApiResponse(
      "ok",
      List(Order(1, "Widget", 3, 9.99), Order(2, "Gadget", 1, 24.50)),
      2
    ).asJson.spaces2,
    encode = () =>
      ApiResponse(
        "ok",
        List(Order(1, "Widget", 3, 9.99), Order(2, "Gadget", 1, 24.50)),
        2
      ).asJson.spaces2,
    decode = input =>
      io.circe.parser.decode[ApiResponse](input) match
        case Right(r) =>
          val orders = r.data.map(o => s"    Order(id=${o.id}, item=\"${o.item}\", quantity=${o.quantity}, price=${o.price})").mkString(",\n")
          s"✓ Decoded successfully:\n  ApiResponse(\n    status = \"${r.status}\",\n    data = List(\n$orders\n    ),\n    total = ${r.total}\n  )"
        case Left(err) => s"✗ Decode error:\n  ${err.getMessage}"
  )

  val all: List[Example] = List(person, shape, color, apiResponse)

// --- App ---

object Playground:
  def main(args: Array[String]): Unit =
    document.addEventListener("DOMContentLoaded", { (_: dom.Event) =>
      render()
    })

  private def render(): Unit =
    val app = document.getElementById("app")
    if app == null then return

    app.innerHTML = ""

    // Header
    val header = document.createElement("div").asInstanceOf[html.Div]
    header.className = "header"
    header.innerHTML = """
      <h1>circe-sanely-auto Playground</h1>
      <p>Interactive JSON ↔ Scala case class encoding/decoding — powered by <a href="https://github.com/nguyenyou/circe-sanely-auto" target="_blank">circe-sanely-auto</a> running in the browser via Scala.js</p>
    """
    app.appendChild(header)

    // Example selector
    val nav = document.createElement("div").asInstanceOf[html.Div]
    nav.className = "examples-nav"
    Examples.all.zipWithIndex.foreach { (ex, idx) =>
      val btn = document.createElement("button").asInstanceOf[html.Button]
      btn.textContent = ex.name
      btn.className = if idx == 0 then "example-btn active" else "example-btn"
      btn.dataset.update("index", idx.toString)
      btn.addEventListener("click", { (_: dom.Event) =>
        selectExample(idx)
      })
      nav.appendChild(btn)
    }
    app.appendChild(nav)

    // Main content
    val content = document.createElement("div").asInstanceOf[html.Div]
    content.id = "content"
    app.appendChild(content)

    selectExample(0)

  private def selectExample(idx: Int): Unit =
    val ex = Examples.all(idx)

    // Update active button
    document.querySelectorAll(".example-btn").foreach { node =>
      val btn = node.asInstanceOf[html.Button]
      btn.className = if btn.dataset.get("index").contains(idx.toString) then "example-btn active" else "example-btn"
    }

    val content = document.getElementById("content")
    content.innerHTML = ""

    // Description
    val desc = document.createElement("div").asInstanceOf[html.Div]
    desc.className = "description"
    desc.textContent = ex.description
    content.appendChild(desc)

    // Scala code
    val codeSection = document.createElement("div").asInstanceOf[html.Div]
    codeSection.className = "code-section"
    codeSection.innerHTML = s"<h3>Scala Code</h3><pre><code>${escapeHtml(ex.scalaCode)}</code></pre>"
    content.appendChild(codeSection)

    // Two-panel layout
    val panels = document.createElement("div").asInstanceOf[html.Div]
    panels.className = "panels"

    // Left panel: JSON input
    val leftPanel = document.createElement("div").asInstanceOf[html.Div]
    leftPanel.className = "panel"

    val leftHeader = document.createElement("div").asInstanceOf[html.Div]
    leftHeader.className = "panel-header"

    val leftTitle = document.createElement("h3")
    leftTitle.textContent = "JSON Input"

    val decodeBtn = document.createElement("button").asInstanceOf[html.Button]
    decodeBtn.textContent = "Decode →"
    decodeBtn.className = "action-btn decode-btn"

    val resetBtn = document.createElement("button").asInstanceOf[html.Button]
    resetBtn.textContent = "Reset"
    resetBtn.className = "action-btn reset-btn"

    leftHeader.appendChild(leftTitle)
    val btnGroup = document.createElement("div").asInstanceOf[html.Div]
    btnGroup.className = "btn-group"
    btnGroup.appendChild(resetBtn)
    btnGroup.appendChild(decodeBtn)
    leftHeader.appendChild(btnGroup)

    val jsonInput = document.createElement("textarea").asInstanceOf[html.TextArea]
    jsonInput.className = "json-input"
    jsonInput.value = ex.defaultJson
    jsonInput.spellcheck = false

    leftPanel.appendChild(leftHeader)
    leftPanel.appendChild(jsonInput)

    // Right panel: Result
    val rightPanel = document.createElement("div").asInstanceOf[html.Div]
    rightPanel.className = "panel"

    val rightHeader = document.createElement("div").asInstanceOf[html.Div]
    rightHeader.className = "panel-header"

    val rightTitle = document.createElement("h3")
    rightTitle.textContent = "Result"

    val encodeBtn = document.createElement("button").asInstanceOf[html.Button]
    encodeBtn.textContent = "← Encode"
    encodeBtn.className = "action-btn encode-btn"

    rightHeader.appendChild(rightTitle)
    rightHeader.appendChild(encodeBtn)

    val resultOutput = document.createElement("pre").asInstanceOf[html.Pre]
    resultOutput.className = "result-output"
    resultOutput.textContent = ex.decode(ex.defaultJson)

    rightPanel.appendChild(rightHeader)
    rightPanel.appendChild(resultOutput)

    panels.appendChild(leftPanel)
    panels.appendChild(rightPanel)
    content.appendChild(panels)

    // Event handlers
    decodeBtn.addEventListener("click", { (_: dom.Event) =>
      val result = ex.decode(jsonInput.value)
      resultOutput.textContent = result
      resultOutput.className = if result.startsWith("✓") then "result-output success" else "result-output error"
    })

    encodeBtn.addEventListener("click", { (_: dom.Event) =>
      val encoded = ex.encode()
      jsonInput.value = encoded
      resultOutput.textContent = ex.decode(encoded)
      resultOutput.className = "result-output success"
    })

    resetBtn.addEventListener("click", { (_: dom.Event) =>
      jsonInput.value = ex.defaultJson
      resultOutput.textContent = ex.decode(ex.defaultJson)
      resultOutput.className = "result-output success"
    })

    // Auto-decode on initial load
    resultOutput.className = "result-output success"

  private def escapeHtml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
