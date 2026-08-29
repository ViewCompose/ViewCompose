package com.viewcompose.navigation.core

import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale

/** Target type used to decode one deep-link placeholder. */
enum class NavDeepLinkArgumentType {
    Text,
    Int,
    Long,
    Boolean,
    Float,
    Double,
}

/** Mutation applied to the target stack after a deep link matches. */
enum class NavDeepLinkLaunchMode {
    Push,
    SingleTop,
    ReplaceTop,
    Reset,
}

/**
 * Immutable external-navigation request matched by a [NavGraph].
 *
 * At least one URI, action, or MIME type must be present. The request stores no Android type and
 * may therefore be created in shared code; Android hosts map `Intent.data`, `Intent.action`, and
 * `Intent.type` into the same model. Validation is performed by `resolveDeepLink` so untrusted
 * malformed values produce structured [NavDeepLinkResolution.Rejected] results instead of partial
 * matches.
 *
 * @sample com.viewcompose.navigation.core.samples.deepLinkResolutionSample
 * @property uri optional absolute hierarchical URI input
 * @property action optional case-sensitive external action
 * @param mimeType optional case-insensitive `type/subtype` input
 * @throws IllegalArgumentException if all request dimensions are absent
 */
class NavDeepLinkRequest(
    val uri: String? = null,
    val action: String? = null,
    mimeType: String? = null,
) {
    /** Optional MIME input normalized with locale-independent lowercase comparison. */
    val mimeType: String? = mimeType?.lowercase(Locale.ROOT)

    init {
        require(uri != null || action != null || mimeType != null) {
            "Navigation deep-link request must contain a URI, action, or MIME type."
        }
    }

    /** Compares every normalized request dimension structurally. */
    override fun equals(other: Any?): Boolean {
        return other is NavDeepLinkRequest &&
            uri == other.uri &&
            action == other.action &&
            mimeType == other.mimeType
    }

    /** Returns the structural hash of every normalized request dimension. */
    override fun hashCode(): Int {
        var result = uri?.hashCode() ?: 0
        result = 31 * result + (action?.hashCode() ?: 0)
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        return result
    }

    /** Returns a diagnostic representation without Android objects or mutable state. */
    override fun toString(): String {
        return "NavDeepLinkRequest(uri=$uri, action=$action, mimeType=$mimeType)"
    }
}

/**
 * Allowlists one URI, action, MIME type, or combined external-navigation declaration on a graph node.
 *
 * URI placeholders must occupy a complete path segment or query value, for example
 * `https://example.com/users/{userId}?source={source}`. Placeholder names are unique and default to
 * [NavDeepLinkArgumentType.Text]. Schemes, hosts, and MIME values compare case-insensitively;
 * actions and decoded URI values compare exactly. Every non-null declaration field is a required
 * constraint, while extra request fields are inert. More constrained declarations rank first,
 * followed by URI and MIME specificity; equally specific winners are rejected as ambiguous.
 *
 * Input query parameters not declared by [uriPattern] never become route arguments, affect match
 * specificity, select a stack, or choose a launch mode. Fragments, user info, malformed percent
 * encoding, duplicate query names, and non-hierarchical URI patterns are rejected at construction.
 *
 * @sample com.viewcompose.navigation.core.samples.deepLinkResolutionSample
 * @property uriPattern optional validated absolute hierarchical URI pattern
 * @param argumentTypes optional declared URI-placeholder type overrides
 * @property targetStackId optional retained stack selected after a match
 * @property action optional non-blank case-sensitive action constraint
 * @param mimeType optional `type/subtype` constraint; either component may be `*`
 * @throws IllegalArgumentException if no constraint exists or a declaration is invalid
 */
