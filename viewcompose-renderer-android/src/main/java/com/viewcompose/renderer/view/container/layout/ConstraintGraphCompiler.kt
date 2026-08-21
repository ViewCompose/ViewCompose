package com.viewcompose.renderer.view.container

import com.viewcompose.ui.node.spec.ConstraintAnchor
import com.viewcompose.ui.node.spec.ConstraintAnchorLink
import com.viewcompose.ui.node.spec.ConstraintBarrierSpec
import com.viewcompose.ui.node.spec.ConstraintChainOrientation
import com.viewcompose.ui.node.spec.ConstraintChainSpec
import com.viewcompose.ui.node.spec.ConstraintCircleSpec
import com.viewcompose.ui.node.spec.ConstraintDimension
import com.viewcompose.ui.node.spec.ConstraintFlowSpec
import com.viewcompose.ui.node.spec.ConstraintGuidelinePosition
import com.viewcompose.ui.node.spec.ConstraintGuidelineSpec
import com.viewcompose.ui.node.spec.ConstraintGroupSpec
import com.viewcompose.ui.node.spec.ConstraintHelpersSpec
import com.viewcompose.ui.node.spec.ConstraintItemSpec
import com.viewcompose.ui.node.spec.ConstraintLayerSpec
import com.viewcompose.ui.node.spec.ConstraintMatchMode
import com.viewcompose.ui.node.spec.ConstraintPlaceholderSpec
import com.viewcompose.ui.node.spec.ConstraintSetSpec
import com.viewcompose.ui.unit.UiDp

/** One mounted content child and its optional inline constraint declaration. */
internal data class ConstraintContentBinding(
    val referenceId: String?,
    val inlineSpec: ConstraintItemSpec?,
    val nativeIdentity: Any,
)

/** Helper classes whose native View instances are owned by one renderer registry. */
internal enum class NativeConstraintHelperKind {
    Guideline,
    Barrier,
    Flow,
    Group,
    Layer,
    Placeholder,
}

/** Complete immutable candidate accepted before any native View mutation. */
internal data class ResolvedConstraintGraph(
    val contentBindings: List<ConstraintContentBinding>,
    val contentById: Map<String, ConstraintContentBinding>,
    val constraints: Map<String, ConstraintItemSpec>,
    val helpers: ConstraintHelpersSpec,
    val helperKinds: Map<String, NativeConstraintHelperKind>,
    val constrainableIds: Set<String>,
    val topologyFingerprint: Long,
    val scalarFingerprint: Long,
)

/** Stable category used by bounded renderer diagnostics for a rejected candidate graph. */
internal enum class ConstraintGraphRejectionReason {
    DuplicateId,
    MissingReference,
    InvalidAnchor,
    InvalidDimension,
    InvalidHelper,
    InvalidValue,
    NativeCommit,
}

/** Structured rejection that leaves the previously accepted native graph unchanged. */
internal data class ConstraintGraphRejection(
    val reason: ConstraintGraphRejectionReason,
    val identity: String?,
    val detail: String,
)

/** Result of compiling a complete candidate without mutating Android Views. */
internal sealed interface ConstraintGraphCompilation {
    data class Accepted(
        val graph: ResolvedConstraintGraph,
    ) : ConstraintGraphCompilation

    data class Rejected(
        val rejection: ConstraintGraphRejection,
    ) : ConstraintGraphCompilation
}

