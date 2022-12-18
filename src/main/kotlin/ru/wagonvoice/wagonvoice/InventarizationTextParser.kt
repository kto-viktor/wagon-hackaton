package ru.wagonvoice.wagonvoice

import com.ibm.icu.text.RuleBasedNumberFormat
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.lang.StringBuilder
import java.text.ParseException
import java.util.Locale
import kotlin.math.max

fun main() = CsvWriter().write("localtest", InventarizationTextParser().parse("with-separator.txt"))

private const val DETAIL_NAME_FREQUENCY_THREESHOLD = 15
private const val MAX_NUMBER_LENGTH = 6
private const val CURRENT_YEAR = 2022
private const val FACTORIES_UNDER_100 = true

class InventarizationTextParser {
    private val numberFormat = RuleBasedNumberFormat(Locale("ru"), RuleBasedNumberFormat.SPELLOUT)

    fun parse(fileName: String): List<Detail> {
        println("will parse $fileName")
        val str = File(fileName).readText().replace("ё", "е")

        // вспомогательная статистика для анализа текста
        val words: MutableList<String> = str.split(" ").toMutableList()
        val countedWordsMap: Map<String, Int> = words.groupingBy { it }.eachCount()
        val sortedWordsMap = countedWordsMap.toList().sortedByDescending { it.second }.toMap()
        println("статистика по словам: $sortedWordsMap")

        val rows = str.split(Regex("[| ]следу[а-я]+[| ]")).toMutableList() // делим на строки по ключевому слову следующая, следующий итд
        val trashWords = setOf(
            "вот", "это", "да", "в", "я", "так", "еще", "есть", "что", "у",
            "все", "с", "меня", "сейчас", "нету", "а", "какой", "как", "за", "если"
        )
        // большинство строк начинаются с названия детали
        val potentialDetailNames =
            getPotentialDetailNames(rows) // пытаемся достоверно найти перечень названия деталей путем матчинга первых слов
        println("potential details names after filtering:")
        println(potentialDetailNames)
        rows[0] = rows[0].removePrefix("начало записи ") // ну, погнали
        var detailName = potentialDetailNames[0] // хитрость - мы будем каждый раз сохранять наименование последней детали,
        // иx, как правило, смотрят одни и те же подряд
        val result = mutableListOf<Detail>()
        rows.forEach { row ->
            try {
                val rowWords = row.split(" ").minus(trashWords)
                detailName = getDetailName(row, potentialDetailNames, detailName, rowWords)
                val (number, numberIndex) = findNumber(rowWords)

                val wordsAfterNumber = rowWords.subList(numberIndex, rowWords.size)

                val yearIndex = wordsAfterNumber.indexOfFirst { isYearWord(it) }
                val factoryIndex = wordsAfterNumber.indexOfFirst { isFactoryWord(it) }
                val maxIndex = max(yearIndex, factoryIndex)
                val fetchNumbersFromRightSide = maxIndex < wordsAfterNumber.size - 1 && isNumber(wordsAfterNumber[maxIndex + 1])

                val yearsWithConfidence = findYears(wordsAfterNumber, fetchNumbersFromRightSide, row, rowWords)
                val factoriesWithConfidence = findFactories(wordsAfterNumber, fetchNumbersFromRightSide, row, rowWords)

                yearsWithConfidence.sortBy { it.second }
                val year = yearsWithConfidence.firstOrNull()?.first ?: ""
                factoriesWithConfidence.sortBy { it.second }
                val factory = factoriesWithConfidence.firstOrNull()?.first ?: ""
                println()
                println("было: $row")
                println("стало: $detailName $number год $year завод $factory")
                result.add(Detail(detailName, number.toString(), year.toString(), factory))
            } catch (e: Exception) {
                println("ERROR:")
                e.printStackTrace()
            }
        }
        println("###### parsed. Result size is ${result.size} rows #######")
        return result
    }

