package sanely

import io.circe.{ACursor, Decoder, DecodingFailure, HCursor, Json, JsonObject}

/** Runtime utility methods called by macro-generated code.
  * Reduces generated AST size by moving common patterns out of inline expansions.
  *
  * This is an internal implementation detail — not part of the public API.
  */
object SanelyRuntime:

  // === Encoder helpers ===

  /** Encode a sum type variant with configured tagging (discriminator or external). */
  def encodeSumVariant(encoded: Json, label: String, discriminator: Option[String]): JsonObject =
    discriminator match
      case None =>
        JsonObject.singleton(label, encoded)
      case Some(discr) =>
        val base = encoded.asObject.getOrElse(JsonObject.empty)
        base.add(discr, Json.fromString(label))

  // === Decoder helpers ===

  /** Validate strict decoding: reject unexpected fields in the JSON object. */
  def checkStrictDecoding(c: HCursor, expectedKeys: Set[String]): Decoder.Result[Unit] =
    c.keys match
      case Some(keys) =>
        val unexpected = keys.filterNot(expectedKeys.contains).toList
        if unexpected.nonEmpty then
          Left(DecodingFailure(s"Unexpected field(s): ${unexpected.mkString(", ")}", c.history))
        else Right(())
      case None => Right(())

  /** Extract sum type key and decode cursor from JSON.
    * Handles both discriminator-based and external tagging.
    */
  def extractSumTypeInfo(
    c: HCursor,
    discriminator: Option[String],
    transformedLabels: Set[String],
    strictDecoding: Boolean
  ): Either[DecodingFailure, (String, ACursor)] =
    discriminator match
      case Some(discField) =>
        c.downField(discField).as[String] match
          case Right(typeName) => Right((typeName, c))
          case Left(_) => Left(DecodingFailure(s"Missing discriminator field '$discField'", c.history))
      case None =>
        c.keys match
          case Some(keys) =>
            val keysList = keys.toList
            if strictDecoding && keysList.size > 1 then
              Left(DecodingFailure("Expected single-key JSON object for sum type", c.history))
            else
              val key = keysList.find(transformedLabels.contains).getOrElse("")
              Right((key, c.downField(key)))
          case None =>
            Left(DecodingFailure("Expected JSON object for sum type", c.history))

  /** Decode a field with default value support (for non-Option types).
    * When useDefaults is true and the field is missing or null, returns the default.
    */
  def tryDecodeFieldWithDefault[T](
    c: HCursor, fieldName: String, dec: Decoder[T],
    useDefaults: Boolean, default: T
  ): Decoder.Result[T] =
    val cursor = c.downField(fieldName)
    if useDefaults && (cursor.failed || cursor.focus.exists(_.isNull)) then Right(default)
    else dec.tryDecode(cursor)

  /** Decode an Option field with default value support.
    * Unlike tryDecodeFieldWithDefault, null does NOT trigger default (decodes as None).
    * Only a missing field triggers the default.
    */
  def tryDecodeOptionFieldWithDefault[T](
    c: HCursor, fieldName: String, dec: Decoder[T],
    useDefaults: Boolean, default: T
  ): Decoder.Result[T] =
    val cursor = c.downField(fieldName)
    if useDefaults && cursor.failed then Right(default)
    else dec.tryDecode(cursor)
