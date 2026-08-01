package com.viewcompose.preview.gradle

import java.io.File
import java.security.MessageDigest

/**
 * Content fingerprint that is deterministic across checkout locations.
 */
internal object PreviewInputFingerprint {
    fun calculate(groups: Map<String, Collection<File>>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        groups.toSortedMap().forEach { (role, roots) ->
            digest.updateUtf8("role:$role\n")
            val entries = roots
                .flatMap(::entries)
                .sortedWith(compareBy(FingerprintEntry::logicalPath, FingerprintEntry::contentHash))
            entries.forEach { entry ->
                digest.updateUtf8("path:${entry.logicalPath}\n")
                digest.updateUtf8("content:${entry.contentHash}\n")
            }
        }
        return digest.digest().toHex()
    }

    fun calculateByGroup(groups: Map<String, Collection<File>>): Map<String, String> {
        return groups.toSortedMap().mapValues { (role, roots) ->
            calculate(mapOf(role to roots))
        }
    }

    fun combine(values: Map<String, String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        values.toSortedMap().forEach { (role, value) ->
            digest.updateUtf8("role:$role\n")
            digest.updateUtf8("value:$value\n")
        }
        return digest.digest().toHex()
    }

    private fun entries(root: File): List<FingerprintEntry> {
        if (!root.exists()) {
            return listOf(
                FingerprintEntry(
                    logicalPath = "${root.name}/<missing>",
                    contentHash = sha256(byteArrayOf()).toHex(),
                ),
            )
        }
        if (root.isFile) {
            return listOf(
                FingerprintEntry(
                    logicalPath = root.name,
                    contentHash = root.inputStream().use(::sha256).toHex(),
                ),
            )
        }
        return root.walkTopDown()
            .filter(File::isFile)
            .map { file ->
                FingerprintEntry(
                    logicalPath = "${root.name}/${file.relativeTo(root).invariantSeparatorsPath}",
                    contentHash = file.inputStream().use(::sha256).toHex(),
                )
            }
            .toList()
    }

    private fun sha256(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    private fun sha256(input: java.io.InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
        return digest.digest()
    }

    private fun MessageDigest.updateUtf8(value: String) {
        update(value.toByteArray(Charsets.UTF_8))
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }

    private data class FingerprintEntry(
        val logicalPath: String,
        val contentHash: String,
    )
}