/** Compiles and validates the merged ConstraintLayout transport graph. */
internal object ConstraintGraphCompiler {
    fun compile(
        contentBindings: List<ConstraintContentBinding>,
        decoupled: ConstraintSetSpec?,
        inlineHelpers: ConstraintHelpersSpec,
    ): ConstraintGraphCompilation {
        return try {
            val contentById = collectContent(contentBindings)
            val inlineConstraints = collectInlineConstraints(contentBindings)
            val constraints = mergeConstraints(
                decoupled = decoupled?.constraints.orEmpty(),
                inline = inlineConstraints,
            )
            val helpers = mergeHelpers(
                decoupled = decoupled?.helpers,
                inline = inlineHelpers,
            )
            val helperKinds = collectHelperKinds(helpers)
            val constrainableIds = contentById.keys + helperKinds.filterValues { kind ->
                kind == NativeConstraintHelperKind.Flow || kind == NativeConstraintHelperKind.Placeholder
            }.keys
            validateNamespace(contentById, helperKinds)
            validateConstraints(constraints, constrainableIds, contentById.keys + helperKinds.keys)
            validateHelpers(helpers, contentById.keys, helperKinds, constraints)
            ConstraintGraphCompilation.Accepted(
                ResolvedConstraintGraph(
                    contentBindings = contentBindings.toList(),
                    contentById = contentById,
                    constraints = constraints,
                    helpers = helpers,
                    helperKinds = helperKinds,
                    constrainableIds = constrainableIds,
                    topologyFingerprint = topologyFingerprint(
                        contentById = contentById,
                        constraints = constraints,
                        helpers = helpers,
                        helperKinds = helperKinds,
                    ),
                    scalarFingerprint = scalarFingerprint(
                        constraints = constraints,
                        helpers = helpers,
                    ),
                ),
            )
        } catch (failure: ConstraintGraphValidationException) {
            ConstraintGraphCompilation.Rejected(failure.rejection)
        }
    }

    private fun topologyFingerprint(
        contentById: Map<String, ConstraintContentBinding>,
        constraints: Map<String, ConstraintItemSpec>,
        helpers: ConstraintHelpersSpec,
        helperKinds: Map<String, NativeConstraintHelperKind>,
    ): Long {
        var result = contentById.keys.hashCode().toLong()
        helperKinds.forEach { (id, kind) ->
            result += 29L * id.hashCode() + kind.name.hashCode()
        }
        constraints.forEach { (id, item) ->
            result += 31L * id.hashCode() + item.topologyHashCode()
        }
        helpers.barriers.forEach { spec ->
            result += 37L * spec.id.hashCode() + spec.referencedIds.hashCode()
        }
        helpers.chains.forEach { spec ->
            result = result * 31L + spec.orientation.name.hashCode()
            result = result * 31L + spec.referencedIds.hashCode()
        }
        helpers.flows.forEach { spec ->
            result += 41L * spec.id.hashCode() + spec.referencedIds.hashCode()
        }
        helpers.groups.forEach { spec ->
            result += 43L * spec.id.hashCode() + spec.referencedIds.hashCode()
        }
        helpers.layers.forEach { spec ->
            result += 47L * spec.id.hashCode() + spec.referencedIds.hashCode()
        }
        helpers.placeholders.forEach { spec ->
            result += 53L * spec.id.hashCode() + (spec.contentId?.hashCode() ?: 0)
        }
        return result
    }

    private fun scalarFingerprint(
        constraints: Map<String, ConstraintItemSpec>,
        helpers: ConstraintHelpersSpec,
    ): Long = 31L * constraints.hashCode() + helpers.hashCode()

    private fun ConstraintItemSpec.topologyHashCode(): Int {
        var result = start.topologyHashCode()
        result = result * 31 + end.topologyHashCode()
        result = result * 31 + top.topologyHashCode()
        result = result * 31 + bottom.topologyHashCode()
        result = result * 31 + baseline.topologyHashCode()
        result = result * 31 + (circle?.targetId?.hashCode() ?: 0)
        return result
    }

    private fun ConstraintAnchorLink?.topologyHashCode(): Int {
        val target = this?.target ?: return 0
        return 31 * (target.id?.hashCode() ?: 0) + target.anchor.name.hashCode()
    }

    private fun collectContent(
        contentBindings: List<ConstraintContentBinding>,
    ): Map<String, ConstraintContentBinding> {
        val contentById = linkedMapOf<String, ConstraintContentBinding>()
        contentBindings.forEach { binding ->
            val id = binding.referenceId
            if (id == null) {
                reject(
                    ConstraintGraphRejectionReason.MissingReference,
                    null,
                    "Every ConstraintLayout child requires a non-empty layout reference ID.",
                )
            }
            requireId(id, "Child")
            if (contentById.put(id, binding) != null) {
                reject(
                    ConstraintGraphRejectionReason.DuplicateId,
                    id,
                    "Duplicate child reference ID '$id'.",
                )
            }
        }
        return contentById
    }

