package ru.wagonvoice.wagonvoice

import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class RecordingController(private val fileService: FileService) {
    @PostMapping("/recording")
    fun uploadRecording(@RequestParam("file") file: MultipartFile): String {
        return fileService.uploadFile(file)
    }

    @GetMapping("/recording/{fileId}")
    fun download(@PathVariable fileId: String): ResponseEntity<Resource?>? {
        val file: Resource = fileService.downloadCsv(fileId)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$fileId\".txt")
            .body(file)
    }
}