package com.hourlysteps.ui

import java.text.NumberFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance()
private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun formatSteps(value: Int): String = numberFormat.format(value)

/** "8:50 AM" style label for the :50 check of [hour]. */
fun formatSlot(hour: Int): String = LocalTime.of(hour, 50).format(timeFormat)

fun formatTime(time: LocalTime): String = time.format(timeFormat)
