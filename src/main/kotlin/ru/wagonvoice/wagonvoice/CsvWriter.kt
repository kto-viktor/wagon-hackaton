package ru.wagonvoice.wagonvoice

import org.springframework.stereotype.Component
import java.io.File
import java.lang.StringBuilder

@Component
class CsvWriter {
    fun write(fileId: String, data: List<Detail>) {
        println("will write to $fileId.csv")
        println("\n\n\n\n")
        val content = StringBuilder("наименование,номер,год,завод,комментарий\n")
        data.forEach { detail ->
            println(detail)
            val row = detail.name + "," + detail.number + "," + detail.year + "," + detail.factory + "," + detail.comment
            content.append(row + "\n")
        }
        val file = File("$fileId.csv")
        file.writeText(content.toString())
        println("############## written to $fileId.csv ####################")
    }
}