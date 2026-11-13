package com.caglaryildiz.alarm

import kotlin.random.Random

/** Each shape appears four times; match the four distinct targets in order. */
internal class ShapeMission(random: Random = Random.Default) {
    val board = List(16) { it % 4 }.shuffled(random)
    val targets = (0..3).shuffled(random)
    val selected = mutableSetOf<Int>()
    val progress get() = selected.size
    val complete get() = progress == targets.size

    fun pick(index: Int): Boolean {
        if (complete || index !in board.indices || index in selected) return false
        if (board[index] != targets[progress]) return false
        selected.add(index)
        return true
    }

    companion object {
        val symbols = listOf("●", "▲", "■", "◆")
        val names = listOf("Daire", "Üçgen", "Kare", "Elmas")
    }
}
