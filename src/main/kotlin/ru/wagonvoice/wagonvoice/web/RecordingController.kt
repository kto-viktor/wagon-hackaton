package ru.wagonvoice.wagonvoice.web

import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.view.RedirectView

@Controller
class RecordingController(private val fileService: FileService) {
    @GetMapping("/")
    fun getIndexPage() = "index"

    @PostMapping("/recording")
    @ResponseBody
    fun uploadRecording(@RequestParam("file") file: MultipartFile): RedirectView {
        val fileId = fileService.uploadFile(file)
        return RedirectView("/recording/$fileId")
    }

    @GetMapping("/recording/{fileId}")
    @ResponseBody
    fun download(@PathVariable fileId: String): ResponseEntity<Resource?>? {
        val file: Resource = fileService.downloadCsv(fileId)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$fileId\".csv")
            .body(file)
    }
}