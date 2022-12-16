package ru.wagonvoice.wagonvoice

import com.fasterxml.jackson.databind.ObjectMapper
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.FileInputStream
import javax.sound.sampled.AudioSystem

class InventarizationParser {
}

fun main() {
    val model = Model("vosk-model-ru-0.22")
    val recognizer = Recognizer(model, 44100.0F)
    val objectMapper = ObjectMapper()
    var nbytes: Int
    val b = ByteArray(4096)
    val text = StringBuilder()
    val ais = AudioSystem.getAudioInputStream(BufferedInputStream(FileInputStream("train_audio.wav")))
    while (ais.read(b).also { nbytes = it } >= 0) {
        if (recognizer.acceptWaveForm(b, nbytes)) {
            val part = objectMapper.readTree(recognizer.result).get("text").textValue()
            text.append("$part ")
        }
    }

    println(text)
}