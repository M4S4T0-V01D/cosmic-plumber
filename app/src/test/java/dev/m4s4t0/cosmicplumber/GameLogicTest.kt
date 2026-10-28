package dev.m4s4t0.cosmicplumber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipePuzzleTest {
    @Test fun scrambledBoardIsUnsolvedButSolutionWorks() {
        for (seed in 0 until 200) {
            val cols = 5 + seed % 6
            val rows = 4 + seed % 4
            val p = PipePuzzle(cols, rows, seed)
            assertFalse("seed $seed starts solved", p.solved())
            for (i in p.masks.indices) {
                // Every scrambled tile must be a rotation of its solved shape.
                var m = p.masks[i]
                var turns = 0
                while (m != p.solution[i] && turns < 4) { m = PipePuzzle.rot(m); turns++ }
                assertTrue("seed $seed tile $i isn't a rotation", turns < 4)
            }
            p.solution.copyInto(p.masks)
            assertTrue("seed $seed solution doesn't solve", p.solved())
        }
    }

    @Test fun rotateTurnsClockwise() {
        assertEquals(PipePuzzle.E, PipePuzzle.rot(PipePuzzle.N))
        assertEquals(PipePuzzle.N, PipePuzzle.rot(PipePuzzle.W))
        assertEquals(PipePuzzle.E or PipePuzzle.W, PipePuzzle.rot(PipePuzzle.N or PipePuzzle.S))
    }
}

class RhythmJudgeTest {
    private val notes = listOf(1f to 0, 2f to 1, 3f to 2, 4f to 0)

    @Test fun perfectGoodAndMiss() {
        val j = RhythmJudge(notes, 0.1f)
        assertEquals(RhythmJudge.Hit.PERFECT, j.tap(0, 1.02f))
        assertEquals(RhythmJudge.Hit.GOOD, j.tap(1, 2.08f))
        assertEquals(RhythmJudge.Hit.WHIFF, j.tap(1, 3f)) // wrong lane
        assertEquals(1, j.advance(3.5f))
        assertEquals(RhythmJudge.Hit.PERFECT, j.tap(0, 4f))
        assertTrue(j.finished)
        assertEquals((2 + 0.6f) / 4f, j.score, 1e-4f)
        assertEquals(2, j.maxCombo)
    }

    @Test fun noteCanOnlyBeHitOnce() {
        val j = RhythmJudge(notes, 0.1f)
        j.tap(0, 1f)
        assertEquals(RhythmJudge.Hit.WHIFF, j.tap(0, 1.01f))
        assertEquals(0, j.combo)
    }

    @Test fun silenceFailsTheJob() {
        val j = RhythmJudge(notes, 0.1f)
        assertEquals(4, j.advance(10f))
        assertEquals(0f, j.score, 0f)
        assertTrue(j.meter < 0.6f)
    }
}

class PayoutTest {
    private val job = Job(1, "A", "B", "C", 0, 0, Kind.PIPES, 1, 100, false)

    @Test fun scalesWithScoreNightAndAlderman() {
        assertEquals(50, Catalog.payout(job, 0f, false, 0))
        assertEquals(150, Catalog.payout(job, 1f, false, 0))
        assertEquals(225, Catalog.payout(job, 1f, true, 0))
        assertEquals(195, Catalog.payout(job, 1f, false, 2))
    }
}
