package com.viewcompose.navigation.core

import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap

/**
 * deep link 占位参数的目标类型。
 * Target type for a deep-link placeholder argument.
 */
enum class NavDeepLinkArgumentType {
    Text,
    Int,
    Long,
    Boolean,
    Float,
    Double,
}

/**
 * deep link 命中后对目标 stack 的打开方式。
 * Launch mode applied to the target stack after a deep link matches.
 */
enum class NavDeepLinkLaunchMode {
    Push,
    SingleTop,
    ReplaceTop,
    Reset,
}

/**
 * 图节点允许匹配的一个 URI pattern。
 * One allowlisted URI pattern for a graph node.
 *
 * 占位符必须占据完整 path segment 或 query value，例如 `https://example.com/users/{userId}?source={source}`。
 * Placeholders must occupy a complete path segment or query value, for example `https://example.com/users/{userId}?source={source}`.
 *
 * 占位符默认按 [NavDeepLinkArgumentType.Text] 解析。
 * Placeholder values default to [NavDeepLinkArgumentType.Text].
 */
class NavDeepLink(
    val uriPattern: String,
    argumentTypes: Map<String, NavDeepLinkArgumentType> = emptyMap(),
    val targetStackId: NavStackId? = null,
) {
    val argumentTypes: Map<String, NavDeepLinkArgumentType> = Collections.unmodifiableMap(
        LinkedHashMap(argumentTypes),
    )

    internal val compiled: CompiledNavDeepLink = compileDeepLink(
        uriPattern = uriPattern,
        argumentTypes = this.argumentTypes,
    )

    override fun equals(other: Any?): Boolean {
        return other is NavDeepLink &&
            uriPattern == other.uriPattern &&
            argumentTypes == other.argumentTypes &&
            targetStackId == other.targetStackId
    }

    override fun hashCode(): Int {
        var result = uriPattern.hashCode()
        result = 31 * result + argumentTypes.hashCode()
        result = 31 * result + (targetStackId?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "NavDeepLink(" +
            "uriPattern=$uriPattern, " +
            "argumentTypes=$argumentTypes, " +
            "targetStackId=$targetStackId" +
            ")"
    }
}

/**
 * deep link 成功匹配后的 route 输出。
 * Route output produced by a successful deep-link match.
 */
data class NavDeepLinkMatch(
    val deepLink: NavDeepLink,
    val route: NavRoute,
)

/**
 * deep link 被拒绝的可诊断原因。
 * Diagnostic reason why a deep link was rejected.
 */
enum class NavDeepLinkRejectionReason {
    MalformedUri,
    InvalidArgument,
    AmbiguousMatch,
}

/**
 * deep link 拒绝详情，包含失败参数或冲突 pattern。
 * Deep-link rejection details, including the failed argument or conflicting patterns.
 */
class NavDeepLinkRejection(
    val reason: NavDeepLinkRejectionReason,
    val argumentName: String? = null,
    matchingPatterns: List<String> = emptyList(),
) {
    val matchingPatterns: List<String> = Collections.unmodifiableList(
        ArrayList(matchingPatterns),
    )

    override fun equals(other: Any?): Boolean {
        return other is NavDeepLinkRejection &&
            reason == other.reason &&
            argumentName == other.argumentName &&
            matchingPatterns == other.matchingPatterns
    }

    override fun hashCode(): Int {
        var result = reason.hashCode()
        result = 31 * result + (argumentName?.hashCode() ?: 0)
        result = 31 * result + matchingPatterns.hashCode()
        return result
    }

    override fun toString(): String {
        return "NavDeepLinkRejection(" +
            "reason=$reason, " +
            "argumentName=$argumentName, " +
            "matchingPatterns=$matchingPatterns" +
            ")"
    }
}

/**
 * deep link 解析结果。
 * Result of resolving a deep link.
 */
sealed interface NavDeepLinkResolution {
    data class Matched(
        val match: NavDeepLinkMatch,
    ) : NavDeepLinkResolution

    data object NoMatch : NavDeepLinkResolution

    data class Rejected(
        val rejection: NavDeepLinkRejection,
    ) : NavDeepLinkResolution

    /**
     * controller 创建时没有绑定导航图。
     * The controller was created without a navigation graph.
     */
    data object Unsupported : NavDeepLinkResolution
}

internal data class NavDeepLinkTarget(
    val routeName: String,
    val deepLink: NavDeepLink,
)

internal fun resolveDeepLinkTargets(
    uri: String,
    targets: List<NavDeepLinkTarget>,
): NavDeepLinkResolution {
    val input = parseInputUri(uri)
        ?: return NavDeepLinkResolution.Rejected(
            NavDeepLinkRejection(NavDeepLinkRejectionReason.MalformedUri),
        )
    val matches = mutableListOf<RankedDeepLinkMatch>()
    val invalidArguments = mutableListOf<InvalidDeepLinkArgument>()
    targets.forEach { target ->
        when (val candidate = target.deepLink.compiled.match(input)) {
            is CompiledMatch.Matched -> {
                matches += RankedDeepLinkMatch(
                    match = NavDeepLinkMatch(
                        deepLink = target.deepLink,
                        route = NavRoute(
                            name = target.routeName,
                            arguments = candidate.arguments,
                        ),
                    ),
                    score = candidate.score,
                )
            }

            is CompiledMatch.InvalidArgument -> {
                invalidArguments += InvalidDeepLinkArgument(
                    name = candidate.name,
                    score = candidate.score,
                    pattern = target.deepLink.uriPattern,
                )
            }

            CompiledMatch.NoMatch -> Unit
        }
    }
    if (matches.isEmpty()) {
        // 没有成功匹配时，返回最具体的参数失败；完全无匹配才返回 NoMatch。
        // With no successful match, report the most specific argument failure; return NoMatch only when nothing matched.
        val invalid = invalidArguments.maxByOrNull(InvalidDeepLinkArgument::score)
        return if (invalid == null) {
            NavDeepLinkResolution.NoMatch
        } else {
            NavDeepLinkResolution.Rejected(
                NavDeepLinkRejection(
                    reason = NavDeepLinkRejectionReason.InvalidArgument,
                    argumentName = invalid.name,
                ),
            )
        }
    }
    val highestScore = matches.maxOf(RankedDeepLinkMatch::score)
    val highestInvalidScore = invalidArguments.maxOfOrNull(InvalidDeepLinkArgument::score)
    if (highestInvalidScore != null && highestInvalidScore >= highestScore) {
        // 参数解析失败的 pattern 与成功 pattern 同等或更具体时，优先暴露参数错误。
        // If an invalid-argument pattern is as specific as or more specific than a successful match, expose that argument error.
        val invalid = invalidArguments.first { candidate ->
            candidate.score == highestInvalidScore
        }
        return NavDeepLinkResolution.Rejected(
            NavDeepLinkRejection(
                reason = NavDeepLinkRejectionReason.InvalidArgument,
                argumentName = invalid.name,
                matchingPatterns = invalidArguments
                    .filter { candidate -> candidate.score == highestInvalidScore }
                    .map(InvalidDeepLinkArgument::pattern),
            ),
        )
    }
    val bestMatches = matches.filter { match -> match.score == highestScore }
    if (bestMatches.size != 1) {
        // 同分最高命中无法安全选择 route，交给调用方处理歧义。
        // Tied top matches cannot safely choose a route, so surface ambiguity to the caller.
        return NavDeepLinkResolution.Rejected(
            NavDeepLinkRejection(
                reason = NavDeepLinkRejectionReason.AmbiguousMatch,
                matchingPatterns = bestMatches.map { match ->
                    match.match.deepLink.uriPattern
                },
            ),
        )
    }
    return NavDeepLinkResolution.Matched(bestMatches.single().match)
}

/**
 * 已编译的 deep-link pattern。
 * Compiled deep-link pattern.
 */
internal class CompiledNavDeepLink(
    private val scheme: String,
    private val host: String?,
    private val port: Int,
    private val path: List<ComponentPattern>,
    private val query: Map<String, ComponentPattern>,
    private val argumentTypes: Map<String, NavDeepLinkArgumentType>,
) {
    fun match(input: ParsedInputUri): CompiledMatch {
        if (!scheme.equals(input.scheme, ignoreCase = true)) {
            return CompiledMatch.NoMatch
        }
        if (!hostsMatch(host, input.host) || port != input.port) {
            return CompiledMatch.NoMatch
        }
        if (path.size != input.path.size) {
            return CompiledMatch.NoMatch
        }
        val arguments = linkedMapOf<String, NavValue>()
        path.forEachIndexed { index, pattern ->
            when (
                val result = pattern.match(
                    value = input.path[index],
                    argumentTypes = argumentTypes,
                )
            ) {
                is ComponentMatch.Argument -> arguments[result.name] = result.value
                is ComponentMatch.InvalidArgument -> {
                    return CompiledMatch.InvalidArgument(
                        name = result.name,
                        score = specificityScore(),
                    )
                }

                ComponentMatch.NoMatch -> return CompiledMatch.NoMatch
                ComponentMatch.Static -> Unit
            }
        }
        query.forEach { (name, pattern) ->
            val values = input.query[name] ?: return CompiledMatch.NoMatch
            if (values.size != 1) {
                return CompiledMatch.InvalidArgument(
                    name = (pattern as? ComponentPattern.Argument)?.name ?: name,
                    score = specificityScore(),
                )
            }
            when (
                val result = pattern.match(
                    value = values.single(),
                    argumentTypes = argumentTypes,
                )
            ) {
                is ComponentMatch.Argument -> arguments[result.name] = result.value
                is ComponentMatch.InvalidArgument -> {
                    return CompiledMatch.InvalidArgument(
                        name = result.name,
                        score = specificityScore(),
                    )
                }

                ComponentMatch.NoMatch -> return CompiledMatch.NoMatch
                ComponentMatch.Static -> Unit
            }
        }
        return CompiledMatch.Matched(
            arguments = arguments,
            score = specificityScore(),
        )
    }

    private fun specificityScore(): Int {
        // 静态 path segment 权重最高，query 次之；参数 segment 仍比完全缺省更具体。
        // Static path segments carry the highest weight, query is next, and argument segments still add specificity.
        val pathScore = path.fold(0) { score, component ->
            when (component) {
                is ComponentPattern.Static -> score + 100
                is ComponentPattern.Argument -> score + 10
            }
        }
        val queryScore = query.values.fold(0) { score, component ->
            when (component) {
                is ComponentPattern.Static -> score + 20
                is ComponentPattern.Argument -> score + 5
            }
        }
        return pathScore + queryScore
    }
}

/**
 * URI path/query component 的匹配模式。
 * Match pattern for one URI path or query component.
 */
internal sealed interface ComponentPattern {
    data class Static(
        val value: String,
    ) : ComponentPattern

    data class Argument(
        val name: String,
    ) : ComponentPattern

    fun match(
        value: String,
        argumentTypes: Map<String, NavDeepLinkArgumentType>,
    ): ComponentMatch {
        return when (this) {
            is Static -> {
                if (this.value == value) ComponentMatch.Static else ComponentMatch.NoMatch
            }

            is Argument -> {
                val parsed = argumentTypes.getValue(name).parse(value)
                if (parsed == null) {
                    ComponentMatch.InvalidArgument(name)
                } else {
                    ComponentMatch.Argument(
                        name = name,
                        value = parsed,
                    )
                }
            }
        }
    }
}

internal sealed interface ComponentMatch {
    data object Static : ComponentMatch
    data class Argument(val name: String, val value: NavValue) : ComponentMatch
    data class InvalidArgument(val name: String) : ComponentMatch
    data object NoMatch : ComponentMatch
}

internal sealed interface CompiledMatch {
    data class Matched(
        val arguments: Map<String, NavValue>,
        val score: Int,
    ) : CompiledMatch

    data class InvalidArgument(
        val name: String,
        val score: Int,
    ) : CompiledMatch

    data object NoMatch : CompiledMatch
}

internal data class ParsedInputUri(
    val scheme: String,
    val host: String?,
    val port: Int,
    val path: List<String>,
    val query: Map<String, List<String>>,
)

private data class RankedDeepLinkMatch(
    val match: NavDeepLinkMatch,
    val score: Int,
)

private data class InvalidDeepLinkArgument(
    val name: String,
    val score: Int,
    val pattern: String,
)

private fun compileDeepLink(
    uriPattern: String,
    argumentTypes: Map<String, NavDeepLinkArgumentType>,
): CompiledNavDeepLink {
    require(uriPattern.isNotBlank()) {
        "Navigation deep-link URI pattern must not be blank."
    }
    val placeholderNames = mutableListOf<String>()
    var placeholderIndex = 0
    val sanitizedPattern = PlaceholderRegex.replace(uriPattern) { match ->
        val name = match.groupValues[1]
        placeholderNames += name
        placeholderToken(placeholderIndex++)
    }
    require(!sanitizedPattern.contains('{') && !sanitizedPattern.contains('}')) {
        "Navigation deep-link pattern contains an invalid placeholder: '$uriPattern'."
    }
    require(placeholderNames.distinct().size == placeholderNames.size) {
        "Navigation deep-link placeholder names must be unique: '$uriPattern'."
    }
    require(argumentTypes.keys.all(placeholderNames::contains)) {
        "Navigation deep-link argument types must reference declared placeholders."
    }
    val parsed = parseUri(sanitizedPattern)
        ?: throw IllegalArgumentException(
            "Navigation deep-link pattern is not a valid absolute hierarchical URI: '$uriPattern'.",
        )
    require(parsed.rawFragment == null) {
        "Navigation deep-link patterns must not contain URI fragments."
    }
    val tokenToName = placeholderNames.mapIndexed { index, name ->
        placeholderToken(index) to name
    }.toMap()
    require(
        tokenToName.keys.none { token ->
            parsed.scheme.contains(token) ||
                parsed.rawAuthority?.contains(token) == true
        },
    ) {
        "Navigation deep-link placeholders are supported only in path segments and query values."
    }
    val path = rawPathSegments(parsed.rawPath).map { rawSegment ->
        componentPattern(
            rawValue = rawSegment,
            tokenToName = tokenToName,
            uriPattern = uriPattern,
        )
    }
    val query = parseRawQuery(parsed.rawQuery)
        ?: throw IllegalArgumentException(
            "Navigation deep-link query is malformed: '$uriPattern'.",
        )
    require(
        query.keys.none { rawName ->
            tokenToName.keys.any(rawName::contains)
        },
    ) {
        "Navigation deep-link query names cannot be placeholders."
    }
    val queryPatterns = LinkedHashMap<String, ComponentPattern>()
    query.forEach { (rawName, rawValues) ->
        require(rawValues.size == 1) {
            "Navigation deep-link query names must be unique: '$uriPattern'."
        }
        val name = strictPercentDecode(rawName)
            ?: throw IllegalArgumentException(
                "Navigation deep-link query name is malformed: '$uriPattern'.",
            )
        require(name.isNotBlank() && queryPatterns.put(
            name,
            componentPattern(
                rawValue = rawValues.single(),
                tokenToName = tokenToName,
                uriPattern = uriPattern,
            ),
        ) == null) {
            "Navigation deep-link query names must be non-blank and unique: '$uriPattern'."
        }
    }
    val resolvedArgumentTypes = placeholderNames.associateWith { name ->
        argumentTypes[name] ?: NavDeepLinkArgumentType.Text
    }
    return CompiledNavDeepLink(
        scheme = parsed.scheme,
        host = parsed.host,
        port = parsed.port,
        path = path,
        query = queryPatterns,
        argumentTypes = resolvedArgumentTypes,
    )
}

private fun componentPattern(
    rawValue: String,
    tokenToName: Map<String, String>,
    uriPattern: String,
): ComponentPattern {
    tokenToName[rawValue]?.let { name ->
        return ComponentPattern.Argument(name)
    }
    require(tokenToName.keys.none(rawValue::contains)) {
        "Navigation deep-link placeholders must occupy a complete path segment or query value: " +
            "'$uriPattern'."
    }
    return ComponentPattern.Static(
        strictPercentDecode(rawValue)
            ?: throw IllegalArgumentException(
                "Navigation deep-link component is malformed: '$uriPattern'.",
            ),
    )
}

/**
 * 严格解析输入 URI，拒绝 fragment、控制字符和非法 percent encoding。
 * Strictly parses input URIs, rejecting fragments, control characters, and invalid percent encoding.
 */
private fun parseInputUri(uri: String): ParsedInputUri? {
    val parsed = parseUri(uri) ?: return null
    if (parsed.rawFragment != null) {
        return null
    }
    val path = rawPathSegments(parsed.rawPath).map { segment ->
        strictPercentDecode(segment) ?: return null
    }
    val rawQuery = parseRawQuery(parsed.rawQuery) ?: return null
    val query = LinkedHashMap<String, MutableList<String>>()
    rawQuery.forEach { (rawName, rawValues) ->
        val name = strictPercentDecode(rawName) ?: return null
        if (name.isBlank()) {
            return null
        }
        val values = query.getOrPut(name) { mutableListOf() }
        rawValues.forEach { rawValue ->
            values += strictPercentDecode(rawValue) ?: return null
        }
    }
    return ParsedInputUri(
        scheme = parsed.scheme,
        host = parsed.host,
        port = parsed.port,
        path = path,
        query = query.mapValues { (_, values) -> values.toList() },
    )
}

private fun parseUri(value: String): URI? {
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    if (
        !uri.isAbsolute ||
        uri.isOpaque ||
        uri.scheme.isNullOrBlank() ||
        uri.rawUserInfo != null
    ) {
        return null
    }
    if (uri.rawAuthority != null && uri.host == null) {
        return null
    }
    return uri
}

private fun parseRawQuery(rawQuery: String?): Map<String, List<String>>? {
    if (rawQuery == null) {
        return emptyMap()
    }
    if (rawQuery.isEmpty()) {
        return emptyMap()
    }
    val result = LinkedHashMap<String, MutableList<String>>()
    rawQuery.split('&').forEach { pair ->
        if (pair.isEmpty()) {
            return null
        }
        val separatorIndex = pair.indexOf('=')
        val name = if (separatorIndex < 0) pair else pair.substring(0, separatorIndex)
        val value = if (separatorIndex < 0) "" else pair.substring(separatorIndex + 1)
        result.getOrPut(name) { mutableListOf() } += value
    }
    return result.mapValues { (_, values) -> values.toList() }
}

private fun rawPathSegments(rawPath: String): List<String> {
    if (rawPath.isEmpty()) {
        return emptyList()
    }
    check(rawPath.startsWith('/'))
    return rawPath.substring(1).split('/')
}

private fun strictPercentDecode(rawValue: String): String? {
    // 先按 percent encoding 组装字节，再用 REPORT 模式校验 UTF-8，避免容错解码吞掉坏输入。
    // Assemble bytes from percent encoding first, then validate UTF-8 with REPORT mode so malformed input is not tolerated.
    val bytes = ByteArrayOutputStream(rawValue.length)
    var index = 0
    while (index < rawValue.length) {
        val character = rawValue[index]
        if (character == '%') {
            if (index + 2 >= rawValue.length) {
                return null
            }
            val high = rawValue[index + 1].digitToIntOrNull(16) ?: return null
            val low = rawValue[index + 2].digitToIntOrNull(16) ?: return null
            bytes.write((high shl 4) or low)
            index += 3
        } else {
            val codePoint = rawValue.codePointAt(index)
            bytes.write(String(Character.toChars(codePoint)).toByteArray(StandardCharsets.UTF_8))
            index += Character.charCount(codePoint)
        }
    }
    val decoded = runCatching {
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes.toByteArray()))
            .toString()
    }.getOrNull() ?: return null
    return decoded.takeIf { value -> value.none(Char::isISOControl) }
}