    private fun collectInlineConstraints(
        contentBindings: List<ConstraintContentBinding>,
    ): Map<String, ConstraintItemSpec> {
        val constraints = linkedMapOf<String, ConstraintItemSpec>()
        contentBindings.forEach { binding ->
            val spec = binding.inlineSpec ?: return@forEach
            val id = binding.referenceId ?: return@forEach
            if (constraints.put(id, spec) != null) {
                reject(
                    ConstraintGraphRejectionReason.DuplicateId,
                    id,
                    "Duplicate inline constraint ID '$id'.",
                )
            }
        }
        return constraints
    }

    private fun mergeConstraints(
        decoupled: Map<String, ConstraintItemSpec>,
        inline: Map<String, ConstraintItemSpec>,
    ): Map<String, ConstraintItemSpec> {
        val merged = linkedMapOf<String, ConstraintItemSpec>()
        decoupled.forEach { (id, spec) ->
            requireId(id, "Constraint")
            merged[id] = spec
        }
        inline.forEach { (id, spec) -> merged[id] = spec }
        return merged.toMap()
    }

    private fun mergeHelpers(
        decoupled: ConstraintHelpersSpec?,
        inline: ConstraintHelpersSpec,
    ): ConstraintHelpersSpec {
        return ConstraintHelpersSpec(
            guidelines = mergeHelperList(
                kind = NativeConstraintHelperKind.Guideline,
                decoupled = decoupled?.guidelines.orEmpty(),
                inline = inline.guidelines,
                id = ConstraintGuidelineSpec::id,
            ),
            barriers = mergeHelperList(
                kind = NativeConstraintHelperKind.Barrier,
                decoupled = decoupled?.barriers.orEmpty(),
                inline = inline.barriers,
                id = ConstraintBarrierSpec::id,
            ),
            chains = decoupled?.chains.orEmpty() + inline.chains,
            flows = mergeHelperList(
                kind = NativeConstraintHelperKind.Flow,
                decoupled = decoupled?.flows.orEmpty(),
                inline = inline.flows,
                id = ConstraintFlowSpec::id,
            ),
            groups = mergeHelperList(
                kind = NativeConstraintHelperKind.Group,
                decoupled = decoupled?.groups.orEmpty(),
                inline = inline.groups,
                id = ConstraintGroupSpec::id,
            ),
            layers = mergeHelperList(
                kind = NativeConstraintHelperKind.Layer,
                decoupled = decoupled?.layers.orEmpty(),
                inline = inline.layers,
                id = ConstraintLayerSpec::id,
            ),
            placeholders = mergeHelperList(
                kind = NativeConstraintHelperKind.Placeholder,
                decoupled = decoupled?.placeholders.orEmpty(),
                inline = inline.placeholders,
                id = ConstraintPlaceholderSpec::id,
            ),
        )
    }

    private fun <T> mergeHelperList(
        kind: NativeConstraintHelperKind,
        decoupled: List<T>,
        inline: List<T>,
        id: (T) -> String,
    ): List<T> {
        fun requireUnique(source: String, values: List<T>) {
            val ids = mutableSetOf<String>()
            values.forEach { value ->
                val helperId = id(value)
                requireId(helperId, "$kind helper")
                if (!ids.add(helperId)) {
                    reject(
                        ConstraintGraphRejectionReason.DuplicateId,
                        helperId,
                        "Duplicate $kind helper ID '$helperId' in $source declarations.",
                    )
                }
            }
        }
        requireUnique("decoupled", decoupled)
        requireUnique("inline", inline)
        val merged = linkedMapOf<String, T>()
        decoupled.forEach { value -> merged[id(value)] = value }
        inline.forEach { value -> merged[id(value)] = value }
        return merged.values.toList()
    }

    private fun collectHelperKinds(
        helpers: ConstraintHelpersSpec,
    ): Map<String, NativeConstraintHelperKind> {
        val kinds = linkedMapOf<String, NativeConstraintHelperKind>()
        fun register(id: String, kind: NativeConstraintHelperKind) {
            val previous = kinds.put(id, kind)
            if (previous != null && previous != kind) {
                reject(
                    ConstraintGraphRejectionReason.DuplicateId,
                    id,
                    "Helper ID '$id' is declared as both $previous and $kind.",
                )
            }
        }
        helpers.guidelines.forEach { register(it.id, NativeConstraintHelperKind.Guideline) }
        helpers.barriers.forEach { register(it.id, NativeConstraintHelperKind.Barrier) }
        helpers.flows.forEach { register(it.id, NativeConstraintHelperKind.Flow) }
        helpers.groups.forEach { register(it.id, NativeConstraintHelperKind.Group) }
        helpers.layers.forEach { register(it.id, NativeConstraintHelperKind.Layer) }
        helpers.placeholders.forEach { register(it.id, NativeConstraintHelperKind.Placeholder) }
        return kinds.toMap()
    }