class NavDeepLink(
    val uriPattern: String? = null,
    argumentTypes: Map<String, NavDeepLinkArgumentType> = emptyMap(),
    val targetStackId: NavStackId? = null,
    val action: String? = null,
    mimeType: String? = null,
) {
    /** Immutable type map for explicitly typed placeholders. */
    val argumentTypes: Map<String, NavDeepLinkArgumentType> = Collections.unmodifiableMap(
        LinkedHashMap(argumentTypes),
    )

    /** Optional MIME constraint normalized with locale-independent lowercase comparison. */
    val mimeType: String? = mimeType?.lowercase(Locale.ROOT)

    internal val matchIdentity = NavDeepLinkMatchIdentity(
        uriPattern = uriPattern,
        action = action,
        mimeType = this.mimeType,
    )
    internal val compiledUri: CompiledNavDeepLink? = uriPattern?.let { pattern ->
        compileDeepLink(
            uriPattern = pattern,
            argumentTypes = this.argumentTypes,
        )
    }
    internal val compiledMimeType: NavMimeType? = this.mimeType?.let { value ->
        requireNotNull(parseMimeType(value)) {
            "Navigation deep-link MIME type must use valid 'type/subtype' syntax: '$value'."
        }
    }

    init {
        require(uriPattern != null || action != null || mimeType != null) {
            "Navigation deep-link declaration must contain a URI pattern, action, or MIME type."
        }
        require(action == null || isValidAction(action)) {
            "Navigation deep-link action must be non-blank and contain no control characters."
        }
        require(uriPattern != null || argumentTypes.isEmpty()) {
            "Navigation deep-link argument types require a URI pattern."
        }
    }

    /** Compares every constraint, argument type, and target stack structurally. */
    override fun equals(other: Any?): Boolean {
        return other is NavDeepLink &&
            uriPattern == other.uriPattern &&
            argumentTypes == other.argumentTypes &&
            targetStackId == other.targetStackId &&
            action == other.action &&
            mimeType == other.mimeType
    }

    /** Returns the structural hash of the public declaration. */
    override fun hashCode(): Int {
        var result = uriPattern?.hashCode() ?: 0
        result = 31 * result + argumentTypes.hashCode()
        result = 31 * result + (targetStackId?.hashCode() ?: 0)
        result = 31 * result + (action?.hashCode() ?: 0)
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        return result
    }

    /** Returns a diagnostic representation of the public declaration. */
    override fun toString(): String {
        return "NavDeepLink(" +
            "uriPattern=$uriPattern, " +
            "action=$action, " +
            "mimeType=$mimeType, " +
            "argumentTypes=$argumentTypes, " +
            "targetStackId=$targetStackId" +
            ")"
    }
}

/**
 * Route produced by a successful deep-link match.
 *
 * @property deepLink declaration that won specificity ranking
 * @property route target graph-node route with decoded placeholder arguments
 */
data class NavDeepLinkMatch(
    val deepLink: NavDeepLink,
    val route: NavRoute,
)

/** Diagnostic reason why a deep link was rejected instead of reported as no match. */
enum class NavDeepLinkRejectionReason {
    MalformedUri,
    MalformedAction,
    MalformedMimeType,
    InvalidArgument,
    AmbiguousMatch,
}

/**
 * Diagnostic details for a rejected deep-link input.
 *
 * [argumentName] is populated for typed decoding failures. [candidates] identifies equally
 * specific declarations for ambiguity, or the most-specific declarations involved in a type
 * failure.
 *
 * @property reason rejection category
 * @property argumentName failed placeholder name, when applicable
 * @param candidates copied conflicting or relevant declarations
 */
class NavDeepLinkRejection(
    val reason: NavDeepLinkRejectionReason,
    val argumentName: String? = null,
    candidates: List<NavDeepLink> = emptyList(),
) {
    /** Immutable relevant declaration list in resolver order. */
    val candidates: List<NavDeepLink> = Collections.unmodifiableList(
        ArrayList(candidates),
    )

    /** Compares all diagnostic fields structurally. */
    override fun equals(other: Any?): Boolean {
        return other is NavDeepLinkRejection &&
            reason == other.reason &&
            argumentName == other.argumentName &&
            candidates == other.candidates
    }

    /** Returns the structural hash of all diagnostic fields. */
    override fun hashCode(): Int {
        var result = reason.hashCode()
        result = 31 * result + (argumentName?.hashCode() ?: 0)
        result = 31 * result + candidates.hashCode()
        return result
    }

    /** Returns a diagnostic representation suitable for logs. */
    override fun toString(): String {
        return "NavDeepLinkRejection(" +
            "reason=$reason, " +
            "argumentName=$argumentName, " +
            "candidates=$candidates" +
            ")"
    }
}

/** Exhaustive result of resolving one external-navigation request. */
sealed interface NavDeepLinkResolution {
    /**
     * One uniquely most-specific pattern matched.
     *
     * @property match winning declaration and decoded route
     */
    data class Matched(
        val match: NavDeepLinkMatch,
    ) : NavDeepLinkResolution

    /** Well-formed input did not match any registered pattern. */
    data object NoMatch : NavDeepLinkResolution

    /**
     * Input matched the pattern domain but could not be accepted safely.
     *
     * @property rejection structured failure details
     */
    data class Rejected(
        val rejection: NavDeepLinkRejection,
    ) : NavDeepLinkResolution

    /** The controller was created without a navigation graph and cannot resolve deep links. */
    data object Unsupported : NavDeepLinkResolution
}

internal data class NavDeepLinkTarget(
    val routeName: String,
    val deepLink: NavDeepLink,
)

