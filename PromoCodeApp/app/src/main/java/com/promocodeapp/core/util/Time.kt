package com.promocodeapp.core.util

import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

fun Clock.today(): LocalDate = LocalDate.now(this)

fun newId(): String = UUID.randomUUID().toString()

private val mediumDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun LocalDate.formatMedium(): String = format(mediumDate)