    private fun validateNamespace(
        contentById: Map<String, ConstraintContentBinding>,
        helperKinds: Map<String, NativeConstraintHelperKind>,
    ) {
        contentById.keys.intersect(helperKinds.keys).firstOrNull()?.let { collision ->
            reject(
                ConstraintGraphRejectionReason.DuplicateId,
                collision,
                "ID '$collision' is declared by both a child and a helper.",
            )
        }
    }

    private fun validateConstraints(
        constraints: Map<String, ConstraintItemSpec>,
        constrainableIds: Set<String>,
        knownIds: Set<String>,
    ) {
        constraints.forEach { (id, item) ->
            if (id !in constrainableIds) {
                reject(
                    ConstraintGraphRejectionReason.MissingReference,
                    id,
                    "Constraint item '$id' has no matching child, Flow, or Placeholder.",
                )
            }
            validateItem(id, item, knownIds)
        }
    }

    private fun validateItem(
        id: String,
        item: ConstraintItemSpec,
        knownIds: Set<String>,
    ) {
        validateLink(id, ConstraintAnchor.Start, item.start, knownIds)
        validateLink(id, ConstraintAnchor.End, item.end, knownIds)
        validateLink(id, ConstraintAnchor.Top, item.top, knownIds)
        validateLink(id, ConstraintAnchor.Bottom, item.bottom, knownIds)
        validateLink(id, ConstraintAnchor.Baseline, item.baseline, knownIds)
        validateDimension("$id.width", item.width)
        validateDimension("$id.height", item.height)
        validateUnitFloat("$id.horizontalBias", item.horizontalBias, 0f..1f)
        validateUnitFloat("$id.verticalBias", item.verticalBias, 0f..1f)

        val edgeOrBaseline = listOf(item.start, item.end, item.top, item.bottom, item.baseline)
            .any { it != null }
        if (item.baseline != null && (item.top != null || item.bottom != null)) {
            reject(
                ConstraintGraphRejectionReason.InvalidAnchor,
                id,
                "Constraint item '$id' combines a baseline link with top or bottom positioning.",
            )
        }
        if (item.circle != null && edgeOrBaseline) {
            reject(
                ConstraintGraphRejectionReason.InvalidAnchor,
                id,
                "Constraint item '$id' combines circular positioning with edge or baseline links.",
            )
        }
        item.circle?.let { validateCircle(id, it, knownIds) }
        if (
            item.ratio != null &&
            item.width !is ConstraintDimension.MatchConstraints &&
            item.height !is ConstraintDimension.MatchConstraints
        ) {
            reject(
                ConstraintGraphRejectionReason.InvalidDimension,
                id,
                "Constraint item '$id' ratio requires a match-constraint width or height.",
            )
        }
    }

    private fun validateLink(
        sourceId: String,
        sourceAnchor: ConstraintAnchor,
        link: ConstraintAnchorLink?,
        knownIds: Set<String>,
    ) {
        link ?: return
        val targetId = link.target.id
        if (targetId != null) {
            requireId(targetId, "Constraint target")
            if (targetId == sourceId) {
                reject(
                    ConstraintGraphRejectionReason.InvalidAnchor,
                    sourceId,
                    "Constraint item '$sourceId' cannot reference itself.",
                )
            }
            if (targetId !in knownIds) {
                reject(
                    ConstraintGraphRejectionReason.MissingReference,
                    sourceId,
                    "Constraint item '$sourceId' references missing ID '$targetId'.",
                )
            }
        }
        val validPlane = when (sourceAnchor) {
            ConstraintAnchor.Start,
            ConstraintAnchor.End,
            -> link.target.anchor == ConstraintAnchor.Start || link.target.anchor == ConstraintAnchor.End

            ConstraintAnchor.Top,
            ConstraintAnchor.Bottom,
            -> link.target.anchor == ConstraintAnchor.Top || link.target.anchor == ConstraintAnchor.Bottom

            ConstraintAnchor.Baseline -> link.target.anchor in setOf(
                ConstraintAnchor.Baseline,
                ConstraintAnchor.Top,
                ConstraintAnchor.Bottom,
            )
        }
        if (!validPlane) {
            reject(
                ConstraintGraphRejectionReason.InvalidAnchor,
                sourceId,
                "Constraint item '$sourceId' connects $sourceAnchor to incompatible ${link.target.anchor}.",
            )
        }
        validateNonNegative("$sourceId.$sourceAnchor.margin", link.margin)
        link.goneMargin?.let { validateNonNegative("$sourceId.$sourceAnchor.goneMargin", it) }
    }

