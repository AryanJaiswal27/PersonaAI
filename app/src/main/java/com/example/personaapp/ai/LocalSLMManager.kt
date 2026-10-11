package com.example.personaapp.ai

import android.content.Context
import com.example.personaapp.data.AppLogger
import kotlin.math.sqrt

class LocalSLMManager(private val context: Context) {

    fun generateKeywordVector(text: String): Map<String, Float> {
        val words = text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split("\\s+".toRegex())
            .filter { it.length > 2 && it !in STOP_WORDS }

        if (words.isEmpty()) return emptyMap()

        val frequencies = mutableMapOf<String, Float>()
        for (w in words) {
            frequencies[w] = (frequencies[w] ?: 0f) + 1f
        }

        // Normalize
        val norm = sqrt(frequencies.values.sumOf { (it * it).toDouble() }).toFloat()
        return if (norm > 0f) frequencies.mapValues { it.value / norm } else frequencies
    }

    fun cosineSimilarity(v1: Map<String, Float>, v2: Map<String, Float>): Float {
        var dot = 0f
        for ((k, score1) in v1) {
            val score2 = v2[k] ?: 0f
            dot += score1 * score2
        }
        return dot
    }

    fun retrieveRelevantMemories(query: String, summaries: Map<String, String>): List<Pair<String, String>> {
        val queryVec = generateKeywordVector(query)
        if (queryVec.isEmpty()) return summaries.entries.take(3).map { it.key to it.value }

        val scored = summaries.entries.map { entry ->
            val summaryVec = generateKeywordVector(entry.value)
            val score = cosineSimilarity(queryVec, summaryVec)
            entry to score
        }.sortedByDescending { it.second }

        AppLogger.log("LOCAL_SLM", "SLM vector similarity ranked ${summaries.size} memory files for query: \"$query\"")
        return scored.take(3).map { it.first.key to it.first.value }
    }

    fun retrieveSubconsciousMemories(query: String, archiveLines: List<String>): List<String> {
        val queryVec = generateKeywordVector(query)
        if (queryVec.isEmpty()) return archiveLines.takeLast(3)

        val scored = archiveLines.map { line ->
            val summaryVec = generateKeywordVector(line)
            val score = cosineSimilarity(queryVec, summaryVec)
            line to score
        }.sortedByDescending { it.second }

        AppLogger.log("LOCAL_SLM", "SLM vector similarity ranked ${archiveLines.size} archived memories for query: \"$query\"")
        return scored.filter { it.second > 0f }.take(3).map { it.first }
    }

    companion object {
        private val STOP_WORDS = setOf(
            "the", "and", "is", "in", "it", "of", "to", "for", "with", "on", "that", "this", "are", "was", "were", "been", "have", "has", "had"
        )
    }
}
