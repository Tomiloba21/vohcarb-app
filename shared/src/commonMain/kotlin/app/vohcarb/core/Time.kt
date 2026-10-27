package app.vohcarb.core

import kotlin.time.Clock
import kotlin.time.Instant

// The database stores times as epoch milliseconds; the API uses ISO-8601 instants.

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun parseInstantMillis(iso: String): Long = Instant.parse(iso).toEpochMilliseconds()

fun isoInstant(millis: Long): String = Instant.fromEpochMilliseconds(millis).toString()

const val MINUTE_MILLIS = 60_000L
