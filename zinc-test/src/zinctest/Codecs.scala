package zinctest

import io.circe.*
import io.circe.syntax.*
import sanely.auto.given

object Codecs:
  def encodeUser(u: User): Json = u.asJson
  def decodeUser(j: Json): Either[DecodingFailure, User] = j.as[User]
  def encodeColor(c: Color): Json = c.asJson
  def decodeColor(j: Json): Either[DecodingFailure, Color] = j.as[Color]
  def encodeAddress(a: Address): Json = a.asJson
  def decodeAddress(j: Json): Either[DecodingFailure, Address] = j.as[Address]
