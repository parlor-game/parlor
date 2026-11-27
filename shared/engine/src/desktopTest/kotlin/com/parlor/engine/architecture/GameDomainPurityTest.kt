package com.parlor.engine.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertEmpty
import kotlin.test.Test
import kotlin.test.assertTrue

class GameDomainPurityTest {
    @Test
    fun gameDomainsDoNotImportRoomOrSessionAdapters() {
        val games = listOf("egyptian-dominoes", "ghamza", "last-light", "mafia", "whodunit", "word-impostor")
        val forbidden = listOf(
            "com.parlor.networking.",
            "com.parlor.session.",
            "com.parlor.storage.",
            "com.parlor.transport.",
            "dev.p2pkit.",
        )

        games.forEach { game ->
            val domainFiles = Konsist.scopeFromModule("game-modes/$game").files.filter {
                val path = it.path.replace('\\', '/')
                "/src/commonMain/" in path && "/domain/" in path
            }
            assertTrue(domainFiles.isNotEmpty(), "No production domain sources found for $game")
            domainFiles.flatMap { it.imports }
                .filter { imported -> forbidden.any { imported.name.startsWith(it) } }
                .assertEmpty(additionalMessage = "$game domain imports a room, storage, or session adapter.")
        }
    }
}
