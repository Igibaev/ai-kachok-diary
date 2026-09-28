package com.fitcoach.app.domain.program

import com.fitcoach.app.domain.model.Goal
import com.fitcoach.app.domain.model.Level
import com.fitcoach.app.domain.model.Restriction
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramCatalogTest {

    private fun workout(programKey: String, planKey: String, completed: Boolean = true) = Workout(
        id = "$programKey-$planKey-${System.nanoTime()}", date = 0L, programKey = programKey, planKey = planKey,
        phaseName = "", weekNumber = 1, isCompleted = completed, durationMinutes = 40, painLevel = 0, notes = ""
    )

    @Test
    fun `catalog has three programs with 12 weeks and 3 phases each`() {
        assertEquals(3, ProgramCatalog.programs.size)
        assertEquals(setOf("START_3", "SLIM_3", "MUSCLE_4"), ProgramCatalog.programs.map { it.key }.toSet())
        ProgramCatalog.programs.forEach { p ->
            assertEquals(3, p.phases.size)
            assertEquals(12, p.totalWeeks)
            assertEquals(1..4, p.phases[0].weeks)
            assertEquals(5..8, p.phases[1].weeks)
            assertEquals(9..12, p.phases[2].weeks)
            p.phases.forEach { ph -> assertTrue(ph.templates.isNotEmpty()) }
        }
        assertEquals(4, ProgramCatalog.get("MUSCLE_4")!!.daysPerWeek)
        assertEquals(3, ProgramCatalog.get("START_3")!!.daysPerWeek)
    }

    @Test
    fun `template keys are unique across catalog and exercise ids unique within template`() {
        val keys = ProgramCatalog.programs.flatMap { p -> p.phases.flatMap { it.templates.map { t -> t.key } } }
        assertEquals(keys.size, keys.toSet().size)
        ProgramCatalog.programs.flatMap { p -> p.phases.flatMap { it.templates } }.forEach { t ->
            val ids = t.exercises.map { it.id }
            assertEquals("duplicate exercise id in ${t.key}", ids.size, ids.toSet().size)
            t.exercises.forEach { ex ->
                assertEquals("${ex.id} must have 2 alternatives", 2, ex.alternatives.size)
                assertTrue(ex.muscleGroup.isNotBlank())
                assertTrue(ex.sets.isNotEmpty())
            }
        }
    }

    @Test
    fun `weights are hints not kilograms`() {
        ProgramCatalog.programs.flatMap { p -> p.phases.flatMap { it.templates } }
            .flatMap { it.exercises }.flatMap { it.sets }
            .forEach { s -> assertFalse("weight looks like kg: ${s.weight}", Regex("^\\d+\\s*кг$").matches(s.weight)) }
    }

    @Test
    fun `legacy BEGINNER_3 key resolves to START_3`() {
        assertEquals("START_3", ProgramCatalog.getOrDefault("BEGINNER_3").key)
        assertEquals("START_3", ProgramCatalog.getOrDefault("unknown").key)
    }

    @Test
    fun `week is completed over daysPerWeek plus one, capped at 12`() {
        assertEquals(1, ProgramCatalog.weekFor(0, 3))
        assertEquals(1, ProgramCatalog.weekFor(2, 3))
        assertEquals(2, ProgramCatalog.weekFor(3, 3))
        assertEquals(5, ProgramCatalog.weekFor(12, 3))
        assertEquals(12, ProgramCatalog.weekFor(100, 3))
        assertEquals(2, ProgramCatalog.weekFor(4, 4))
    }

    @Test
    fun `nextWorkout cycles templates within phase and switches phase by week`() {
        val p = ProgramCatalog.get("START_3")!!
        val phase1 = p.phases[0].templates
        assertEquals(phase1[0].key, ProgramCatalog.nextWorkout(p, 0, 3).template.key)
        assertEquals(phase1[1].key, ProgramCatalog.nextWorkout(p, 1, 3).template.key)
        assertEquals(phase1[2].key, ProgramCatalog.nextWorkout(p, 2, 3).template.key)
        assertEquals(phase1[0].key, ProgramCatalog.nextWorkout(p, 3, 3).template.key)
        assertEquals(2, ProgramCatalog.nextWorkout(p, 3, 3).weekNumber)

        // 12 выполнено при 3/нед → неделя 5 → фаза 2, первая тренировка фазы
        val n = ProgramCatalog.nextWorkout(p, 12, 3)
        assertEquals(2, n.phase.index)
        assertEquals(p.phases[1].templates[0].key, n.template.key)
        assertEquals(13, n.ordinal)

        // 24 → неделя 9 → фаза 3
        assertEquals(3, ProgramCatalog.nextWorkout(p, 24, 3).phase.index)
        // после 36 — остаёмся в фазе 3 по кругу
        assertEquals(3, ProgramCatalog.nextWorkout(p, 40, 3).phase.index)
        assertEquals(12, ProgramCatalog.nextWorkout(p, 40, 3).weekNumber)
    }

    @Test
    fun `nextWorkout counts only completed workouts of the same program, ignoring activities`() {
        val p = ProgramCatalog.get("SLIM_3")!!
        val list = listOf(
            workout("SLIM_3", "L1_A"),
            workout("SLIM_3", "L1_B"),
            workout("SLIM_3", "L1_C", completed = false),
            workout("SLIM_3", "ACTIVITY_GROUP"),
            workout("MUSCLE_4", "M1_UA")
        )
        assertEquals(2, ProgramCatalog.completedInProgram(p, list))
        assertEquals("L1_C", ProgramCatalog.nextWorkout(p, list, 3).template.key)
    }

    @Test
    fun `muscle program uses 4 templates per phase with upper lower split`() {
        val p = ProgramCatalog.get("MUSCLE_4")!!
        p.phases.forEach { assertEquals(4, it.templates.size) }
        val titles = (0 until 4).map { ProgramCatalog.nextWorkout(p, it, 4).template.title }
        assertTrue(titles[0].startsWith("Верх"))
        assertTrue(titles[1].startsWith("Низ"))
        assertTrue(titles[2].startsWith("Верх"))
        assertTrue(titles[3].startsWith("Низ"))
    }

    @Test
    fun `recommendation follows goal, level and pregnancy`() {
        assertEquals("START_3", ProgramCatalog.recommend(UserProfile(goal = Goal.BACK_HEALTH, level = Level.ADVANCED)).key)
        assertEquals("START_3", ProgramCatalog.recommend(UserProfile(goal = Goal.GENERAL_FITNESS, level = Level.BEGINNER)).key)
        assertEquals("SLIM_3", ProgramCatalog.recommend(UserProfile(goal = Goal.FAT_LOSS, level = Level.BEGINNER)).key)
        assertEquals("SLIM_3", ProgramCatalog.recommend(UserProfile(goal = Goal.TONE, level = Level.INTERMEDIATE)).key)
        assertEquals("MUSCLE_4", ProgramCatalog.recommend(UserProfile(goal = Goal.MUSCLE_GAIN, level = Level.INTERMEDIATE)).key)
        assertEquals("START_3", ProgramCatalog.recommend(UserProfile(goal = Goal.MUSCLE_GAIN, level = Level.BEGINNER)).key)

        val preg = ProgramCatalog.recommendation(UserProfile(goal = Goal.MUSCLE_GAIN, level = Level.ADVANCED, restrictions = setOf(Restriction.PREGNANCY_POSTPARTUM)))
        assertEquals("START_3", preg.program.key)
        assertTrue(preg.needsTrainerConsult)
        assertFalse(ProgramCatalog.recommendation(UserProfile()).needsTrainerConsult)
    }

    @Test
    fun `applyRestrictions swaps avoided exercises to first alternative with alt id`() {
        val p = ProgramCatalog.get("MUSCLE_4")!!
        val t = p.phases[0].templates.first { it.key == "M1_LA" }
        val squat = t.exercises.first { it.id == "m1la_1" }
        assertTrue(Restriction.BACK in squat.avoidFor)

        val safe = ProgramCatalog.applyRestrictions(t, setOf(Restriction.BACK))
        val swapped = safe.exercises.first { ProgramCatalog.baseIdOf(it.id) == "m1la_1" }
        assertEquals(squat.alternatives.first(), swapped.name)
        assertEquals("m1la_1~1", swapped.id)
        assertNotEquals(squat.name, swapped.name)

        // без ограничений — без изменений
        assertEquals(t, ProgramCatalog.applyRestrictions(t, emptySet()))
        // не затронутые упражнения остаются
        assertEquals(t.exercises.size, safe.exercises.size)
    }

    @Test
    fun `findTemplate works across programs and estimated minutes are sane`() {
        val t = ProgramCatalog.findTemplate("SLIM_3", "L2_B")
        assertNotNull(t)
        assertEquals("L2_B", t!!.key)
        assertNotNull(ProgramCatalog.findTemplate("", "M3_UB"))
        ProgramCatalog.programs.flatMap { p -> p.phases.flatMap { it.templates } }.forEach { tpl ->
            assertTrue("${tpl.key}: ${tpl.estimatedMinutes}", tpl.estimatedMinutes in 20..90)
        }
    }

    @Test
    fun `alternative ids round-trip`() {
        assertEquals("a", ProgramCatalog.alternativeId("a", 0))
        assertEquals("a~2", ProgramCatalog.alternativeId("a~1", 2))
        assertEquals("a", ProgramCatalog.baseIdOf("a~2"))
    }
}