    private fun validateCircle(
        sourceId: String,
        circle: ConstraintCircleSpec,
        knownIds: Set<String>,
    ) {
        requireId(circle.targetId, "Circle target")
        if (circle.targetId == sourceId || circle.targetId !in knownIds) {
            reject(
                if (circle.targetId == sourceId) {
                    ConstraintGraphRejectionReason.InvalidAnchor
                } else {
                    ConstraintGraphRejectionReason.MissingReference
                },
                sourceId,
                "Constraint item '$sourceId' has invalid circle target '${circle.targetId}'.",
            )
        }
        if (
            !circle.radius.value.isFinite() ||
            circle.radius.value < 0f ||
            !circle.angle.isFinite() ||
            circle.angle < 0f ||
            circle.angle >= 360f
        ) {
            reject(
                ConstraintGraphRejectionReason.InvalidValue,
                sourceId,
                "Constraint item '$sourceId' circle radius must be finite/non-negative and " +
                    "angle within 0f..<360f.",
            )
        }
    }

    private fun validateDimension(
        identity: String,
        dimension: ConstraintDimension,
    ) {
        when (dimension) {
            ConstraintDimension.WrapContent,
            ConstraintDimension.ConstrainedWrapContent,
            -> Unit

            is ConstraintDimension.Fixed -> validateNonNegative(identity, dimension.value)
            is ConstraintDimension.MatchConstraints -> {
                val min = dimension.min
                val max = dimension.max
                min?.let { validateNonNegative("$identity.min", it) }
                max?.let { validateNonNegative("$identity.max", it) }
                if (min != null && max != null && min > max) {
                    reject(
                        ConstraintGraphRejectionReason.InvalidDimension,
                        identity,
                        "$identity minimum exceeds maximum.",
                    )
                }
                val mode = dimension.mode
                if (mode is ConstraintMatchMode.Percent && (!mode.fraction.isFinite() || mode.fraction !in 0f..1f)) {
                    reject(
                        ConstraintGraphRejectionReason.InvalidDimension,
                        identity,
                        "$identity percent must be finite and within 0f..1f.",
                    )
                }
            }
        }
    }

