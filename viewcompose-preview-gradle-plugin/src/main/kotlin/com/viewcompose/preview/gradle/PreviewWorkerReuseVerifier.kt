package com.viewcompose.preview.gradle

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Hard gate used by tests and manual verification before enabling a new reuse policy. */
internal object PreviewWorkerReuseVerifier {
    fun requireEquivalent(
        warmImage: File,
        coldImage: File,
        warmTree: File,
        coldTree: File,
    ) {
        require(warmImage.isFile && coldImage.isFile) {
            "Worker reuse verification requires warm and cold preview images."
        }
        require(warmTree.isFile && coldTree.isFile) {
            "Worker reuse verification requires warm and cold render snapshots."
        }
        val warmImageHash = warmImage.sha256()
        val coldImageHash = coldImage.sha256()
        require(warmImageHash == coldImageHash) {
            "Warm and cold preview pixels differ: warm=$warmImageHash cold=$coldImageHash."
        }
        val warmSnapshot = normalizedSnapshot(warmTree)
        val coldSnapshot = normalizedSnapshot(coldTree)
        require(warmSnapshot == coldSnapshot) {
            "Warm and cold preview structures differ after removing tooling-only node ids."
        }
    }

    private fun normalizedSnapshot(file: File): JsonElement {
        return normalize(PreviewJson.format.parseToJsonElement(file.readText()))
    }

    private fun normalize(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.entries
                .filterNot { (name, _) -> name == TOOLING_NODE_ID_FIELD }
                .associate { (name, value) -> name to normalize(value) },
        )

        is JsonArray -> JsonArray(element.map(::normalize))
        else -> element
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }
}

private object PreviewJson {
    val format = kotlinx.serialization.json.Json { ignoreUnknownKeys = false }
}

private const val TOOLING_NODE_ID_FIELD = "nodeId"
