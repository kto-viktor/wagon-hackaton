package ru.wagonvoice.wagonvoice.algorithm

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
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
    @Value("\${spring.main.web-application-type:}")
    private lateinit var applicationType: String

    @PostConstruct
    fun init() {
        if (applicationType == "none") {
            val filePrefix = "test_audio"
            val wavFileName = "$filePrefix.wav"
            println("offline mode. SpeechRecognizer init. Will recognize $wavFileName")
            if (File(wavFileName).exists()) {
                if (File("$filePrefix.txt").exists()) {
                    println("Seems like audio already parsed: i see $filePrefix.txt exists. Skipping")
                    return
                }
                Thread {
                    recognize(FileInputStream(wavFileName), 420350882, "test_audio")
                }.start()
            } else {
                println("file $wavFileName doesnt exists, skipping")
            }
        }
    }

    fun recognize(inputStream: InputStream, fileSizeBytes: Long, fileId: String): String {
        println("will recognize $fileId size $fileSizeBytes")
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
        println("####### recognized. written to $fileId.txt #########")
        return fileId
    }
}