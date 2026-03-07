package sanely.jsoniter

case class JsoniterConfiguration(
  transformMemberNames: String => String = Predef.identity,
  transformConstructorNames: String => String = Predef.identity,
  useDefaults: Boolean = false,
  discriminator: Option[String] = None,
  strictDecoding: Boolean = false,
  dropNullValues: Boolean = false
):
  def withTransformMemberNames(f: String => String): JsoniterConfiguration = copy(transformMemberNames = f)
  def withSnakeCaseMemberNames: JsoniterConfiguration = withTransformMemberNames(JsoniterConfiguration.snakeCase)
  def withTransformConstructorNames(f: String => String): JsoniterConfiguration = copy(transformConstructorNames = f)
  def withDefaults: JsoniterConfiguration = copy(useDefaults = true)
  def withDiscriminator(d: String): JsoniterConfiguration = copy(discriminator = Some(d))
  def withStrictDecoding: JsoniterConfiguration = copy(strictDecoding = true)
  def withDropNullValues: JsoniterConfiguration = copy(dropNullValues = true)

object JsoniterConfiguration:
  val default: JsoniterConfiguration = JsoniterConfiguration()

  private[jsoniter] def snakeCase(s: String): String =
    val sb = new StringBuilder
    var i = 0
    while i < s.length do
      val c = s.charAt(i)
      if c.isUpper then
        if i > 0 then sb.append('_')
        sb.append(c.toLower)
      else
        sb.append(c)
      i += 1
    sb.toString