internal fun resolveDeepLinkTargets(
    request: NavDeepLinkRequest,
    targets: List<NavDeepLinkTarget>,
): NavDeepLinkResolution {
    if (request.action != null && !isValidAction(request.action)) {
        return NavDeepLinkResolution.Rejected(
            NavDeepLinkRejection(NavDeepLinkRejectionReason.MalformedAction),
        )
    }
    val inputMimeType = request.mimeType?.let { mimeType ->
        parseMimeType(mimeType)
            ?: return NavDeepLinkResolution.Rejected(
                NavDeepLinkRejection(NavDeepLinkRejectionReason.MalformedMimeType),
            )
    }
    val inputUri = request.uri?.let { uri ->
        parseInputUri(uri)
            ?: return NavDeepLinkResolution.Rejected(
            NavDeepLinkRejection(NavDeepLinkRejectionReason.MalformedUri),
        )
    }
    val matches = mutableListOf<RankedDeepLinkMatch>()
    val invalidArguments = mutableListOf<InvalidDeepLinkArgument>()
    targets.forEach { target ->
        when (
            val candidate = target.deepLink.match(
                request = request,
                inputUri = inputUri,
                inputMimeType = inputMimeType,
            )
        ) {
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
                    deepLink = target.deepLink,
                )
            }

            CompiledMatch.NoMatch -> Unit
        }
    }
    if (matches.isEmpty()) {
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
        // If an invalid-argument pattern is as specific as or more specific than a successful match, expose that argument error.
        val invalid = invalidArguments.first { candidate ->
            candidate.score == highestInvalidScore
        }
        return NavDeepLinkResolution.Rejected(
            NavDeepLinkRejection(
                reason = NavDeepLinkRejectionReason.InvalidArgument,
                argumentName = invalid.name,
                candidates = invalidArguments
                    .filter { candidate -> candidate.score == highestInvalidScore }
                    .map(InvalidDeepLinkArgument::deepLink),
            ),
        )
    }
    val bestMatches = matches.filter { match -> match.score == highestScore }
    if (bestMatches.size != 1) {
        // Tied top matches cannot safely choose a route, so surface ambiguity to the caller.
        return NavDeepLinkResolution.Rejected(
            NavDeepLinkRejection(
                reason = NavDeepLinkRejectionReason.AmbiguousMatch,
                candidates = bestMatches.map { match ->
                    match.match.deepLink
                },
            ),
        )
    }
    return NavDeepLinkResolution.Matched(bestMatches.single().match)
}

internal fun resolveDeepLinkTargets(
    uri: String,
    targets: List<NavDeepLinkTarget>,
): NavDeepLinkResolution {
    return resolveDeepLinkTargets(
        request = NavDeepLinkRequest(uri = uri),
        targets = targets,
    )
}

private fun NavDeepLink.match(
    request: NavDeepLinkRequest,
    inputUri: ParsedInputUri?,
    inputMimeType: NavMimeType?,
): CompiledMatch {
    if (action != null && action != request.action) {
        return CompiledMatch.NoMatch
    }
    val mimeSpecificity = compiledMimeType?.matchSpecificity(inputMimeType)
        ?: if (compiledMimeType == null) 0 else return CompiledMatch.NoMatch
    if (compiledMimeType != null && mimeSpecificity < 0) {
        return CompiledMatch.NoMatch
    }
    val constraintCount = listOf(uriPattern, action, mimeType).count { value -> value != null }
    val actionSpecificity = if (action == null) 0 else 1
    val uriMatch = when (val compiled = compiledUri) {
        null -> CompiledUriMatch.Matched(arguments = emptyMap(), score = 0)
        else -> {
            val uri = inputUri ?: return CompiledMatch.NoMatch
            compiled.match(uri)
        }
    }
    return when (uriMatch) {
        is CompiledUriMatch.Matched -> CompiledMatch.Matched(
            arguments = uriMatch.arguments,
            score = NavDeepLinkSpecificity(
                constraintCount = constraintCount,
                uriScore = if (compiledUri == null) 0 else uriMatch.score + 1,
                actionScore = actionSpecificity,
                mimeScore = mimeSpecificity,
            ),
        )

        is CompiledUriMatch.InvalidArgument -> CompiledMatch.InvalidArgument(
            name = uriMatch.name,
            score = NavDeepLinkSpecificity(
                constraintCount = constraintCount,
                uriScore = uriMatch.score + 1,
                actionScore = actionSpecificity,
                mimeScore = mimeSpecificity,
            ),
        )

        CompiledUriMatch.NoMatch -> CompiledMatch.NoMatch
    }
}

