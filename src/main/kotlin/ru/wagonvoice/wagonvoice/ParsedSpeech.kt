package ru.wagonvoice.wagonvoice

data class ParsedSpeech(
    val preamble: String,
    val headers: List<String>,
    val data: List<Map<String, String>>
)