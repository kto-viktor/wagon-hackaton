package ru.wagonvoice.wagonvoice

import com.ibm.icu.text.RuleBasedNumberFormat
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.lang.StringBuilder
import java.text.ParseException
import java.util.Locale

fun main() = InventarizationParser().parse("with-separator.txt")

private const val DETAIL_NAME_FREQUENCY_THREESHOLD = 15
private const val MAX_NUMBER_LENGTH = 6

class InventarizationParser {
    private val numberFormat = RuleBasedNumberFormat(Locale("ru"), RuleBasedNumberFormat.SPELLOUT)

    fun parse(fileName: String) {
        val str = File(fileName).readText().replace("ё", "е")

        // вспомогательная статистика для анализа текста, не используется
        val words: MutableList<String> = str.split(" ").toMutableList()
        val countedWordsMap: Map<String, Int> = words.groupingBy { it }.eachCount()
        val sortedWordsMap = countedWordsMap.toList().sortedByDescending { it.second }.toMap()
        println("статистика по словам: $sortedWordsMap")

        val rows = str.split(Regex("[| ]следу[а-я]+[| ]")).toMutableList() // делим на строки по ключевому слову следующая, следующий итд
        val trashWords = listOf("вот", "это", "да", "в", "я", "так", "еще", "есть", "что", "у",
            "все", "с", "меня", "сейчас", "нету", "а", "какой", "как", "за", "если")
        // большинство строк начинаются с названия детали
        val potentialDetailNames =
            getPotentialDetailNames(rows) // пытаемся достоверно найти перечень названия деталей путем матчинга первых слов
        println("potential details names after filtering:")
        println(potentialDetailNames)
        rows[0] = rows[0].removePrefix("начало записи ")
        var detailName = potentialDetailNames[0] // хитрость - мы будем каждый раз сохранять наименование последней детали,
        // иx как правило смотрят одни и те же подряд
        rows.forEach { row ->
            //println(row)
            val parts = row.split("|").filter { it.isNotEmpty() }.map { it.trim().split(" ") }
            val yearsWithConfidence = parseYear(parts)
            detailName = getDetailName(row, potentialDetailNames, detailName, words)
            val words = row.split(" ")
            var numberStarts = false
            var previous = -1
            val number = StringBuilder()
            println(row)
            for (word in words) {
                if (word.contains("|")) continue
                val num = getNumber(word)
                if (num > 0) numberStarts = true
                if (!row.contains("двадцать девять девятьсот шесть")) break
                if (numberStarts && num != -1) {
                    if ((100..900 step 100).contains(previous)) {
                        if (num < 100) {
                            previous += num
                        } else {
                            if (number.length + previous.toString().length > MAX_NUMBER_LENGTH) break;
                            number.append(previous)
                            previous = num
                        }
                    } else if (previous > 10 && previous % 10 == 0) {
                        if (num < 10) {
                            previous += num
                            if (number.length + previous.toString().length > MAX_NUMBER_LENGTH) break;
                            number.append(previous)
                            previous = -1
                        } else {
                            if (number.length + previous.toString().length > MAX_NUMBER_LENGTH) break;
                            number.append(previous)
                            previous = num
                        }
                    } else if (previous == -1) {
                        if (num < 20) {
                            if (number.length + previous.toString().length > MAX_NUMBER_LENGTH) break;
                            number.append(num)
                        } else {
                            previous = num
                        }
                    }
                }
                if (num == -1 && numberStarts) { // мы встретили не число
                    if (number.length in 4..6) {
                        break;
                    }  else if (number.length <= 3 && previous != -1) {
                        number.append(previous)
                        numberStarts = false
                        number.clear()
                    } else {
                        numberStarts = false
                        number.clear()
                    }
                }
                if (number.length == 6) {
                    break;
                }
            }
            if (Constants.CORRECT_NUMS.contains(number.toString())) {
                println("+++++++++++++++++++++++++++++++++++++++")
            } else {
                println("!!!!!    $number        !!!!!!")
            }
            //println(">>>$yearsWithConfidence")
            //println(detailName)
        }
    }

    private fun parseYear(parts: List<List<String>>) {
        val yearsWithConfidence: MutableList<Pair<Int, Int>> = mutableListOf()
        parts.filter { it.any { word -> isYearWord(word) } }
            .forEach { part ->
                if (part.none { word -> word == "завод" || word == "зовут" }) {
                    val cleanPart = part.filter { isNumber(it) || isYearWord(it) }
                    val indexOfYearWord = cleanPart.indexOfFirst { isYearWord(it) }
                    val start = cleanPart.subList(indexOfYearWord, cleanPart.size).indexOfFirst { isNumber(it) } + indexOfYearWord
                    if (start != -1) {
                        val nextNum = getNumber(cleanPart[start])
                        if (nextNum == 2 && getNumber(cleanPart[start + 1]) == 1000) {
                            val tryYear = getNumber(cleanPart[start] + " " + cleanPart[start + 1] + " " + cleanPart[start + 2])
                            if (tryYear != -1) {
                                yearsWithConfidence.add(tryYear to 99)
                            } else {
                                val check2k = getNumber(cleanPart[start] + " " + cleanPart[start + 1])
                                if (check2k == 2000) {
                                    yearsWithConfidence.add(2000 to 70)
                                }
                            }
                        } else if (nextNum in 0..22) {
                            yearsWithConfidence.add(2000 + nextNum to 90)
                        } else if (nextNum % 10 == 0 && isNumber(cleanPart[start] + " " + cleanPart[start + 1])) {
                            yearsWithConfidence.add(1900 + getNumber(cleanPart[start] + " " + cleanPart[start + 1]) to 80)
                        }
                    }
                }
            }
    }

