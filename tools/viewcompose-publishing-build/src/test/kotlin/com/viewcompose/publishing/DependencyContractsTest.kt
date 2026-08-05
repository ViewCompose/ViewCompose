package com.viewcompose.publishing

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DependencyContractsTest {
    @Test
    fun `loader requires an exact contract for every registered artifact`() {
        val file = Files.createTempFile("dependency-contracts", ".properties").toFile().apply {
            writeText(
                """
                schema.version=1
                module.viewcompose-runtime=api=;implementation=;compileOnly=;runtimeOnly=
                module.viewcompose-widget-core=api=viewcompose-runtime;implementation=;compileOnly=;runtimeOnly=
                """.trimIndent(),
            )
        }

        val contracts = PublishedDependencyContracts.load(
            file = file,
            registeredArtifacts = setOf("viewcompose-runtime", "viewcompose-widget-core"),
        )

        assertEquals(
            setOf("viewcompose-runtime"),
            contracts.single { it.artifact == "viewcompose-widget-core" }
                .dependencies.getValue("api"),
        )
        assertThrows(IllegalStateException::class.java) {
            PublishedDependencyContracts.load(
                file = file,
                registeredArtifacts = setOf(
                    "viewcompose-runtime",
                    "viewcompose-widget-core",
                    "viewcompose-host-android",
                ),
            )
        }
    }

    @Test
    fun `loader rejects one dependency in multiple exposure configurations`() {
        val file = Files.createTempFile("duplicate-dependency-contract", ".properties").toFile().apply {
            writeText(
                """
                schema.version=1
                module.viewcompose-runtime=api=;implementation=;compileOnly=;runtimeOnly=
                module.viewcompose-widget-core=api=viewcompose-runtime;implementation=viewcompose-runtime;compileOnly=;runtimeOnly=
                """.trimIndent(),
            )
        }

        assertThrows(IllegalStateException::class.java) {
            PublishedDependencyContracts.load(
                file = file,
                registeredArtifacts = setOf("viewcompose-runtime", "viewcompose-widget-core"),
            )
        }
    }

    @Test
    fun `encoded contract round trips deterministically`() {
        val contract = PublishedDependencyContract(
            artifact = "viewcompose-host-android",
            dependencies = mapOf(
                "api" to setOf("viewcompose-runtime", "viewcompose-widget-core"),
                "implementation" to setOf("viewcompose-renderer"),
                "compileOnly" to emptySet(),
                "runtimeOnly" to emptySet(),
            ),
        )

        assertEquals(contract, PublishedDependencyContract.decode(contract.encoded()))
    }
}