    private fun validateHelpers(
        helpers: ConstraintHelpersSpec,
        contentIds: Set<String>,
        helperKinds: Map<String, NativeConstraintHelperKind>,
        constraints: Map<String, ConstraintItemSpec>,
    ) {
        val knownIds = contentIds + helperKinds.keys
        helpers.guidelines.forEach(::validateGuideline)
        helpers.barriers.forEach { spec ->
            validateReferenceList("Barrier '${spec.id}'", spec.id, spec.referencedIds, knownIds, 1)
            validateNonNegative("Barrier '${spec.id}'.margin", spec.margin)
        }
        helpers.chains.forEachIndexed { index, spec ->
            validateChain(index, spec, contentIds)
        }
        helpers.flows.forEach { spec ->
            validateReferenceList("Flow '${spec.id}'", spec.id, spec.referencedIds, knownIds, 1)
            validateNonNegative("Flow '${spec.id}'.horizontalGap", spec.horizontalGap)
            validateNonNegative("Flow '${spec.id}'.verticalGap", spec.verticalGap)
            validateNonNegative("Flow '${spec.id}'.padding", spec.padding)
            validateNonNegative("Flow '${spec.id}'.paddingStart", spec.paddingStart)
            validateNonNegative("Flow '${spec.id}'.paddingEnd", spec.paddingEnd)
            validateNonNegative("Flow '${spec.id}'.paddingTop", spec.paddingTop)
            validateNonNegative("Flow '${spec.id}'.paddingBottom", spec.paddingBottom)
            validateUnitFloat("Flow '${spec.id}'.horizontalBias", spec.horizontalBias, 0f..1f)
            validateUnitFloat("Flow '${spec.id}'.verticalBias", spec.verticalBias, 0f..1f)
            validateUnitFloat("Flow '${spec.id}'.firstHorizontalBias", spec.firstHorizontalBias, 0f..1f)
            validateUnitFloat("Flow '${spec.id}'.firstVerticalBias", spec.firstVerticalBias, 0f..1f)
            validateUnitFloat("Flow '${spec.id}'.lastHorizontalBias", spec.lastHorizontalBias, 0f..1f)
            validateUnitFloat("Flow '${spec.id}'.lastVerticalBias", spec.lastVerticalBias, 0f..1f)
            if (spec.maxElementsWrap != -1 && spec.maxElementsWrap <= 0) {
                reject(
                    ConstraintGraphRejectionReason.InvalidHelper,
                    spec.id,
                    "Flow '${spec.id}' maxElementsWrap must be -1 or greater than zero.",
                )
            }
        }
        helpers.groups.forEach { spec ->
            validateReferenceList("Group '${spec.id}'", spec.id, spec.referencedIds, contentIds, 1)
            validateNonNegative("Group '${spec.id}'.elevation", spec.elevation)
        }
        helpers.layers.forEach { spec ->
            validateReferenceList("Layer '${spec.id}'", spec.id, spec.referencedIds, contentIds, 1)
            listOf(spec.rotation, spec.scaleX, spec.scaleY).forEach { value ->
                if (!value.isFinite()) {
                    reject(
                        ConstraintGraphRejectionReason.InvalidValue,
                        spec.id,
                        "Layer '${spec.id}' transform values must be finite.",
                    )
                }
            }
            validateNonNegative("Layer '${spec.id}'.elevation", spec.elevation)
            validateFinite("Layer '${spec.id}'.translationX", spec.translationX)
            validateFinite("Layer '${spec.id}'.translationY", spec.translationY)
            spec.pivotX?.let { validateFinite("Layer '${spec.id}'.pivotX", it) }
            spec.pivotY?.let { validateFinite("Layer '${spec.id}'.pivotY", it) }
        }
        helpers.placeholders.forEach { spec ->
            spec.contentId?.let { contentId ->
                if (contentId == spec.id || contentId !in contentIds) {
                    reject(
                        ConstraintGraphRejectionReason.MissingReference,
                        spec.id,
                        "Placeholder '${spec.id}' content '$contentId' is not a mounted child.",
                    )
                }
            }
        }
        validateChainOwnership(helpers.chains, constraints)
        validateHelperCycles(helpers, helperKinds)
    }

    private fun validateChainOwnership(
        chains: List<ConstraintChainSpec>,
        constraints: Map<String, ConstraintItemSpec>,
    ) {
        val horizontalMembers = mutableSetOf<String>()
        val verticalMembers = mutableSetOf<String>()
        chains.forEachIndexed { index, chain ->
            val ownedMembers = when (chain.orientation) {
                ConstraintChainOrientation.Horizontal -> horizontalMembers
                ConstraintChainOrientation.Vertical -> verticalMembers
            }
            chain.referencedIds.forEach { id ->
                if (!ownedMembers.add(id)) {
                    reject(
                        ConstraintGraphRejectionReason.InvalidHelper,
                        id,
                        "Constraint item '$id' belongs to multiple ${chain.orientation} chains.",
                    )
                }
                val item = constraints[id] ?: return@forEach
                val competing = when (chain.orientation) {
                    ConstraintChainOrientation.Horizontal ->
                        item.start != null || item.end != null || item.circle != null

                    ConstraintChainOrientation.Vertical ->
                        item.top != null || item.bottom != null || item.baseline != null || item.circle != null
                }
                if (competing) {
                    reject(
                        ConstraintGraphRejectionReason.InvalidAnchor,
                        id,
                        "Chain[$index] owns the ${chain.orientation} anchors for '$id'; " +
                            "the item must not declare competing links.",
                    )
                }
            }
        }
    }

