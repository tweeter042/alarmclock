package io.github.tweeter042.alarmclock.core.model.alarm

import kotlinx.serialization.Serializable

@Serializable
enum class MissionType {
    /** Solve arithmetic problems. count = number of problems. */
    MATH,
    /** Shake the phone. count = number of shakes. */
    SHAKE,
    /** Scan one of the registered QR codes / barcodes (targetIds = RegisteredCode ids). count unused. */
    BARCODE,
    /** Photograph a registered object (targetIds = PhotoTarget ids). count unused. */
    PHOTO,
    /** Walk. count = number of steps. */
    STEPS,
    /** Type the shown sentence exactly. count = number of sentences. */
    TYPING,
    /** Repeat a flashed tile pattern. count = number of rounds. */
    MEMORY,
    /** Handwrite the shown word (S Pen or finger). count = number of words. */
    HANDWRITING,
}

@Serializable
enum class MissionDifficulty {
    EASY, MEDIUM, HARD, EXPERT;

    fun raisedBy(levels: Int): MissionDifficulty =
        entries[(ordinal + levels.coerceAtLeast(0)).coerceAtMost(entries.lastIndex)]
}

@Serializable
data class MissionConfig(
    val type: MissionType,
    val difficulty: MissionDifficulty = MissionDifficulty.MEDIUM,
    /** Meaning depends on [type]; see [MissionType]. */
    val count: Int = MissionDefaults.defaultCount(type),
    /** RegisteredCode ids (BARCODE) or PhotoTarget ids (PHOTO). Any one of them is accepted. */
    val targetIds: List<Long> = emptyList(),
    /** HANDWRITING: accept stylus (S Pen) input only. */
    val stylusOnly: Boolean = false,
)

object MissionDefaults {
    fun defaultCount(type: MissionType): Int = when (type) {
        MissionType.MATH -> 3
        MissionType.SHAKE -> 30
        MissionType.BARCODE -> 1
        MissionType.PHOTO -> 1
        MissionType.STEPS -> 30
        MissionType.TYPING -> 2
        MissionType.MEMORY -> 3
        MissionType.HANDWRITING -> 2
    }

    /** Valid range for the count slider in the editor. */
    fun countRange(type: MissionType): IntRange = when (type) {
        MissionType.MATH -> 1..10
        MissionType.SHAKE -> 10..200
        MissionType.BARCODE -> 1..1
        MissionType.PHOTO -> 1..1
        MissionType.STEPS -> 10..300
        MissionType.TYPING -> 1..5
        MissionType.MEMORY -> 1..8
        MissionType.HANDWRITING -> 1..5
    }
}
