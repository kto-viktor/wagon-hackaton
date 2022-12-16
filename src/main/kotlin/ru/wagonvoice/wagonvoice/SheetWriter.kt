package ru.wagonvoice.wagonvoice

import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Component
import java.io.FileOutputStream

@Component
class SheetWriter {
    fun writeXlsx(parsedSpeech: ParsedSpeech, filename: String) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Инвентаризация")
        val headerStyle = workbook.createCellStyle()
        //headerStyle.fillForegroundColor = IndexedColors.LIGHT_BLUE.getIndex()
        //headerStyle.fillPattern = FillPatternType.SOLID_FOREGROUND
        val font = workbook.createFont()
        font.fontName = "Arial"
        font.bold = true
        headerStyle.setFont(font)

        val cell = sheet.createRow(0).createCell(0)
        cell.setCellValue(parsedSpeech.preamble)
        cell.cellStyle = headerStyle

        val headerRow = sheet.createRow(1)
        for (i in 0 until parsedSpeech.headers.size) {
            val headerCell = headerRow.createCell(i)
            headerCell.setCellValue(parsedSpeech.headers[i])
            headerCell.cellStyle = headerStyle
        }

        parsedSpeech.data.forEachIndexed { rowIndex, rowDataMap ->
            val dataRow = sheet.createRow(rowIndex + 2)
            parsedSpeech.headers.forEachIndexed { headerIndex, header ->
                if (rowDataMap.containsKey(header)) {
                    dataRow.createCell(headerIndex).setCellValue(rowDataMap[header])
                }
            }
        }

        val fileOut = FileOutputStream(filename)
        workbook.write(fileOut)
        fileOut.close()
    }
}