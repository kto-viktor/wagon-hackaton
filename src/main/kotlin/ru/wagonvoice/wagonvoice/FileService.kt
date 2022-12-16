package ru.wagonvoice.wagonvoice

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedInputStream
import javax.sound.sampled.AudioSystem

@Service
class FileService(private val parser: SpeechToTableParser, private val sheetWriter: SheetWriter) {
    fun uploadFile(file: MultipartFile): String {
        val audioInputStream = AudioSystem.getAudioInputStream(BufferedInputStream(file.inputStream))
        val parsedSpeech = parser.parse(audioInputStream)
        val fileId = System.currentTimeMillis().toString()
        sheetWriter.writeXlsx(parsedSpeech, "$fileId.xlsx")
        return fileId
    }

    fun downloadXlsx(fileId: String): Resource {
        return FileSystemResource("$fileId.xlsx")
    }
}