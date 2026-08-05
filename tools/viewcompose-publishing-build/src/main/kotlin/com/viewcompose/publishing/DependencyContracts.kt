package com.viewcompose.publishing

import java.io.File
import java.util.Properties

internal data class PublishedDependencyContract(
    val artifact: String,
    val dependencies: Map<String, Set<String>>,
) {
    fun encoded(): String = buildString {
        append(artifact)
        PUBLISHED_DEPENDENCY_CONFIGURATIONS.forEach { configuration ->
            append('|')
            append(configuration)
            append('=')
            append(dependencies.getValue(configuration).sorted().joinToString(","))
        }
    }

    companion object {
        fun decode(value: String): PublishedDependencyContract {
            val fields = value.split('|')
            check(fields.size == PUBLISHED_DEPENDENCY_CONFIGURATIONS.size + 1) {
                "Invalid dependency contract '$value'."
            }
            val artifact = fields.first()
            val dependencies = fields.drop(1).associate { field ->
                val configuration = field.substringBefore('=')
                check(configuration in PUBLISHED_DEPENDENCY_CONFIGURATIONS) {
                    "Unknown dependency configuration '$configuration' in '$value'."
                }
                val modules = field.substringAfter('=', missingDelimiterValue = "")
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                check(modules.distinct().size == modules.size) {
                    "Duplicate dependency in '$value'."
                }
                configuration to modules.toSet()
            }
            check(dependencies.keys == PUBLISHED_DEPENDENCY_CONFIGURATIONS.toSet()) {
                "Dependency contract '$value' must declare every supported configuration."
            }
            return PublishedDependencyContract(artifact, dependencies)
        }
    }
}

internal object PublishedDependencyContracts {
    fun load(
        file: File,
        registeredArtifacts: Set<String>,
    ): List<PublishedDependencyContract> {
        check(file.isFile) { "Missing dependency contract metadata: ${file.absolutePath}" }
        val properties = Properties().apply {
            file.inputStream().use(::load)
        }
        check(properties.getProperty("schema.version") == "1") {
            "Unsupported dependency contract schema '${properties.getProperty("schema.version")}'."
        }
        val modulePrefix = "module."
        val contracts = properties.stringPropertyNames()
            .filter { key -> key.startsWith(modulePrefix) }
            .map { key ->
                val artifact = key.removePrefix(modulePrefix)
                val declarations = properties.getProperty(key)
                    .split(';')
                    .associate { declaration ->
                        val configuration = declaration.substringBefore('=')
                        check(configuration in PUBLISHED_DEPENDENCY_CONFIGURATIONS) {
                            "$artifact -> unknown dependency configuration '$configuration'."
                        }
                        val modules = declaration.substringAfter('=', missingDelimiterValue = "")
                            .split(',')
                            .map(String::trim)
                            .filter(String::isNotEmpty)
                        check(modules.distinct().size == modules.size) {
                            "$artifact:$configuration -> duplicate dependency declaration."
                        }
                        configuration to modules.toSet()
                    }
                check(declarations.keys == PUBLISHED_DEPENDENCY_CONFIGURATIONS.toSet()) {
                    "$artifact -> dependency contract must declare " +
                        PUBLISHED_DEPENDENCY_CONFIGURATIONS.joinToString()
                }
                PublishedDependencyContract(
                    artifact = artifact,
                    dependencies = declarations,
                )
            }
            .sortedBy(PublishedDependencyContract::artifact)

        val contractArtifacts = contracts.map(PublishedDependencyContract::artifact).toSet()
        check(contractArtifacts == registeredArtifacts) {
            val missing = registeredArtifacts - contractArtifacts
            val unknown = contractArtifacts - registeredArtifacts
            "Dependency contracts must match registered Maven artifacts. " +
                "Missing: ${missing.sorted()}; unknown: ${unknown.sorted()}."
        }
        contracts.forEach { contract ->
            contract.dependencies.forEach { (configuration, dependencies) ->
                val unknownDependencies = dependencies - registeredArtifacts
                check(unknownDependencies.isEmpty()) {
                    "${contract.artifact}:$configuration -> unknown published dependencies " +
                        unknownDependencies.sorted()
                }
                check(contract.artifact !in dependencies) {
                    "${contract.artifact}:$configuration -> an artifact cannot depend on itself."
                }
            }
            val duplicates = contract.dependencies.values
                .flatten()
                .groupingBy(String::toString)
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
            check(duplicates.isEmpty()) {
                "${contract.artifact} -> dependencies declared in multiple configurations: " +
                    duplicates.sorted()
            }
        }
        return contracts
    }
}

internal val PUBLISHED_DEPENDENCY_CONFIGURATIONS = listOf(
    "api",
    "implementation",
    "compileOnly",
    "runtimeOnly",
)
