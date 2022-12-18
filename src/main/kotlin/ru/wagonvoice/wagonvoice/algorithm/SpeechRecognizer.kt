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
import java.util.concurrent.CompletableFuture
import javax.annotation.PostConstruct
import javax.sound.sampled.AudioInputStream
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
                    recognizeParallel(wavFileName, filePrefix)
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
        val ais = AudioSystem.getAudioInputStream(BufferedInputStream(inputStream))
        var totalBytesRead = 0
        val frameRate = ais.format.frameRate
        if (frameRate != 44100.0F) {
            println("framerate $frameRate is not recommended")
        }
        val recognizer = Recognizer(model, frameRate)
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

    fun recognizeParallel(fileName: String, fileId: String): String {
        println("will recognize $fileName at parallel")
        val wavFile = File(fileName)
        val audioStreams = mutableListOf<AudioInputStream>()
        val threads = 8
        for (i in 0 until threads) {
            val stream = AudioSystem.getAudioInputStream(BufferedInputStream(FileInputStream(wavFile)))
            audioStreams.add(stream)
            stream.skip(i*wavFile.length()/threads)
        }

        val futures: List<CompletableFuture<String>> = audioStreams.map { CompletableFuture.supplyAsync { recognizeVoice(it, wavFile.length()/threads) } }
        CompletableFuture.allOf(*futures.toTypedArray()).join()
        val text = futures.joinToString("") { it.get() }

        val file = File("$fileId.txt")
        file.writeText(text)
        println("####### recognized. written to $fileId.txt #########")
        return fileId
    }

    private fun recognizeVoice(ais: AudioInputStream, size: Long): String {
        var totalBytesRead = 0
        val frameRate = ais.format.frameRate
        if (frameRate != 44100.0F) {
            println("framerate $frameRate is not recommended")
        }
        val recognizer = Recognizer(model, frameRate)
        var progressBarDivider: Long = 0
        val b = ByteArray(4096)
        var nbytes: Int
        val text = StringBuilder()
        while (ais.read(b).also { nbytes = it } >= 0 && totalBytesRead<=size) {
            if (recognizer.acceptWaveForm(b, nbytes)) {
                val part = objectMapper.readTree(recognizer.result).get("text").textValue()
                text.append("$part | ")
            }
            totalBytesRead += nbytes
            progressBarDivider += 1
            if (progressBarDivider % 100 == 0L) {
                val progress = totalBytesRead.toDouble() / size.toDouble() * 100
                println(Thread.currentThread().name + ": progress $progress%")
            }
        }
        return text.toString()
    }
}