    private fun isYearWord(it: String) = it == "год" || it == "код"

    private fun isNumber(word: String): Boolean {
        return getNumber(word) != -1
    }

    private fun getNumber(word: String): Int {
        if (word.startsWith("тысяч")) return 1000
        return try {
            numberFormat.parse(word).toInt()
        } catch (e: ParseException) {
            -1
        }
    }

    private fun getDetailName(
        row: String,
        potentialDetailNames: List<String>,
        previousDetailName: String,
        words: MutableList<String>
    ): String {
        val beforeNumber = row.split("номер")[0]
        potentialDetailNames.forEach { potentialName ->
            // 1 - надо перебирать все комбиранции из potentialName.split(" ").size слов подряд до номер
            // и смотреть совпадение
            // потом тоже самое, но после слова номер
            if (row.startsWith(potentialName)) {
                return potentialName
            } else if (stringsSimilar(potentialName, beforeNumber)) {
                return potentialName
            } else if (wordsSimilar(potentialName, words, 2)
                || wordsSimilar(potentialName, words, 3)
                || wordsSimilar(potentialName, words, 4)
            ) {
                return potentialName
            } else if (row.contains(potentialName)) {
                return potentialName
            }
        }
        return previousDetailName
    }

    private fun wordsSimilar(potentialName: String, words: List<String>, countOfWords: Int) =
        stringsSimilar(potentialName, words.take(countOfWords).joinToString { " " })

    // большинство строк начинаются с названия детали; пытаемся достоверно найти перечень названия деталей путем матчинга первых слов
    private fun getPotentialDetailNames(rows: List<String>): List<String> {
        val potentialDetailNames = mutableMapOf<String, Int>()
        rows.forEach { row ->
            val splitByNumberWord = row.split("номер")// часто название детали говорится перед словом "номер"
            val beforeNumberWord = splitByNumberWord[0].filter { it != '|' }.trim()
            if (beforeNumberWord.length <= 50) { // если перед словом номер достаточно короткий текст, предположим, что это название
                incrementOrPutOne(potentialDetailNames, beforeNumberWord.trim())
            } else {
                val rowWords = row.split(" ")
                    .map { word -> word.filter { it != '|' } }
                    .map { it.trim() } // если по слову номер выцепить не получилось, будем считать встречающуюся частоту по первым 1..5 словам в строках
                incrementOrPutOne(potentialDetailNames, rowWords[0])
                incrementOrPutOne(potentialDetailNames, rowWords[0] + " " + rowWords[1])
                incrementOrPutOne(potentialDetailNames, rowWords[0] + " " + rowWords[1] + " " + rowWords[2])
                incrementOrPutOne(potentialDetailNames, rowWords[0] + " " + rowWords[1] + " " + rowWords[2] + " " + rowWords[3])
                incrementOrPutOne(
                    potentialDetailNames,
                    rowWords[0] + " " + rowWords[1] + " " + rowWords[2] + " " + rowWords[3] + " " + rowWords[4]
                )
            }
        }
        val sortedPotentialDetailNames: Map<String, Int> = potentialDetailNames // отсортируем по количеству найденных повторений
            .toList()
            .sortedByDescending { it.second }
            .toMap()
            .filterKeys { it.isNotEmpty() }

        val potentialDetailNamesWithoutDuplicates = mutableMapOf<String, Int>()
        sortedPotentialDetailNames.forEach { (name, frequency) ->
            val duplicate = findDuplicate(
                potentialDetailNamesWithoutDuplicates,
                name
            ) // будем искать среди получившихся потенциальных названий деталей похожие
            if (duplicate == null) {
                potentialDetailNamesWithoutDuplicates[name] = frequency
            } else {
                potentialDetailNamesWithoutDuplicates[duplicate] =
                    potentialDetailNamesWithoutDuplicates[duplicate]!! + frequency // и суммировать К САМОМУ ПОПУЛЯРНОМУ варианту
            }
        }
        println("potentialDetailNamesWithoutDuplicates without filter:")
        println(potentialDetailNamesWithoutDuplicates)
        return potentialDetailNamesWithoutDuplicates.filterValues { it > DETAIL_NAME_FREQUENCY_THREESHOLD }.keys.toList() // уберем редкие варианты
    }

    private fun findDuplicate(
        potentialDetailNamesWithoutDuplicates: MutableMap<String, Int>,
        name: String
    ) = potentialDetailNamesWithoutDuplicates.keys.find { it != name && stringsSimilar(it, name) }

    private fun stringsSimilar(it: String, name: String) = StringUtils.getJaroWinklerDistance(it, name) > 0.7

    private fun incrementOrPutOne(potentialDetailNames: MutableMap<String, Int>, beforeNumberWord: String) {
        val frequency = potentialDetailNames[beforeNumberWord]
        if (frequency != null) {
            potentialDetailNames[beforeNumberWord] = frequency + 1
        } else {
            potentialDetailNames[beforeNumberWord] = 1
        }
    }
}

