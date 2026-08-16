package com.viewcompose.ui.foundation

/**
 * Explicitly declares that keyed content has no changing ordinary non-State inputs.
 *
 * Pass this singleton as a `contentRevision` only when every ordinary value read by the item,
 * sticky header, pager page, or tab remains semantically constant for that key. Observable State
 * reads and framework environment changes are tracked separately and may still invalidate the
 * content. This marker is a caller promise rather than runtime or compiler analysis; retaining it
 * while an ordinary capture changes permits the framework to skip rendering and can leave stale
 * content. Use a changing semantic revision instead whenever such a capture can change.
 *
 * @sample com.viewcompose.ui.foundation.samples.staticContentRevisionSample
 */
object StaticContentRevision