/** Compiled deep-link pattern used by the internal resolver. */
internal class CompiledNavDeepLink(
    private val scheme: String,
    private val host: String?,
    private val port: Int,
    private val path: List<ComponentPattern>,
    private val query: Map<String, ComponentPattern>,
    private val argumentTypes: Map<String, NavDeepLinkArgumentType>,
) {
    fun match(input: ParsedInputUri): CompiledUriMatch {
        if (!scheme.equals(input.scheme, ignoreCase = true)) {
            return CompiledUriMatch.NoMatch
        }
        if (!hostsMatch(host, input.host) || port != input.port) {
            return CompiledUriMatch.NoMatch
        }
        if (path.size != input.path.size) {
            return CompiledUriMatch.NoMatch
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
                    return CompiledUriMatch.InvalidArgument(
                        name = result.name,
                        score = specificityScore(),
                    )
                }

                ComponentMatch.NoMatch -> return CompiledUriMatch.NoMatch
                ComponentMatch.Static -> Unit
            }
        }
        query.forEach { (name, pattern) ->
            val values = input.query[name] ?: return CompiledUriMatch.NoMatch
            if (values.size != 1) {
                return CompiledUriMatch.InvalidArgument(
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
                    return CompiledUriMatch.InvalidArgument(
                        name = result.name,
                        score = specificityScore(),
                    )
                }

                ComponentMatch.NoMatch -> return CompiledUriMatch.NoMatch
                ComponentMatch.Static -> Unit
            }
        }
        return CompiledUriMatch.Matched(
            arguments = arguments,
            score = specificityScore(),
        )
    }

    private fun specificityScore(): Int {
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

/** Match pattern for one URI path or query component. */
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

internal sealed interface CompiledUriMatch {
    data class Matched(
        val arguments: Map<String, NavValue>,
        val score: Int,
    ) : CompiledUriMatch

    data class InvalidArgument(
        val name: String,
        val score: Int,
    ) : CompiledUriMatch

    data object NoMatch : CompiledUriMatch
}

internal sealed interface CompiledMatch {
    data class Matched(
        val arguments: Map<String, NavValue>,
        val score: NavDeepLinkSpecificity,
    ) : CompiledMatch

    data class InvalidArgument(
        val name: String,
        val score: NavDeepLinkSpecificity,
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
    val score: NavDeepLinkSpecificity,
)

private data class InvalidDeepLinkArgument(
    val name: String,
    val score: NavDeepLinkSpecificity,
    val deepLink: NavDeepLink,
)

internal data class NavDeepLinkMatchIdentity(
    val uriPattern: String?,
    val action: String?,
    val mimeType: String?,
)

internal data class NavDeepLinkSpecificity(
    val constraintCount: Int,
    val uriScore: Int,
    val actionScore: Int,
    val mimeScore: Int,
) : Comparable<NavDeepLinkSpecificity> {
    override fun compareTo(other: NavDeepLinkSpecificity): Int {
        return compareValuesBy(
            this,
            other,
            NavDeepLinkSpecificity::constraintCount,
            NavDeepLinkSpecificity::uriScore,
            NavDeepLinkSpecificity::actionScore,
            NavDeepLinkSpecificity::mimeScore,
        )
    }
}

internal data class NavMimeType(
    val type: String,
    val subtype: String,
) {
    fun matchSpecificity(request: NavMimeType?): Int {
        request ?: return -1
        if (type != "*" && type != request.type) {
            return -1
        }
        if (subtype != "*" && subtype != request.subtype) {
            return -1
        }
        return (if (type == "*") 0 else 1) + (if (subtype == "*") 0 else 1)
    }
}

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

/** Strictly parses input URIs, rejecting fragments, control characters, and invalid percent encoding. */
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

private fun isValidAction(action: String): Boolean {
    return action.isNotBlank() && action.none(Char::isISOControl)
}

private fun parseMimeType(value: String): NavMimeType? {
    if (value.isBlank() || value.any(Char::isWhitespace)) {
        return null
    }
    val separatorIndex = value.indexOf('/')
    if (separatorIndex <= 0 || separatorIndex != value.lastIndexOf('/') || separatorIndex == value.lastIndex) {
        return null
    }
    val type = value.substring(0, separatorIndex).lowercase(Locale.ROOT)
    val subtype = value.substring(separatorIndex + 1).lowercase(Locale.ROOT)
    if (!MimeTokenRegex.matches(type) || !MimeTokenRegex.matches(subtype)) {
        return null
    }
    return NavMimeType(type = type, subtype = subtype)
}

private fun placeholderToken(index: Int): String = "__vc_deep_link_argument_${index}__"

private val PlaceholderRegex = Regex("\\{([A-Za-z][A-Za-z0-9_]*)\\}")
private val MimeTokenRegex = Regex("(?:[A-Za-z0-9!#$%&'*+.^_`|~-]+|\\*)")
