package ru.wagonvoice.wagonvoice

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class FileService(private val recognizer: SpeechRecognizer) {
    fun uploadFile(file: MultipartFile): String {
        val fileId = recognizer.recognize(file.inputStream, file.size)
        return fileId
    }

    fun downloadCsv(fileId: String): Resource {
        return FileSystemResource("$fileId.txt")
    }
}