    private fun validateGuideline(spec: ConstraintGuidelineSpec) {
        when (val position = spec.position) {
            is ConstraintGuidelinePosition.Offset -> validateNonNegative(
                "Guideline '${spec.id}'.offset",
                position.value,
            )

            is ConstraintGuidelinePosition.Fraction -> {
                if (!position.value.isFinite() || position.value !in 0f..1f) {
                    reject(
                        ConstraintGraphRejectionReason.InvalidHelper,
                        spec.id,
                        "Guideline '${spec.id}' fraction must be finite and within 0f..1f.",
                    )
                }
            }
        }
    }

    private fun validateChain(
        index: Int,
        spec: ConstraintChainSpec,
        contentIds: Set<String>,
    ) {
        val identity = "Chain[$index]"
        validateReferenceList(identity, null, spec.referencedIds, contentIds, 2)
        spec.weights?.let { weights ->
            if (weights.size != spec.referencedIds.size || weights.any { !it.isFinite() || it <= 0f }) {
                reject(
                    ConstraintGraphRejectionReason.InvalidHelper,
                    identity,
                    "$identity weights must match references and be finite positive values.",
                )
            }
        }
        validateUnitFloat("$identity.bias", spec.bias, 0f..1f)
    }

    private fun validateReferenceList(
        identity: String,
        ownerId: String?,
        references: List<String>,
        knownIds: Set<String>,
        minimumSize: Int,
    ) {
        if (references.size < minimumSize) {
            reject(
                ConstraintGraphRejectionReason.InvalidHelper,
                ownerId ?: identity,
                "$identity requires at least $minimumSize referenced ID(s).",
            )
        }
        if (references.toSet().size != references.size) {
            reject(
                ConstraintGraphRejectionReason.DuplicateId,
                ownerId ?: identity,
                "$identity contains duplicate referenced IDs.",
            )
        }
        references.forEach { reference ->
            requireId(reference, "$identity reference")
            if (reference == ownerId || reference !in knownIds) {
                reject(
                    if (reference == ownerId) {
                        ConstraintGraphRejectionReason.InvalidHelper
                    } else {
                        ConstraintGraphRejectionReason.MissingReference
                    },
                    ownerId ?: identity,
                    "$identity contains invalid reference '$reference'.",
                )
            }
        }
    }

    private fun validateHelperCycles(
        helpers: ConstraintHelpersSpec,
        helperKinds: Map<String, NativeConstraintHelperKind>,
    ) {
        val helperIds = helperKinds.keys
        val dependencies = linkedMapOf<String, Set<String>>()
        helpers.barriers.forEach { dependencies[it.id] = it.referencedIds.filterTo(mutableSetOf()) { id -> id in helperIds } }
        helpers.flows.forEach { dependencies[it.id] = it.referencedIds.filterTo(mutableSetOf()) { id -> id in helperIds } }
        helpers.guidelines.forEach { dependencies.putIfAbsent(it.id, emptySet()) }
        helpers.groups.forEach { dependencies.putIfAbsent(it.id, emptySet()) }
        helpers.layers.forEach { dependencies.putIfAbsent(it.id, emptySet()) }
        helpers.placeholders.forEach { dependencies.putIfAbsent(it.id, emptySet()) }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visit(id: String): Boolean {
            if (!visiting.add(id)) return true
            if (!visited.add(id)) {
                visiting.remove(id)
                return false
            }
            val cycle = dependencies[id].orEmpty().any(::visit)
            visiting.remove(id)
            return cycle
        }
        dependencies.keys.firstOrNull(::visit)?.let { id ->
            reject(
                ConstraintGraphRejectionReason.InvalidHelper,
                id,
                "Helper dependency graph contains a cycle involving '$id'.",
            )
        }
    }

    private fun requireId(id: String, owner: String) {
        if (id.isBlank()) {
            reject(
                ConstraintGraphRejectionReason.DuplicateId,
                id,
                "$owner ID must not be blank.",
            )
        }
    }

    private fun validateUnitFloat(
        identity: String,
        value: Float?,
        range: ClosedFloatingPointRange<Float>,
    ) {
        if (value != null && (!value.isFinite() || value !in range)) {
            reject(
                ConstraintGraphRejectionReason.InvalidValue,
                identity,
                "$identity must be finite and within ${range.start}..${range.endInclusive}.",
            )
        }
    }

