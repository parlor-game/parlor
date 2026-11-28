package com.parlor.app.storage

import com.parlor.storage.snapshot.SnapshotProtectionException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopSnapshotFileSystemTest {
    @Test
    fun emptyEncryptedSnapshotRoundTrips() = runTest {
        val root = Files.createTempDirectory("parlor-desktop-snapshot-test")
        try {
            val fileSystem = DesktopSnapshotFileSystem(
                baseDir = root.resolve("snapshots"),
                keyPath = root.resolve("snapshot-key.bin"),
                ioDispatcher = StandardTestDispatcher(testScheduler),
            )

            fileSystem.write("empty.snapshot.json", ByteArray(0))

            assertContentEquals(ByteArray(0), fileSystem.read("empty.snapshot.json"))
        } finally {
            assertTrue(root.toFile().deleteRecursively())
        }
    }

    @Test
    fun authenticatedOnlyReadCannotMigratePlaintextHostAuthority() = runTest {
        val root = Files.createTempDirectory("parlor-host-authority-test")
        try {
            val path = root.resolve("snapshots")
            val fileSystem = DesktopSnapshotFileSystem(path, root.resolve("key.bin"), StandardTestDispatcher(testScheduler))
            val name = "hosted-party.checkpoint"
            val plaintext = "{}".encodeToByteArray()
            Files.write(path.resolve(name), plaintext)

            assertFailsWith<SnapshotProtectionException> { fileSystem.readAuthenticated(name) }
            assertContentEquals(plaintext, Files.readAllBytes(path.resolve(name)))

            fileSystem.write(name, plaintext)
            assertContentEquals(plaintext, fileSystem.readAuthenticated(name))
        } finally {
            assertTrue(root.toFile().deleteRecursively())
        }
    }
}
