package zinctest

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*

object Main:
  def main(args: Array[String]): Unit =
    // Encode Address
    val addr = Address("123 Main St", "Springfield")
    val addrJson = Codecs.encodeAddress(addr)
    println(s"ADDR_ENC:${addrJson.noSpaces}")

    // Roundtrip Address
    val addrBack = Codecs.decodeAddress(addrJson)
    println(s"ADDR_DEC:${addrBack.map(_.toString).getOrElse("FAIL")}")

    // Encode User (contains nested Address)
    val user = User("Alice", 30, addr)
    val userJson = Codecs.encodeUser(user)
    println(s"USER_ENC:${userJson.noSpaces}")

    // Roundtrip User
    val userBack = Codecs.decodeUser(userJson)
    println(s"USER_DEC:${userBack.map(_.toString).getOrElse("FAIL")}")

    // Encode Color enum
    val color: Color = Color.Red
    val colorJson = Codecs.encodeColor(color)
    println(s"COLOR_ENC:${colorJson.noSpaces}")

    // Roundtrip Color
    val colorBack = Codecs.decodeColor(colorJson)
    println(s"COLOR_DEC:${colorBack.map(_.toString).getOrElse("FAIL")}")