    private fun validateNonNegative(identity: String, value: UiDp) {
        if (!value.value.isFinite() || value.value < 0f) {
            reject(
                ConstraintGraphRejectionReason.InvalidValue,
                identity,
                "$identity must be finite and non-negative.",
            )
        }
    }

    private fun validateFinite(identity: String, value: UiDp) {
        if (!value.value.isFinite()) {
            reject(
                ConstraintGraphRejectionReason.InvalidValue,
                identity,
                "$identity must be finite.",
            )
        }
    }

    private fun reject(
        reason: ConstraintGraphRejectionReason,
        identity: String?,
        detail: String,
    ): Nothing {
        throw ConstraintGraphValidationException(
            ConstraintGraphRejection(
                reason = reason,
                identity = identity,
                detail = detail,
            ),
        )
    }
}

/** Collision-safe topology comparison; fingerprints are only the constant-time rejection gate. */
internal fun ResolvedConstraintGraph.hasSameTopologyAs(other: ResolvedConstraintGraph): Boolean {
    if (topologyFingerprint != other.topologyFingerprint) return false
    if (contentById.size != other.contentById.size) return false
    if (helperKinds != other.helperKinds) return false
    if (constrainableIds != other.constrainableIds) return false
    contentById.forEach { (id, binding) ->
        if (other.contentById[id]?.nativeIdentity !== binding.nativeIdentity) return false
    }
    constrainableIds.forEach { id ->
        if (!sameItemTopology(constraints[id], other.constraints[id])) return false
    }
    if (!sameReferencedTopology(
            helpers.barriers,
            other.helpers.barriers,
            id = ConstraintBarrierSpec::id,
            references = ConstraintBarrierSpec::referencedIds,
        )
    ) {
        return false
    }
    if (helpers.chains.size != other.helpers.chains.size) return false
    helpers.chains.indices.forEach { index ->
        val current = helpers.chains[index]
        val previous = other.helpers.chains[index]
        if (
            current.orientation != previous.orientation ||
            current.referencedIds != previous.referencedIds
        ) {
            return false
        }
    }
    if (!sameReferencedTopology(
            helpers.flows,
            other.helpers.flows,
            id = ConstraintFlowSpec::id,
            references = ConstraintFlowSpec::referencedIds,
        )
    ) {
        return false
    }
    if (!sameReferencedTopology(
            helpers.groups,
            other.helpers.groups,
            id = ConstraintGroupSpec::id,
            references = ConstraintGroupSpec::referencedIds,
        )
    ) {
        return false
    }
    if (!sameReferencedTopology(
            helpers.layers,
            other.helpers.layers,
            id = ConstraintLayerSpec::id,
            references = ConstraintLayerSpec::referencedIds,
        )
    ) {
        return false
    }
    if (helpers.placeholders.size != other.helpers.placeholders.size) return false
    helpers.placeholders.indices.forEach { index ->
        val current = helpers.placeholders[index]
        val previous = other.helpers.placeholders[index]
        if (current.id != previous.id || current.contentId != previous.contentId) return false
    }
    return true
}

private fun sameItemTopology(
    current: ConstraintItemSpec?,
    previous: ConstraintItemSpec?,
): Boolean {
    return current?.start?.target == previous?.start?.target &&
        current?.end?.target == previous?.end?.target &&
        current?.top?.target == previous?.top?.target &&
        current?.bottom?.target == previous?.bottom?.target &&
        current?.baseline?.target == previous?.baseline?.target &&
        current?.circle?.targetId == previous?.circle?.targetId
}

private inline fun <T> sameReferencedTopology(
    current: List<T>,
    previous: List<T>,
    id: (T) -> String,
    references: (T) -> List<String>,
): Boolean {
    if (current.size != previous.size) return false
    current.indices.forEach { index ->
        val currentItem = current[index]
        val previousItem = previous[index]
        if (id(currentItem) != id(previousItem)) return false
        if (references(currentItem) != references(previousItem)) return false
    }
    return true
}

private class ConstraintGraphValidationException(
    val rejection: ConstraintGraphRejection,
) : IllegalArgumentException(rejection.detail)
