package ru.wagonvoice.wagonvoice

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.annotation.PostConstruct
import javax.sound.sampled.AudioSystem


@Service
class SpeechRecognizer(private val model: Model, private val objectMapper: ObjectMapper) {
    @PostConstruct
    fun init() {
//        println("SpeechRecognizer init. Will recognize train_audio.wav")
//        Thread {
//            recognize(FileInputStream("train_audio.wav"), 420350882)
//        }.start()
    }

    fun recognize(inputStream: InputStream, fileSizeBytes: Long): String {
        val fileId = System.currentTimeMillis().toString()
        var nbytes: Int
        val b = ByteArray(4096)
        val text = StringBuilder()
        val ais = AudioSystem.getAudioInputStream(BufferedInputStream(inputStream)) // попробовать читать параллельно BufferedInputStream
        var totalBytesRead = 0
        val recognizer = Recognizer(model, 44100.0F)
        var progressBarDivider: Long = 0
        while (ais.read(b).also { nbytes = it } >= 0) {
            if (recognizer.acceptWaveForm(b, nbytes)) {
                val part = objectMapper.readTree(recognizer.result).get("text").textValue()
                text.append("$part | ")
            }
            totalBytesRead += nbytes
            progressBarDivider += 1
            if (progressBarDivider % 100 == 0L) {
                val progress = totalBytesRead.toDouble() / fileSizeBytes.toDouble() * 100
                println("progress $progress%")
            }
        }
        val file = File("$fileId.txt")
        file.writeText(text.toString())
        println("written to $fileId.txt")
        return fileId
    }
}