package com.cinedrive.tv.data.db

import androidx.room.TypeConverter

/**
 * Room type converters.
 *
 * Subtitle lists are stored newline-joined: absolute POSIX paths can never
 * contain a raw '\n', so this is lossless while keeping a single row (no
 * join table, no N+1 when the player needs the track list).
 */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString("\n")

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split("\n")
}
