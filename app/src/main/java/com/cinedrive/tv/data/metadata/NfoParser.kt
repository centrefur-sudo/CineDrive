package com.cinedrive.tv.data.metadata

import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Lenient parser for Kodi-style .nfo files (XmlPullParser, no DOM — NFOs can
 * be large and parse per-file during indexing).
 *
 * Why lenient: .nfo files in the wild are hand-edited and exporter-dependent.
 * Common shapes are all supported:
 *
 *  • modern movie  — <movie><title>…</title><ratings><rating name="imdb" default="true" max="10"><value>7.6</value></rating></ratings>…
 *  • legacy movie  — <movie><title>…</title><rating>7.6</rating><year>1998</year>…
 *  • episode       — <episodedetails><title>…</title><season>4</season><episode>12</episode><showtitle>…</showtitle>…
 *  • show          — <tvshow><title>…</title><premiered>2001-09-22</premiered>…
 *  • BOM / leading whitespace / trailing junk after the root element
 *
 * A file that fails to parse returns null and the caller falls back to the
 * file name — a broken NFO must never break an index run.
 */
@Singleton
class NfoParser @Inject constructor() {

    fun parse(content: String): NfoData? {
        val cleaned = content.trimStart('\uFEFF').trim()
        if (cleaned.isEmpty()) return null
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(cleaned))
            parseStream(parser)
        } catch (error: Throwable) {
            // Malformed XML, entity junk, OOM-safe bailout: no NFO is fatal.
            null
        }
    }

    private fun parseStream(parser: XmlPullParser): NfoData? {
        var kind: NfoKind? = null
        var rootName: String? = null
        var rootDepth = -1
        // True only when the matched root element was explicitly closed.
        // kxml2 delivers END_DOCUMENT at EOF without throwing on truncated
        // documents, so this flag is what rejects cut-off NFOs.
        var rootClosed = false

        var title: String? = null
        var originalTitle: String? = null
        var plot: String? = null
        var outline: String? = null
        var year: Int? = null
        var premiered: String? = null
        var aired: String? = null
        var runtimeMin: Int? = null
        var runtimeSeconds: Int? = null
        var season: Int? = null
        var episode: Int? = null
        var showTitle: String? = null
        var thumb: String? = null
        val directors = ArrayList<String>()

        // Rating candidates: name → value; the default="true" one wins.
        val ratings = ArrayList<Pair<String?, Float>>()
        var defaultRatingName: String? = null

        // Generic leaf-capture state: text of one interesting tag at a time.
        var captureTag: String? = null
        val captureBuf = StringBuilder()

        // Rating sub-state machine (handles nested <value>).
        var ratingName: String? = null
        var ratingText: StringBuilder? = null
        var inRatingValue = false

        var thumbIsSpoiler = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    if (kind == null) {
                        kind = ROOT_KINDS[name]
                        if (kind != null) {
                            // Remember the root element so we can stop at its
                            // matching END_TAG: NFOs with trailing junk or a
                            // second concatenated <movie> block stay parseable.
                            rootName = name
                            rootDepth = parser.depth
                        }
                    }
                    when {
                        name == "rating" -> {
                            ratingName = parser.getAttributeValue(null, "name")
                            if (parser.getAttributeValue(null, "default") == "true") {
                                defaultRatingName = ratingName
                            }
                            ratingText = StringBuilder()
                            inRatingValue = false
                        }

                        name == "value" && ratingText != null -> {
                            inRatingValue = true
                        }

                        name == "thumb" -> {
                            thumbIsSpoiler =
                                parser.getAttributeValue(null, "spoiler") == "true"
                            if (captureTag == null) {
                                captureTag = name
                                captureBuf.setLength(0)
                            }
                        }

                        name in TEXT_TAGS && captureTag == null -> {
                            captureTag = name
                            captureBuf.setLength(0)
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text
                    if (text.isNotEmpty()) {
                        if (ratingText != null && (inRatingValue || captureTag == null)) {
                            // <value>7.5</value> or legacy <rating>7.5</rating>
                            ratingText.append(text)
                        }
                        if (captureTag != null) {
                            captureBuf.append(text)
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    if (name == rootName && parser.depth == rootDepth) {
                        rootClosed = true
                        break
                    }
                    when {
                        name == "rating" && ratingText != null -> {
                            val value = ratingText.toString()
                                .trim()
                                .substringBefore(' ')
                                .toFloatOrNull()
                            if (value != null) ratings.add(ratingName to value)
                            ratingName = null
                            ratingText = null
                            inRatingValue = false
                        }

                        name == "value" && inRatingValue -> inRatingValue = false

                        name == captureTag -> {
                            val text = captureBuf.toString().trim()
                            captureTag = null
                            if (name == "thumb" && thumbIsSpoiler) {
                                // Skip spoiler thumbnails, keep looking.
                            } else if (text.isNotEmpty()) {
                                when (name) {
                                    "title" -> if (title == null) title = text
                                    "originaltitle" -> if (originalTitle == null) originalTitle = text
                                    "showtitle" -> if (showTitle == null) showTitle = text
                                    "plot" -> if (plot == null) plot = text
                                    "outline" -> if (outline == null) outline = text
                                    "year" -> if (year == null) year = text.toIntOrNull()
                                    "premiered" -> if (premiered == null) premiered = text
                                    "aired" -> if (aired == null) aired = text
                                    "runtime" ->
                                        if (runtimeMin == null) {
                                            runtimeMin = text.takeWhile { it.isDigit() }
                                                .takeIf { it.isNotEmpty() }
                                                ?.toIntOrNull()
                                        }
                                    "durationinseconds" ->
                                        if (runtimeSeconds == null) runtimeSeconds = text.toIntOrNull()
                                    "season" -> if (season == null) season = text.toIntOrNull()
                                    "episode" -> if (episode == null) episode = text.toIntOrNull()
                                    "director" -> if (text !in directors) directors.add(text)
                                    "thumb" -> if (thumb == null) thumb = text
                                }
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }

        // Reject documents whose root never closed (truncated files) as well
        // as files without a recognized Kodi root element.
        if (kind == null || !rootClosed) return null

        val resolvedYear = year
            ?: premiered?.let { YEAR_IN_IT.find(it)?.value?.toIntOrNull() }
            ?: aired?.let { YEAR_IN_IT.find(it)?.value?.toIntOrNull() }

        val resolvedRuntime = runtimeMin ?: runtimeSeconds?.let { secs ->
            if (secs > 0) (secs + 59) / 60 else null
        }

        val resolvedRating = ratings.lastOrNull { it.first == defaultRatingName }?.second
            ?: ratings.firstOrNull()?.second

        return NfoData(
            kind = kind,
            title = title,
            originalTitle = originalTitle,
            plot = plot,
            outline = outline,
            director = directors.joinToString(", ").takeIf { it.isNotEmpty() },
            year = resolvedYear,
            rating = resolvedRating,
            runtimeMin = resolvedRuntime,
            season = season,
            episode = episode,
            showTitle = showTitle,
            thumb = thumb,
        )
    }

    private companion object {
        val ROOT_KINDS = mapOf(
            "movie" to NfoKind.MOVIE,
            "episodedetails" to NfoKind.EPISODE,
            "tvshow" to NfoKind.TV_SHOW,
        )

        val TEXT_TAGS = setOf(
            "title", "originaltitle", "showtitle", "plot", "outline",
            "year", "premiered", "aired", "runtime", "durationinseconds",
            "season", "episode", "director", "thumb",
        )

        val YEAR_IN_IT = Regex("""(?:19|20)\d{2}""")
    }
}
