package ru.wagonvoice.wagonvoice

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Service
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem


@Service
class SpeechToTableParser(private val recognizer: Recognizer, private val objectMapper: ObjectMapper) {
    fun parse(ais: AudioInputStream): ParsedSpeech {
        var nbytes: Int
        val b = ByteArray(4096)
        val text = StringBuilder()
        while (ais.read(b).also { nbytes = it } >= 0) {
            if (recognizer.acceptWaveForm(b, nbytes)) {
                val part = objectMapper.readTree(recognizer.result).get("text").textValue()
                text.append("$part ")
            }
        }
        val splitPreamble = text.split(" столбцы ")
        val preamble = splitPreamble[0]
        val splitHeader = splitPreamble[1].split(" поехали ")
        val headers = splitHeader[0].split(" и ")
        val body = splitHeader[1]
        val rows = body.split(" далее ")
        val parsedData = mutableListOf<Map<String, String>>()
        rows.forEach { row ->
            val stringBuilder = StringBuilder(row)
            headers.forEach { header ->
                val headerIndex = stringBuilder.indexOf(header)
                if (headerIndex >= 0) {
                    stringBuilder.insert(headerIndex, "|")
                    stringBuilder.insert(headerIndex + header.length + 1, "|")
                }
            }
            val columns = stringBuilder.split("|")
            val rowMap = mutableMapOf<String, String>()
            rowMap[headers[0]] = columns[0]
            for (i in 1 until columns.size step 2) {
                rowMap[columns[i]] = columns[i + 1].trim()
            }
            parsedData.add(rowMap)
        }
        return ParsedSpeech(preamble, headers, parsedData)
    }

    private fun writeCsv(parsedData: ParsedSpeech) {
        val csv = StringBuilder()
        csv.append(parsedData.preamble + ";\n")
        parsedData.headers.forEach { csv.append("$it;") }
        csv.append("\n")
        parsedData.data.forEach { dataMap ->
            parsedData.headers.forEach { header ->
                csv.append(dataMap[header].orEmpty() + ";")
            }
            csv.append("\n")
        }
        File("table.csv").writeText(csv.toString())
    }
}