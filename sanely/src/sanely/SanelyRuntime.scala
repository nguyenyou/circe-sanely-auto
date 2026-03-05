package sanely

import cats.data.{NonEmptyList, Validated}
import io.circe.{ACursor, Decoder, DecodingFailure, Encoder, HCursor, Json, JsonObject}

/** Runtime utility methods called by macro-generated code.
  * Reduces generated AST size by moving common patterns out of inline expansions.
  *
  * This is an internal implementation detail — not part of the public API.
  */
object SanelyRuntime:

  // === Encoder helpers ===

  /** Encode a sum type variant using ordinal-based dispatch (non-configured, external tagging). */
  def encodeSum(a: Any, ord: Int, labels: Array[String], encoders: Array[Encoder[Any]], isSubTrait: Array[Boolean]): JsonObject =
    if isSubTrait(ord) then
      encoders(ord).asInstanceOf[Encoder.AsObject[Any]].encodeObject(a)
    else
      JsonObject.singleton(labels(ord), encoders(ord)(a))

  /** Decode a sum type variant (non-configured, external tagging). */
  def decodeSum[S](c: HCursor, key: String, labels: Array[String], decoders: Array[Decoder[Any]], isSubTrait: Array[Boolean]): Decoder.Result[S] =
    var i = 0
    while i < labels.length do
      if isSubTrait(i) then
        decoders(i).tryDecode(c) match
          case Right(v) => return Right(v.asInstanceOf[S])
          case Left(_) =>
      else
        if key == labels(i) then
          return decoders(i).tryDecode(c.downField(labels(i))).asInstanceOf[Decoder.Result[S]]
      i += 1
    Left(DecodingFailure("Unknown variant", c.history))

  /** Encode a sum type variant with configured tagging (discriminator or external). */
  def encodeSumVariant(encoded: Json, label: String, discriminator: Option[String]): JsonObject =
    discriminator match
      case None =>
        JsonObject.singleton(label, encoded)
      case Some(discr) =>
        val base = encoded.asObject.getOrElse(JsonObject.empty)
        base.add(discr, Json.fromString(label))

  /** Encode a product's fields into a JsonObject using runtime arrays.
    * Reduces macro-generated AST from N nested .add() calls to flat arrays + 1 call.
    */
  def encodeProductFields(a: Product, names: Array[String], encoders: Array[Encoder[Any]]): JsonObject =
    var obj = JsonObject.empty
    var i = 0
    while i < names.length do
      obj = obj.add(names(i), encoders(i)(a.productElement(i)))
      i += 1
    obj

  /** Encode a sum type variant using ordinal-based dispatch with runtime arrays. */
  def encodeSumConfigured(
    a: Any, ord: Int, labels: Array[String],
    encoders: Array[Encoder[Any]], isSubTrait: Array[Boolean],
    transformConstructorNames: String => String, discriminator: Option[String]
  ): JsonObject =
    if isSubTrait(ord) then
      encoders(ord).asInstanceOf[Encoder.AsObject[Any]].encodeObject(a)
    else
      val encoded = encoders(ord)(a)
      val transformedLabel = transformConstructorNames(labels(ord))
      encodeSumVariant(encoded, transformedLabel, discriminator)

  // === Decoder helpers ===

  /** Decode a product's fields using runtime arrays (non-configured, no defaults). */
  def decodeProductFields[P](
    c: HCursor, mirror: scala.deriving.Mirror.ProductOf[P],
    fieldNames: Array[String], decoders: Array[Decoder[Any]]
  ): Decoder.Result[P] =
    val n = fieldNames.length
    val results = new Array[Any](n)
    var i = 0
    while i < n do
      decoders(i).tryDecode(c.downField(fieldNames(i))) match
        case Right(v) => results(i) = v
        case Left(e) => return Left(e)
      i += 1
    Right(mirror.fromProduct(new ArrayProduct(results)))

  /** Lightweight Product wrapper over an Array for use with Mirror.fromProduct. */
  private final class ArrayProduct(val arr: Array[Any]) extends Product:
    def canEqual(that: Any): Boolean = true
    def productArity: Int = arr.length
    def productElement(n: Int): Any = arr(n)

  /** Decode a product's fields using runtime arrays.
    * Reduces macro-generated AST from N-deep nested match expressions to flat arrays + 1 call.
    */
  def decodeProductFieldsConfigured[P](
    c: HCursor, mirror: scala.deriving.Mirror.ProductOf[P],
    fieldNames: Array[String], decoders: Array[Decoder[Any]],
    hasDefault: Array[Boolean], defaults: Array[Any],
    isOption: Array[Boolean], useDefaults: Boolean
  ): Decoder.Result[P] =
    val n = fieldNames.length
    val results = new Array[Any](n)
    var i = 0
    while i < n do
      val fieldResult: Decoder.Result[Any] =
        if hasDefault(i) then
          if isOption(i) then
            tryDecodeOptionFieldWithDefault(c, fieldNames(i), decoders(i), useDefaults, defaults(i))
          else
            tryDecodeFieldWithDefault(c, fieldNames(i), decoders(i), useDefaults, defaults(i))
        else
          decoders(i).tryDecode(c.downField(fieldNames(i)))
      fieldResult match
        case Right(v) => results(i) = v
        case Left(e) => return Left(e)
      i += 1
    Right(mirror.fromProduct(new ArrayProduct(results)))

  /** Decode a sum type variant using array-based dispatch.
    * Sub-traits are tried on the full cursor; direct variants match by key equality.
    */
  def decodeSumConfigured[S](
    c: HCursor, matchValue: String, decodeCursor: ACursor,
    labels: Array[String], decoders: Array[Decoder[Any]], isSubTrait: Array[Boolean],
    transformConstructorNames: String => String, discriminator: Option[String]
  ): Decoder.Result[S] =
    // Try sub-traits first (they need to attempt decode on full cursor)
    // Then try direct variants (key equality match)
    // Order: iterate forward, sub-traits try-and-fallback, direct variants exact-match
    var i = 0
    while i < labels.length do
      if isSubTrait(i) then
        decoders(i).tryDecode(c) match
          case Right(v) => return Right(v.asInstanceOf[S])
          case Left(_) => // continue to next
      else
        if matchValue == transformConstructorNames(labels(i)) then
          return decoders(i).tryDecode(decodeCursor).asInstanceOf[Decoder.Result[S]]
      i += 1
    discriminator match
      case Some(_) => Left(DecodingFailure("Unknown variant: " + matchValue, c.history))
      case None    => Left(DecodingFailure("Unknown variant", c.history))

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

  // === Accumulating decoder helpers ===

  /** Accumulating decode for a product's fields (non-configured, no defaults).
    * Collects ALL field errors instead of short-circuiting on the first.
    */
  def decodeProductFieldsAccumulating[P](
    c: HCursor, mirror: scala.deriving.Mirror.ProductOf[P],
    fieldNames: Array[String], decoders: Array[Decoder[Any]]
  ): Decoder.AccumulatingResult[P] =
    if !c.value.isObject then
      return Validated.invalidNel(DecodingFailure("Expected JSON object for product type", c.history))
    val n = fieldNames.length
    val results = new Array[Any](n)
    val failed = List.newBuilder[DecodingFailure]
    var i = 0
    while i < n do
      decoders(i).tryDecodeAccumulating(c.downField(fieldNames(i))) match
        case Validated.Valid(v) => results(i) = v
        case Validated.Invalid(errs) => failed ++= errs.toList
      i += 1
    val errors = failed.result()
    if errors.isEmpty then Validated.valid(mirror.fromProduct(new ArrayProduct(results)))
    else Validated.invalid(NonEmptyList.fromListUnsafe(errors))

  /** Accumulating decode for a sum type (non-configured, external tagging). */
  def decodeSumAccumulating[S](
    c: HCursor, key: String, labels: Array[String],
    decoders: Array[Decoder[Any]], isSubTrait: Array[Boolean]
  ): Decoder.AccumulatingResult[S] =
    var i = 0
    while i < labels.length do
      if isSubTrait(i) then
        decoders(i).tryDecodeAccumulating(c) match
          case v @ Validated.Valid(_) => return v.asInstanceOf[Decoder.AccumulatingResult[S]]
          case Validated.Invalid(_) =>
      else
        if key == labels(i) then
          return decoders(i).tryDecodeAccumulating(c.downField(labels(i))).asInstanceOf[Decoder.AccumulatingResult[S]]
      i += 1
    Validated.invalidNel(DecodingFailure("Unknown variant", c.history))

  /** Accumulating decode for a product's fields (configured, with defaults).
    * Collects ALL field errors plus optional strict decoding errors.
    */
  def decodeProductFieldsConfiguredAccumulating[P](
    c: HCursor, mirror: scala.deriving.Mirror.ProductOf[P],
    fieldNames: Array[String], decoders: Array[Decoder[Any]],
    hasDefault: Array[Boolean], defaults: Array[Any],
    isOption: Array[Boolean], useDefaults: Boolean,
    extraErrors: List[DecodingFailure]
  ): Decoder.AccumulatingResult[P] =
    val n = fieldNames.length
    val results = new Array[Any](n)
    val failed = List.newBuilder[DecodingFailure]
    failed ++= extraErrors
    var i = 0
    while i < n do
      val fieldResult: Decoder.AccumulatingResult[Any] =
        if hasDefault(i) then
          if isOption(i) then
            tryDecodeOptionFieldWithDefaultAccumulating(c, fieldNames(i), decoders(i), useDefaults, defaults(i))
          else
            tryDecodeFieldWithDefaultAccumulating(c, fieldNames(i), decoders(i), useDefaults, defaults(i))
        else
          decoders(i).tryDecodeAccumulating(c.downField(fieldNames(i)))
      fieldResult match
        case Validated.Valid(v) => results(i) = v
        case Validated.Invalid(errs) => failed ++= errs.toList
      i += 1
    val errors = failed.result()
    if errors.isEmpty then Validated.valid(mirror.fromProduct(new ArrayProduct(results)))
    else Validated.invalid(NonEmptyList.fromListUnsafe(errors))

  /** Accumulating decode for a sum type (configured, discriminator or external tagging). */
  def decodeSumConfiguredAccumulating[S](
    c: HCursor, matchValue: String, decodeCursor: ACursor,
    labels: Array[String], decoders: Array[Decoder[Any]], isSubTrait: Array[Boolean],
    transformConstructorNames: String => String, discriminator: Option[String]
  ): Decoder.AccumulatingResult[S] =
    var i = 0
    while i < labels.length do
      if isSubTrait(i) then
        decoders(i).tryDecodeAccumulating(c) match
          case v @ Validated.Valid(_) => return v.asInstanceOf[Decoder.AccumulatingResult[S]]
          case Validated.Invalid(_) =>
      else
        if matchValue == transformConstructorNames(labels(i)) then
          return decoders(i).tryDecodeAccumulating(decodeCursor).asInstanceOf[Decoder.AccumulatingResult[S]]
      i += 1
    discriminator match
      case Some(_) => Validated.invalidNel(DecodingFailure("Unknown variant: " + matchValue, c.history))
      case None    => Validated.invalidNel(DecodingFailure("Unknown variant", c.history))

  /** Accumulating version of tryDecodeFieldWithDefault. */
  def tryDecodeFieldWithDefaultAccumulating[T](
    c: HCursor, fieldName: String, dec: Decoder[T],
    useDefaults: Boolean, default: T
  ): Decoder.AccumulatingResult[T] =
    val cursor = c.downField(fieldName)
    if useDefaults && (cursor.failed || cursor.focus.exists(_.isNull)) then Validated.valid(default)
    else dec.tryDecodeAccumulating(cursor)

  /** Accumulating version of tryDecodeOptionFieldWithDefault. */
  def tryDecodeOptionFieldWithDefaultAccumulating[T](
    c: HCursor, fieldName: String, dec: Decoder[T],
    useDefaults: Boolean, default: T
  ): Decoder.AccumulatingResult[T] =
    val cursor = c.downField(fieldName)
    if useDefaults && cursor.failed then Validated.valid(default)
    else dec.tryDecodeAccumulating(cursor)