    private fun findYears(
        wordsAfterNumber: List<String>,
        fetchNumbersFromRightSide: Boolean,
        row: String,
        rowWords: List<String>
    ): MutableList<Pair<Int, Int>> {
        val yearsWithConfidence = mutableListOf<Pair<Int, Int>>()
        for ((i, word) in wordsAfterNumber.withIndex()) {
            if (isYearWord(word)) {
                if (fetchNumbersFromRightSide && i < wordsAfterNumber.size - 1 && isNumber(wordsAfterNumber[i + 1])) { //можно поискать справа от слова год
                    if (wordsAfterNumber[i + 1].startsWith("дв") && i < wordsAfterNumber.size - 3) { // 2k
                        if (getNumber(wordsAfterNumber[i + 2]) == 1000) { //2k++
                            if (wordsAfterNumber[i + 2].endsWith("ый")) {
                                yearsWithConfidence.add(0, 2000 to 60)
                            } else if (getNumber(wordsAfterNumber[i + 1] + " " + wordsAfterNumber[i + 2] + " " + wordsAfterNumber[i + 3]) in 2000..CURRENT_YEAR) {
                                val year =
                                    getNumber(wordsAfterNumber[i + 1] + " " + wordsAfterNumber[i + 2] + " " + wordsAfterNumber[i + 3])
                                yearsWithConfidence.add(0, year to 99)
                            }
                        }
                    } else if (getNumber(wordsAfterNumber[i + 1]) in 0..19) {
                        val tryYear = getNumber(wordsAfterNumber[i + 1]) + 2000
                        yearsWithConfidence.add(0, tryYear to 85)
                    } else if (getNumber(wordsAfterNumber[i + 1]) in 20..90 step 10) {
                        var tryYear = -1
                        if (i < wordsAfterNumber.size - 2) {
                            tryYear = getNumber(wordsAfterNumber[i + 1] + " " + wordsAfterNumber[i + 2]) //девяносто третий, итд - два слова
                            if (tryYear == -1) {
                                tryYear = getNumber(wordsAfterNumber[i + 1]) //девяностый итд - одно слово
                            }
                        }
                        if (tryYear in 20..CURRENT_YEAR % 2000) {
                            tryYear += 2000
                            yearsWithConfidence.add(0, tryYear to 82)
                        } else if (tryYear in 50..99) {
                            tryYear += 1900
                            yearsWithConfidence.add(0, tryYear to 82)
                        }
                    }
                }

                if (!fetchNumbersFromRightSide && i >= 1 && isNumber(wordsAfterNumber[i - 1])) { // можно поискать слева от слова год
                    if (i >= 3 && wordsAfterNumber[i - 3].startsWith("дв")) { //2kxxx
                        val tryYear =
                            getNumber(wordsAfterNumber[i - 3] + " " + wordsAfterNumber[i - 2] + " " + wordsAfterNumber[i - 1])
                        if (tryYear in 2000..CURRENT_YEAR) {
                            yearsWithConfidence.add(0, tryYear to 99)
                        }
                    }
                    if ((i >= 2 && wordsAfterNumber[i - 2].startsWith("дв")) || wordsAfterNumber[i - 1].startsWith("дв")) { //2000
                        var tryYear = getNumber(wordsAfterNumber[i - 2] + " " + wordsAfterNumber[i - 1]) //двух тысячный, две тысячи
                        if (tryYear == -1) tryYear = getNumber(wordsAfterNumber[i - 1]) // двухтысячный
                        if (tryYear in 2000..CURRENT_YEAR) {
                            yearsWithConfidence.add(0, tryYear to 80)
                        }
                    }
                }
            }
        }
        if (yearsWithConfidence.isEmpty()) {
            if (row.contains("тысяч")) {
                val thousandWordIndex = rowWords.indexOfFirst { getNumber(it) == 1000 }
                val tryYear =
                    getNumber(rowWords[thousandWordIndex - 1] + " " + rowWords[thousandWordIndex] + " " + rowWords[thousandWordIndex + 1])
                if (tryYear in 2000..CURRENT_YEAR) {
                    yearsWithConfidence.add(tryYear to 50) // либо да, либо нет :))
                }
            }
        }
        return yearsWithConfidence
    }

