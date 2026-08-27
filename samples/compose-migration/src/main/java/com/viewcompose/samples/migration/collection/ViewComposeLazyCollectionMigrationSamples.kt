package com.viewcompose.samples.migration.collection

import android.content.Context
import android.view.View
import com.viewcompose.host.android.AndroidView
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.HorizontalPager
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.StaticContentRevision
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.toLazyItemsSnapshot
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.asLazyItemTable
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.unit.UiDp

data class Message(
    val id: Long,
    val version: Int,
    val title: String,
)

data class Account(
    val id: Long,
    val version: Int,
    val name: String,
    val status: String,
)

fun UiTreeBuilder.typedRevisionMigrationSample(messages: List<Message>) {
    // DOCS_REGION_START(migration-lazy-typed-revision)
    LazyColumn(
        items = messages,
        key = { message -> message.id },
        contentType = { "message-row" },
        contentRevision = { message -> message.version },
    ) { message ->
        MessageRow(message)
    }
    // DOCS_REGION_END(migration-lazy-typed-revision)
}

fun UiTreeBuilder.staticRevisionMigrationSample() {
    LazyColumn {
        // DOCS_REGION_START(migration-lazy-static-revision)
        stickyHeader(
            key = "messages-header",
            contentRevision = StaticContentRevision,
        ) {
            Text("Messages")
        }
        // DOCS_REGION_END(migration-lazy-static-revision)
    }
}

fun UiTreeBuilder.pagerRevisionMigrationSample(account: Account) {
    HorizontalPager(currentPage = 0, onPageChanged = {}) {
        // DOCS_REGION_START(migration-lazy-pager-revision)
        Page(
            key = account.id,
            contentRevision = account.version,
            contentType = "account-page",
        ) {
            AccountPage(account)
        }
        // DOCS_REGION_END(migration-lazy-pager-revision)
    }
}

fun UiTreeBuilder.namedItemRevisionMigrationSample(message: Message) {
    LazyColumn {
        // DOCS_REGION_START(migration-lazy-named-item)
        item(
            key = message.id,
            contentRevision = message.version,
            contentType = "message-row",
        ) {
            MessageRow(message)
        }
        // DOCS_REGION_END(migration-lazy-named-item)
    }
}

fun UiTreeBuilder.snapshotMigrationSample(messages: List<Message>) {
    // DOCS_REGION_START(migration-lazy-snapshot)
    val lazyMessages = remember(messages) {
        messages.toLazyItemsSnapshot()
    }

    LazyColumn(
        items = lazyMessages,
        key = { message -> message.id },
        contentType = { "message-row" },
        contentRevision = { message -> message.version },
    ) { message ->
        MessageRow(message)
    }
    // DOCS_REGION_END(migration-lazy-snapshot)
}

fun UiTreeBuilder.implicitSiblingMigrationSample(account: Account) {
    LazyColumn {
        // DOCS_REGION_START(migration-lazy-implicit-siblings)
        item(key = "account", contentRevision = account.version) {
            Text(account.name)
            Text(account.status)
        }
        // DOCS_REGION_END(migration-lazy-implicit-siblings)
    }
}

fun UiTreeBuilder.explicitRootMigrationSample(account: Account) {
    LazyColumn {
        // DOCS_REGION_START(migration-lazy-explicit-root)
        item(key = "account", contentRevision = account.version) {
            Column {
                Text(account.name)
                Text(account.status)
            }
        }
        // DOCS_REGION_END(migration-lazy-explicit-root)
    }
}

fun UiTreeBuilder.androidViewReuseMigrationSample(item: Message) {
    // DOCS_REGION_START(migration-lazy-android-view-reuse)
    AndroidView(
        factory = { context -> PlayerView(context) },
        update = { view -> bindPlayer(view as PlayerView, item) },
        onReset = { view -> resetPlayer(view as PlayerView) },
        onRelease = { view -> (view as PlayerView).release() },
    )
    // DOCS_REGION_END(migration-lazy-android-view-reuse)
}

fun lazyItemTableMigrationSample(itemModels: List<LazyListItem>) {
    // DOCS_REGION_START(migration-lazy-item-table)
    LazyColumnNodeProps(
        contentPadding = LazyContentPadding.None,
        spacing = UiDp.Zero,
        items = itemModels.asLazyItemTable(),
    )
    // DOCS_REGION_END(migration-lazy-item-table)
}

private fun UiTreeBuilder.MessageRow(message: Message) {
    Text(message.title)
}

private fun UiTreeBuilder.AccountPage(account: Account) {
    Text(account.name)
}

private class PlayerView(context: Context) : View(context) {
    fun release() = Unit
}

private fun bindPlayer(view: PlayerView, item: Message) {
    view.contentDescription = item.title
}

private fun resetPlayer(view: PlayerView) {
    view.contentDescription = null
}
