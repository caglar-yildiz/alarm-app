package com.caglaryildiz.alarm

import kotlin.random.Random

internal enum class MathDifficulty(val label: String) {
    EASY("Kolay"),
    MEDIUM("Orta"),
    HARD("Zor")
}

internal data class MathSettings(
    val difficulty: MathDifficulty = MathDifficulty.MEDIUM,
    val count: Int = 1
) {
    companion object {
        val counts = 1..5

        fun from(difficulty: String?, count: Int) = MathSettings(
            MathDifficulty.entries.firstOrNull { it.name == difficulty } ?: MathDifficulty.MEDIUM,
            count.coerceIn(counts)
        )
    }
}

internal data class MathQuestion(val expression: String, val answer: Int)

internal class MathMission(private val settings: MathSettings, private val random: Random = Random.Default) {
    private val questions = buildList {
        while (size < settings.count) {
            val question = generate()
            if (none { it.expression == question.expression }) add(question)
        }
    }
    private var solved = 0
    val question get() = questions.getOrNull(solved)
    val progress get() = "Soru ${minOf(solved + 1, settings.count)} / ${settings.count}"

    fun submit(answer: Int?): MathResult {
        val current = question ?: return MathResult.COMPLETE
        if (answer != current.answer) return MathResult.INCORRECT
        solved++
        return if (question == null) MathResult.COMPLETE else MathResult.NEXT
    }

    private fun generate(): MathQuestion = when (settings.difficulty) {
        MathDifficulty.EASY -> addition(random.nextInt(1, 10), random.nextInt(1, 10))
        MathDifficulty.MEDIUM -> addition(random.nextInt(12, 50), random.nextInt(8, 40))
        MathDifficulty.HARD -> {
            val first = random.nextInt(3, 13)
            val second = random.nextInt(3, 13)
            val third = random.nextInt(10, 40)
            MathQuestion("($first × $second) + $third", first * second + third)
        }
    }

    private fun addition(first: Int, second: Int) = MathQuestion("$first + $second", first + second)
}

internal enum class MathResult { INCORRECT, NEXT, COMPLETE }