    private fun findFactories(
        wordsAfterNumber: List<String>,
        fetchNumbersFromRightSide: Boolean,
        row: String,
        rowWords: List<String>
    ): MutableList<Pair<String, Int>> {
        val factoriesWithConfidence = mutableListOf<Pair<String, Int>>()
        if (rowWords.contains("китай")) {
            factoriesWithConfidence.add(0, "китай" to 99)
        }
        for ((i, word) in wordsAfterNumber.withIndex()) {
            if (isFactoryWord(word)) {
                if (fetchNumbersFromRightSide && i < wordsAfterNumber.size - 1 && isNumber(wordsAfterNumber[i + 1])) { //можно поискать справа от слова завод
                    if (getNumber(wordsAfterNumber[i+1]) in 20..90 step 10) { // 2k
                        var tryFactory = -1
                        if (i < wordsAfterNumber.size - 2) {
                            tryFactory = getNumber(wordsAfterNumber[i + 1] + " " + wordsAfterNumber[i + 2]) //девяносто третий, итд - два слова
                            if (tryFactory == -1) {
                                tryFactory = getNumber(wordsAfterNumber[i + 1]) //девяностый итд - одно слово
                            }
                        }
                        factoriesWithConfidence.add(0, tryFactory.toString() to 80)
                    } else if (getNumber(wordsAfterNumber[i+1]) in 1..19) {
                        val factoryNumber = getNumber(wordsAfterNumber[i + 1]).toString()
                        factoriesWithConfidence.add(0, factoryNumber to 70)
                    }

                    if (!FACTORIES_UNDER_100) {
                        // сюда логику, если хотим учитывать номера заводов больше 100
                        // проверить, что справа трехзначное число, и так далее .. опасно
                    }
                }

                if (!fetchNumbersFromRightSide && i >= 1 && isNumber(wordsAfterNumber[i - 1])) { // можно поискать слева от слова завод
                    if (i>=2 && getNumber(wordsAfterNumber[i-2]) in 20..90 step 10) { // 2k
                        var tryFactory = -1
                        tryFactory = getNumber(wordsAfterNumber[i - 2] + " " + wordsAfterNumber[i - 1]) //девяносто третий, итд - два слова
                        if (tryFactory != -1) {
                            factoriesWithConfidence.add(0, tryFactory.toString() to 60)
                        }
                    } else  {
                        val factoryNumber = getNumber(wordsAfterNumber[i - 1]).toString()
                        factoriesWithConfidence.add(0, factoryNumber to 60)
                    }
                }
            }
        }
        return factoriesWithConfidence
    }

    private fun findNumber(words: List<String>): Pair<StringBuilder, Int> {
        var numberIndex = 0
        var numberStarts = false
        var previous = -1
        val number = StringBuilder()
        for ((i, word) in words.withIndex()) {
            numberIndex = i // поиск года и завода начнем уже после номера, поэтому запоминаем
            if (word.contains("|")) continue
            val num = getNumberInSubjectiveCase(word)
            if (num > 0) numberStarts = true
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
                    break
                } else if (number.length <= 3 && previous != -1) {
                    number.append(previous)
                    break
                } else { // чето не угадали - длинна не подходит
                    numberStarts = false
                    number.clear()
                }
            }
            if (number.length == 6) {
                break;
            }
        }
        return number to numberIndex
    }

    private fun isYearWord(it: String) = it == "год" || it == "код" // || it == "вот"
    private fun isFactoryWord(it: String) = it == "завод" || it == "зовут"

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

    // когда диктуют номер, числа называют в именительном падеже
    // потом могут сразу называть год в другом падеже (номер сто пять двенадцать пятнадцатый год)
    private fun getNumberInSubjectiveCase(word: String): Int {
        if (word.startsWith("тысяч")) return 1000
        if (word == "две" || word.endsWith("й")) return -1
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
        words: List<String>
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

