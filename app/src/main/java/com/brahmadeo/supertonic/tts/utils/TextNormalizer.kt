package com.brahmadeo.supertonic.tts.utils

import java.util.regex.Pattern
import com.brahmadeo.supertonic.tts.SupertonicTTS

/**
 * Enhanced TextNormalizer with comprehensive rule set
 * Handles currencies, numbers, abbreviations, and more for natural TTS
 */
class TextNormalizer {
    private val currencyNormalizer = CurrencyNormalizer()
    
    data class Rule(val pattern: Pattern, val replacement: (java.util.regex.Matcher) -> String)
    private val rules: List<Rule> = initializeRules()

    private fun initializeRules(): List<Rule> {
        val rulesList = mutableListOf<Rule>()
        
        fun addStr(regex: String, replacement: String) {
            rulesList.add(Rule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE)) { replacement })
        }
        
        fun addLambda(regex: String, replacement: (java.util.regex.Matcher) -> String) {
            rulesList.add(Rule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), replacement))
        }

        // QUOTE PUNCTUATION SPACING (Model Stability)
        // Adds space between double quote and punctuation (".) -> (" .) to prevent audio glitches
        addLambda("([\"”])([.!?])") { m ->
            "${m.group(1)} ${m.group(2)}"
        }

        // PARENTHESES SPACING (Audio Fix)
        // Adds space inside parentheses to fix tokenization artifacts
        addLambda("\\(([^)]+)\\)") { m ->
            "( ${m.group(1)} )"
        }

        // RANGE NORMALIZATION (e.g. 10-15 years -> 10 to 15 years)
        // Matches digits separated by hyphen (-), en dash (–), or em dash (—)
        addLambda("\\b(\\d+)\\s*[-–—]\\s*(\\d+)\\b") { m ->
            "${m.group(1)} to ${m.group(2)}"
        }

        // EM DASH NORMALIZATION (Priority: High)
        // Replace em dashes with comma to prevent hard pauses/sentence splitting
        addStr("\\s*[—]\\s*", ", ")

        // EMERGENCY NUMBERS (Priority: Highest)
        addStr("\\b911\\b", "nine one one")
        addLambda("\\b(999|112|000)\\b") { m -> 
            val num = m.group(1) ?: ""
            num.toCharArray().joinToString(" ") 
        }

        // MEASUREMENTS
        addLambda("\\b(\\d+(?:\\.\\d+)?)\\s*m\\b(?=[^a-zA-Z]|$)") { m ->
            val amount = m.group(1) ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            if (amount == "1") "1 meter" else "$valStr meters"
        }

        addLambda("\\b(\\d+(?:\\.\\d+)?)(km|mi)\\b") { m ->
            val amount = m.group(1) ?: ""
            val unit = m.group(2)?.lowercase() ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            val fullUnit = if (unit == "km") "kilometers" else "miles"
            "$valStr $fullUnit"
        }

        addLambda("\\b(\\d+(?:\\.\\d+)?)(kph|mph|kmh|km/h|m/s)\\b") { m ->
            val amount = m.group(1) ?: ""
            val unit = m.group(2)?.lowercase() ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            val fullUnit = when (unit) {
                "kph", "kmh", "km/h" -> "kilometers per hour"
                "mph" -> "miles per hour"
                "m/s" -> "meters per second"
                else -> unit
            }
            "$valStr $fullUnit"
        }

        addLambda("\\b(\\d+(?:\\.\\d+)?)(kg|g|lb|lbs)\\b") { m ->
            val amount = m.group(1) ?: ""
            val unit = m.group(2)?.lowercase() ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            val fullUnit = when (unit) {
                "kg" -> "kilograms"
                "g" -> "grams"
                "lb", "lbs" -> "pounds"
                else -> unit
            }
            if (amount == "1") "1 ${fullUnit.trimEnd('s')}" else "$valStr $fullUnit"
        }

        addLambda("\\b(\\d+(?:\\.\\d+)?)h\\b") { m ->
            val amount = m.group(1) ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            if (amount == "1") "1 hour" else "$valStr hours"
        }

        // LARGE NUMBERS (Non-currency)
        addLambda("\\b(\\d+(?:\\.\\d+)?)\\s*(?:M|mn)\\b") { m ->
            val amount = m.group(1) ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            "$valStr million"
        }

        addLambda("\\b(\\d+(?:\\.\\d+)?)\\s*(?:B|bn)\\b") { m ->
            val amount = m.group(1) ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            "$valStr billion"
        }

        addLambda("\\b(\\d+(?:\\.\\d+)?)tn\\b") { m ->
            val amount = m.group(1) ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            "$valStr trillion"
        }

        // PERCENTAGES
        addLambda("\\b(\\d+(?:\\.\\d+)?)%") { m ->
            val amount = m.group(1) ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            "$valStr percent"
        }

        // ORDINALS
        addLambda("\\b(\\d+)(st|nd|rd|th)\\b") { m ->
            val num = m.group(1)?.toIntOrNull() ?: 0
            numberToOrdinal(num)
        }

        // YEARS
        // Rule: 2000-2009 (Priority over general split)
        addLambda("\\b200(\\d)\\b") { m ->
            val digit = m.group(1) ?: "0"
            if (digit == "0") "two thousand" else "two thousand $digit"
        }

        // Rule: 1900-1909
        addLambda("\\b190(\\d)\\b") { m ->
            val digit = m.group(1) ?: "0"
            if (digit == "0") "nineteen hundred" else "nineteen oh $digit"
        }

        // YEARS (Split 4 digit years starting with 19 or 20)
        addLambda("\\b(19|20)(\\d{2})\\b(?!s)") { m ->
            "${m.group(1)} ${m.group(2)}"
        }

        // TITLES
        addLambda("\\b(Prof|Dr|Mr|Mrs|Ms)\\.\\s+") { m ->
            when (m.group(1)) {
                "Prof" -> "Professor "
                "Dr" -> "Doctor "
                "Mr" -> "Mister "
                "Mrs" -> "Missus "
                "Ms" -> "Miss "
                else -> m.group(0) ?: ""
            }
        }

        // ABBREVIATIONS
        addLambda("\\b(approx|vs|etc)\\.\\b") { m ->
            when (m.group(1)?.lowercase()) {
                "approx" -> "approximately"
                "vs" -> "versus"
                "etc" -> "et cetera"
                else -> m.group(0) ?: ""
            }
        }

        return rulesList
    }

    private fun numberToOrdinal(num: Int): String {
        val ordinals = mapOf(
            1 to "first", 2 to "second", 3 to "third", 4 to "fourth", 5 to "fifth",
            6 to "sixth", 7 to "seventh", 8 to "eighth", 9 to "ninth", 10 to "tenth",
            11 to "eleventh", 12 to "twelfth", 13 to "thirteenth", 14 to "fourteenth",
            15 to "fifteenth", 16 to "sixteenth", 17 to "seventeenth", 18 to "eighteenth",
            19 to "nineteenth", 20 to "twentieth"
        )
        
        if (ordinals.containsKey(num)) return ordinals[num]!!
        
        val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
        val ones = arrayOf("", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth")
        
        if (num < 100) {
            val tenDigit = num / 10
            val oneDigit = num % 10
            if (oneDigit == 0) return "${tens[tenDigit]}th"
            return "${tens[tenDigit]} ${ones[oneDigit]}"
        }
        
        val lastTwo = num % 100
        if (lastTwo in 11..13) return "${num}th"
        
        val lastDigit = num % 10
        return when (lastDigit) {
            1 -> "${num}st"
            2 -> "${num}nd"
            3 -> "${num}rd"
            else -> "${num}th"
        }
    }

    private val lithuanianMonths = arrayOf(
        "", "sausio", "vasario", "kovo", "balandžio", "gegužės", "birželio",
        "liepos", "rugpjūčio", "rugsėjo", "spalio", "lapkričio", "gruodžio"
    )

    private val lithuanianDayOrdinals = mapOf(
        1 to "pirma", 2 to "antra", 3 to "trečia", 4 to "ketvirta", 5 to "penkta",
        6 to "šešta", 7 to "septinta", 8 to "aštunta", 9 to "devinta", 10 to "dešimta",
        11 to "vienuolikta", 12 to "dvylikta", 13 to "trylikta", 14 to "keturiolikta",
        15 to "penkiolikta", 16 to "šešiolikta", 17 to "septyniolikta",
        18 to "aštuoniolikta", 19 to "devyniolikta", 20 to "dvidešimta",
        21 to "dvidešimt pirma", 22 to "dvidešimt antra", 23 to "dvidešimt trečia",
        24 to "dvidešimt ketvirta", 25 to "dvidešimt penkta", 26 to "dvidešimt šešta",
        27 to "dvidešimt septinta", 28 to "dvidešimt aštunta", 29 to "dvidešimt devinta",
        30 to "trisdešimta", 31 to "trisdešimt pirma"
    )

    private val lithuanianYearOrdinals = mapOf(
        1 to "pirmų", 2 to "antrų", 3 to "trečių", 4 to "ketvirtų", 5 to "penktų",
        6 to "šeštų", 7 to "septintų", 8 to "aštuntų", 9 to "devintų", 10 to "dešimtų",
        11 to "vienuoliktų", 12 to "dvyliktų", 13 to "tryliktų", 14 to "keturioliktų",
        15 to "penkioliktų", 16 to "šešioliktų", 17 to "septynioliktų",
        18 to "aštuonioliktų", 19 to "devynioliktų", 20 to "dvidešimtų",
        30 to "trisdešimtų", 40 to "keturiasdešimtų", 50 to "penkiasdešimtų",
        60 to "šešiasdešimtų", 70 to "septyniasdešimtų", 80 to "aštuoniasdešimtų",
        90 to "devyniasdešimtų"
    )

    private fun replacementSafe(value: String): String =
        value.replace("\\", "\\\\").replace("$", "\\$")

    private fun lithuanianYearOrdinal(value: Int): String {
        lithuanianYearOrdinals[value]?.let { return it }
        val tens = (value / 10) * 10
        val ones = value % 10
        val tensText = NumberUtils.convertLithuanian(tens.toLong())
        val onesText = lithuanianYearOrdinals[ones] ?: NumberUtils.convertLithuanian(ones.toLong())
        return "$tensText $onesText"
    }

    private fun lithuanianYearForDate(year: String): String {
        val yearValue = year.toIntOrNull() ?: return NumberUtils.convertLithuanianNumberString(year)
        val lastTwo = yearValue % 100
        if (lastTwo == 0) return NumberUtils.convertLithuanian(yearValue.toLong())
        val base = yearValue - lastTwo
        return "${NumberUtils.convertLithuanian(base.toLong())} ${lithuanianYearOrdinal(lastTwo)}"
    }

    private fun lithuanianDate(year: String, month: String, day: String): String {
        val monthIndex = month.toIntOrNull()
        val dayIndex = day.toIntOrNull()
        val monthText = monthIndex?.takeIf { it in 1..12 }?.let { lithuanianMonths[it] } ?: month
        val dayText = dayIndex?.let { lithuanianDayOrdinals[it] } ?: NumberUtils.convertLithuanianNumberString(day)
        return "${lithuanianYearForDate(year)} metų $monthText $dayText diena"
    }

    private fun lithuanianTime(hour: String, minute: String): String {
        val hourValue = hour.toLongOrNull() ?: return "$hour:$minute"
        val minuteValue = minute.toLongOrNull() ?: return "$hour:$minute"
        val hourText = NumberUtils.convertLithuanian(hourValue)
        val hourUnit = NumberUtils.lithuanianUnitForm(hourValue, "valanda", "valandos", "valandų")
        if (minuteValue == 0L) return "$hourText $hourUnit"
        val minuteText = NumberUtils.convertLithuanian(minuteValue)
        val minuteUnit = NumberUtils.lithuanianUnitForm(minuteValue, "minutė", "minutės", "minučių")
        return "$hourText $hourUnit $minuteText $minuteUnit"
    }

    private fun lithuanianEuroAmount(value: String): String {
        val normalized = value.trim().replace(" ", "").replace(',', '.')
        val parts = normalized.split(".", limit = 2)
        val euros = parts.getOrNull(0)?.toLongOrNull() ?: return value
        val euroText = NumberUtils.convertLithuanian(euros)
        val euroUnit = NumberUtils.lithuanianUnitForm(euros, "euras", "eurai", "eurų")
        val cents = parts.getOrNull(1)
            ?.filter { it.isDigit() }
            ?.padEnd(2, '0')
            ?.take(2)
            ?.toLongOrNull()
            ?: 0L
        if (cents == 0L) return "$euroText $euroUnit"
        val centText = NumberUtils.convertLithuanian(cents)
        val centUnit = NumberUtils.lithuanianUnitForm(cents, "centas", "centai", "centų")
        return "$euroText $euroUnit $centText $centUnit"
    }

    fun normalize(text: String, lang: String = "en", isAdvancedEnabled: Boolean = false): String {
        val lowerLang = lang.lowercase()
        
        // 1. Lexicon applies to all languages except Korean
        val processedText = if (lowerLang != "ko") {
            LexiconManager.apply(text)
        } else {
            text
        }

        if (lowerLang.startsWith("hi")) {
            return normalizeHindi(processedText)
        }

        if (lowerLang.startsWith("lt")) {
            return normalizeLithuanian(processedText)
        }

        // 2. Determine if we should apply English-style normalization rules
        // Currently: Always for English, or if toggle is on for Romance languages
        val isRomance = lowerLang.startsWith("fr") || lowerLang.startsWith("es") || lowerLang.startsWith("pt")
        val shouldNormalize = lowerLang.startsWith("en") || (isRomance && isAdvancedEnabled)

        if (!shouldNormalize) {
            return processedText
        }

        // Step 0: Fix smushed text from webpage layouts
        // Fix smushed sentences: lowercase char, period, uppercase char (reserved.Reuse)
        val smushedSentencePattern = Pattern.compile("([a-z])\\.([A-Z])")
        var fixedText = smushedSentencePattern.matcher(processedText).replaceAll("$1. $2")
        
        // Fix smushed words: lowercase char, uppercase char (economyIMF)
        val smushedWordPattern1 = Pattern.compile("([a-z])([A-Z])")
        fixedText = smushedWordPattern1.matcher(fixedText).replaceAll("$1 $2")
        
        // Fix smushed words: Uppercase followed by Uppercase+Lowercase (FTNews)
        val smushedWordPattern2 = Pattern.compile("([A-Z])([A-Z][a-z])")
        fixedText = smushedWordPattern2.matcher(fixedText).replaceAll("$1 $2")

        // Fix letter-number merges (Published8 -> Published 8)
        val letterNumberPattern = Pattern.compile("([a-zA-Z])(\\d)")
        fixedText = letterNumberPattern.matcher(fixedText).replaceAll("$1 $2")

        // Step 1: Currency
        var normalized = currencyNormalizer.normalize(fixedText)
        
        // Step 2: Other rules
        for (rule in rules) {
            val matcher = rule.pattern.matcher(normalized)
            val sb = StringBuffer()
            while (matcher.find()) {
                val replacement = rule.replacement(matcher).replace("\\", "\\\\").replace("$", "\\$")
                matcher.appendReplacement(sb, replacement)
            }
            matcher.appendTail(sb)
            normalized = sb.toString()
        }

        // Step 3: Convert remaining numbers to words (CRITICAL for C++ Engine)
        // Matches integers and decimals (e.g. "300000" -> "three hundred thousand")
        val numberPattern = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\b")
        val matcher = numberPattern.matcher(normalized)
        val sb = StringBuffer()
        while (matcher.find()) {
            val numStr = matcher.group(1) ?: ""
            try {
                val replacement = if (numStr.contains(".")) {
                    NumberUtils.convertDouble(numStr.toDouble())
                } else {
                    NumberUtils.convert(numStr.toLong())
                }
                matcher.appendReplacement(sb, replacement)
            } catch (_: Exception) {
                // If number is too large for Long, keep it as digits (or implement BigInt logic if needed)
                // For TTS, massive numbers usually read digit-by-digit anyway
                matcher.appendReplacement(sb, numStr)
            }
        }
        matcher.appendTail(sb)
        normalized = sb.toString()

        return normalized
    }

    fun splitIntoSentences(text: String, lang: String = "en"): List<String> {
        return SupertonicTTS.chunkText(text, lang)
    }

    private fun normalizeLithuanian(text: String): String {
        var normalized = text

        fun replaceAll(regex: String, replacement: (java.util.regex.Matcher) -> String) {
            val matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE).matcher(normalized)
            val sb = StringBuffer()
            while (matcher.find()) {
                matcher.appendReplacement(sb, replacementSafe(replacement(matcher)))
            }
            matcher.appendTail(sb)
            normalized = sb.toString()
        }

        replaceAll("\\b(\\d{4})[-./](\\d{1,2})[-./](\\d{1,2})\\b") { m ->
            lithuanianDate(m.group(1) ?: "", m.group(2) ?: "", m.group(3) ?: "")
        }
        replaceAll("\\b(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})\\b") { m ->
            lithuanianDate(m.group(3) ?: "", m.group(2) ?: "", m.group(1) ?: "")
        }
        replaceAll("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b") { m ->
            lithuanianTime(m.group(1) ?: "", m.group(2) ?: "")
        }
        replaceAll("\\b(\\d[\\d\\s]*(?:[,.]\\d+)?)\\s*(?:€|EUR\\b)") { m ->
            lithuanianEuroAmount(m.group(1) ?: "")
        }
        replaceAll("(?:€)\\s*(\\d[\\d\\s]*(?:[,.]\\d+)?)\\b") { m ->
            lithuanianEuroAmount(m.group(1) ?: "")
        }
        replaceAll("\\b(\\d[\\d\\s]*(?:[,.]\\d+)?)\\s*%") { m ->
            val amount = NumberUtils.convertLithuanianNumberString(m.group(1) ?: "")
            val raw = (m.group(1) ?: "").trim().replace(" ", "").replace(',', '.')
            val whole = raw.substringBefore(".").toLongOrNull() ?: 0L
            "$amount ${NumberUtils.lithuanianUnitForm(whole, "procentas", "procentai", "procentų")}"
        }
        replaceAll("\\b(\\d+)\\s*[-\\u2013\\u2014]\\s*(\\d+)\\b") { m ->
            "nuo ${NumberUtils.convertLithuanianNumberString(m.group(1) ?: "")} iki ${NumberUtils.convertLithuanianNumberString(m.group(2) ?: "")}"
        }
        replaceAll("\\b(\\d[\\d\\s]*(?:[,.]\\d+)?)\\s*(km|kg|g|m)\\b") { m ->
            val value = m.group(1) ?: ""
            val number = NumberUtils.convertLithuanianNumberString(value)
            val unit = when (m.group(2)?.lowercase()) {
                "km" -> "kilometrų"
                "kg" -> "kilogramų"
                "g" -> "gramų"
                "m" -> "metrų"
                else -> m.group(2) ?: ""
            }
            "$number $unit"
        }
        replaceAll("\\b\\d[\\d\\s]*(?:[,.]\\d+)?\\b") { m ->
            NumberUtils.convertLithuanianNumberString(m.group(0) ?: "")
        }

        return normalized
    }

    private fun normalizeHindi(text: String): String {
        // 1. Convert Devanagari digits (०-९) to Latin digits (0-9)
        var normalized = convertDevanagariDigits(text)

        // 2. Clean up commas in numbers (e.g. "1,50,000" -> "150000")
        val commaPattern = Pattern.compile("(?<=\\d),(?=\\d)")
        normalized = commaPattern.matcher(normalized).replaceAll("")

        // 3. Hindi Range Normalization (e.g., "10-15" -> "10 से 15")
        val rangePattern = Pattern.compile("\\b(\\d+)\\s*[-–—]\\s*(\\d+)\\b")
        val rangeMatcher = rangePattern.matcher(normalized)
        val rangeSb = StringBuffer()
        while (rangeMatcher.find()) {
            val replacement = "${rangeMatcher.group(1)} से ${rangeMatcher.group(2)}"
            rangeMatcher.appendReplacement(rangeSb, replacement)
        }
        rangeMatcher.appendTail(rangeSb)
        normalized = rangeSb.toString()

        // 4. Currency symbols (e.g. "₹500" or "INR 500" -> "500 रुपये")
        val currencyPattern = Pattern.compile("(?:\\bINR|₹)\\s*(\\d+(?:\\.\\d+)?)\\b")
        val currencyMatcher = currencyPattern.matcher(normalized)
        val currencySb = StringBuffer()
        while (currencyMatcher.find()) {
            val amount = currencyMatcher.group(1) ?: ""
            val replacement = "$amount रुपये"
            currencyMatcher.appendReplacement(currencySb, replacement)
        }
        currencyMatcher.appendTail(currencySb)
        normalized = currencySb.toString()

        // 5. Percentages (e.g. "5%" -> "5 प्रतिशत")
        val percentPattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%")
        val percentMatcher = percentPattern.matcher(normalized)
        val percentSb = StringBuffer()
        while (percentMatcher.find()) {
            val amount = percentMatcher.group(1) ?: ""
            val replacement = "$amount प्रतिशत"
            percentMatcher.appendReplacement(percentSb, replacement)
        }
        percentMatcher.appendTail(percentSb)
        normalized = percentSb.toString()

        // 6. Convert remaining numbers to words (CRITICAL for C++ Engine)
        val numberPattern = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\b")
        val numberMatcher = numberPattern.matcher(normalized)
        val sb = StringBuffer()
        while (numberMatcher.find()) {
            val numStr = numberMatcher.group(1) ?: ""
            try {
                val replacement = if (numStr.contains(".")) {
                    NumberUtils.convertHindiDouble(numStr.toDouble())
                } else {
                    NumberUtils.convertHindi(numStr.toLong())
                }
                numberMatcher.appendReplacement(sb, replacement)
            } catch (_: Exception) {
                numberMatcher.appendReplacement(sb, numStr)
            }
        }
        numberMatcher.appendTail(sb)
        normalized = sb.toString()

        return normalized
    }

    private fun convertDevanagariDigits(text: String): String {
        val sb = StringBuilder()
        for (char in text) {
            val replaced = when (char) {
                '०' -> '0'
                '१' -> '1'
                '२' -> '2'
                '३' -> '3'
                '४' -> '4'
                '५' -> '5'
                '६' -> '6'
                '७' -> '7'
                '८' -> '8'
                '९' -> '9'
                else -> char
            }
            sb.append(replaced)
        }
        return sb.toString()
    }
}
