package org.classflow.automation

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** Strict subset of JSON Schema used by the checked-in Android tool contract. */
class AutomationContract(source: String) {
    private val root = JSONObject(source)
    val tools: JSONArray get() = root.getJSONArray("tools")
    fun schema(name: String): JSONObject = (0 until tools.length())
        .map { tools.getJSONObject(it) }.firstOrNull { it.getString("name") == name }
        ?.getJSONObject("inputSchema") ?: throw IllegalArgumentException("未知工具")
    fun fields(kind: String): JSONObject = root.getJSONObject(if (kind == "courses") "courseFields" else "eventFields")

    fun validate(value: Any, schema: JSONObject, path: String = "arguments") {
        val type = schema.get("type")
        val types = if (type is JSONArray) (0 until type.length()).map { type.getString(it) } else listOf(type.toString())
        val matches = types.any {
            when (it) {
                "null" -> value === JSONObject.NULL
                "object" -> value is JSONObject
                "array" -> value is JSONArray
                "string" -> value is String
                "boolean" -> value is Boolean
                "number" -> value is Number && value.toDouble().isFinite()
                "integer" -> value is Number && value.toDouble().isFinite() && value.toDouble() % 1.0 == 0.0
                else -> false
            }
        }
        require(matches) { "$path 类型错误" }
        if (value === JSONObject.NULL) return
        schema.optJSONArray("enum")?.let { choices ->
            require((0 until choices.length()).any { choices.get(it) == value }) { "$path 不支持此值" }
        }
        when (value) {
            is JSONObject -> {
                val fields = schema.getJSONObject("properties")
                value.keys().forEach { key ->
                    require(fields.has(key)) { "$path 包含未支持的字段" }
                    validate(value.get(key), fields.getJSONObject(key), "$path.$key")
                }
                schema.optJSONArray("required")?.let { required ->
                    for (i in 0 until required.length()) require(value.has(required.getString(i))) { "$path 缺少 ${required.getString(i)}" }
                }
            }
            is JSONArray -> {
                require(value.length() in schema.optInt("minItems", 0)..schema.optInt("maxItems", 10000)) { "$path 数量超限" }
                for (i in 0 until value.length()) validate(value.get(i), schema.getJSONObject("items"), "$path[$i]")
            }
            is Number -> require(value.toDouble() >= schema.optDouble("minimum", -Double.MAX_VALUE) &&
                value.toDouble() <= schema.optDouble("maximum", Double.MAX_VALUE)) { "$path 超出范围" }
            is String -> {
                require(value.length in schema.optInt("minLength", 0)..schema.optInt("maxLength", 16000)) { "$path 长度错误" }
                if (schema.has("pattern")) require(Regex(schema.getString("pattern")).matches(value)) { "$path 格式错误" }
                try {
                    when (schema.optString("format")) {
                        "date" -> { require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)); LocalDate.parse(value) }
                        "time" -> { require(Regex("\\d{2}:\\d{2}").matches(value)); LocalTime.parse(value) }
                    }
                } catch (_: Exception) { throw IllegalArgumentException("$path 日期或时间无效") }
            }
        }
    }
}