private fun NavDeepLinkArgumentType.parse(value: String): NavValue? {
    return when (this) {
        NavDeepLinkArgumentType.Text -> NavValue.Text(value)
        NavDeepLinkArgumentType.Int -> value.toIntOrNull()?.let(NavValue::IntValue)
        NavDeepLinkArgumentType.Long -> value.toLongOrNull()?.let(NavValue::LongValue)
        NavDeepLinkArgumentType.Boolean -> when (value) {
            "true" -> NavValue.BooleanValue(true)
            "false" -> NavValue.BooleanValue(false)
            else -> null
        }

        NavDeepLinkArgumentType.Float -> value.toFloatOrNull()
            ?.takeIf(Float::isFinite)
            ?.let(NavValue::FloatValue)

        NavDeepLinkArgumentType.Double -> value.toDoubleOrNull()
            ?.takeIf(Double::isFinite)
            ?.let(NavValue::DoubleValue)
    }
}

private fun hostsMatch(expected: String?, actual: String?): Boolean {
    return when {
        expected == null -> actual == null
        actual == null -> false
        else -> expected.equals(actual, ignoreCase = true)
    }
}

private fun placeholderToken(index: Int): String = "__vc_deep_link_argument_${index}__"

private val PlaceholderRegex = Regex("\\{([A-Za-z][A-Za-z0-9_]*)\\}")
