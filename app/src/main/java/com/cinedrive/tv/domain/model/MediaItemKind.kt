package com.cinedrive.tv.domain.model

/**
 * How a media item was classified by the library engine.
 *
 * MOVIE   — a standalone film (single file, own .nfo, or no episode markers).
 * EPISODE — one episode of a show, grouped under a [Show] row by folder.
 */
enum class MediaItemKind {
    MOVIE,
    EPISODE,
}
