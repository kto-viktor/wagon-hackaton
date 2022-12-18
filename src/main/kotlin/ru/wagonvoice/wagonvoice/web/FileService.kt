package ru.wagonvoice.wagonvoice.web

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import ru.wagonvoice.wagonvoice.CsvWriter
import ru.wagonvoice.wagonvoice.algorithm.InventarizationTextParser
import ru.wagonvoice.wagonvoice.algorithm.SpeechRecognizer

@Service
class FileService(
    private val recognizer: SpeechRecognizer,
    private val inventarizationParser: InventarizationTextParser,
    private val csvWriter: CsvWriter
) {

    fun uploadFile(file: MultipartFile): String {
        val fileId = recognizer.recognize(file.inputStream, file.size, System.currentTimeMillis().toString())
        val parsedData = inventarizationParser.parse("$fileId.txt")
        csvWriter.write(fileId, parsedData)
        return fileId
    }

    fun downloadCsv(fileId: String): Resource {
        return FileSystemResource("$fileId.csv")
    }
}