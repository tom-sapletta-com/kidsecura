package com.parentalcontrol.mvp.utils

import android.content.Context
import java.text.Normalizer
import java.util.*

/**
 * Wielojęzyczny detektor słów kluczowych
 * 
 * Funkcje:
 * - Normalizacja tekstu (usuwanie polskich znaków, diakrytyki)
 * - Wykrywanie słów w wielu językach jednocześnie (PL, EN, DE, FR, ES, IT)
 * - Wsparcie dla slangu lokalnego i regionalnego
 * - Fuzzy matching (tolerancja literówek)
 * - Case-insensitive porównywanie
 */
class MultilingualKeywordDetector(private val context: Context) {

    private val prefsManager = PreferencesManager(context)
    
    companion object {
        private const val TAG = "MultilingualDetector"
        
        // Domyślne języki aktywne
        private val DEFAULT_LANGUAGES = listOf("pl", "en")
        
        // Mapowanie polskich znaków na łacińskie
        private val POLISH_TO_LATIN = mapOf(
            'ą' to 'a', 'Ą' to 'A',
            'ć' to 'c', 'Ć' to 'C',
            'ę' to 'e', 'Ę' to 'E',
            'ł' to 'l', 'Ł' to 'L',
            'ń' to 'n', 'Ń' to 'N',
            'ó' to 'o', 'Ó' to 'O',
            'ś' to 's', 'Ś' to 'S',
            'ź' to 'z', 'Ź' to 'Z',
            'ż' to 'z', 'Ż' to 'Z'
        )
        
        // Słowa kluczowe dla każdego języka z wariantami slangowymi
        private val KEYWORD_DATABASE = mapOf(
            // Polski + slang regionalny
            "pl" to listOf(
                // Narkotyki
                "narkotyki", "marihuana", "trawka", "zioło", "gandzia", "skun", "joint",
                "kokaina", "koka", "biały", "amfa", "amfetamina", "speed", "metamfetamina",
                "ecstasy", "eksta", "pigułki", "lsd", "grzyby", "haszysz",
                
                // Przemoc
                "bić", "zabić", "pobić", "zamordować", "znęcać się", "krzywda",
                "samobójstwo", "popełnić samobójstwo", "skończyć z sobą", "targnąć się na życie",
                
                // Cyberbullying
                "gnojek", "frajer", "ciota", "pedał", "dziwka", "kurwa", "szmata",
                "idiota", "debil", "kretyn", "głupek", "dureń", "ciul", "chuj",
                "pierdol się", "spierdalaj", "jebać", "wypierdalać",
                
                // Zagrożenia online
                "nagie zdjęcia", "nago", "porno", "seks", "ruchać", "wyruchać",
                "spotkanie", "spotykamy się", "adres", "gdzie mieszkasz",
                "numer telefonu", "konto bankowe", "hasło", "pin"
            ),
            
            // Angielski + slang
            "en" to listOf(
                // Drugs
                "drugs", "weed", "marijuana", "pot", "dope", "grass", "ganja", "bud",
                "cocaine", "coke", "crack", "snow", "blow",
                "heroin", "smack", "junk", "horse",
                "meth", "crystal", "ice", "speed", "amphetamine",
                "ecstasy", "molly", "mdma", "pills",
                "lsd", "acid", "tabs", "shrooms", "mushrooms",
                
                // Violence  
                "kill", "murder", "hurt", "beat", "punch", "stab", "shoot",
                "suicide", "kill myself", "end my life", "hang myself",
                
                // Cyberbullying
                "loser", "idiot", "stupid", "dumb", "retard", "fag", "faggot",
                "bitch", "slut", "whore", "asshole", "bastard", "fuck you",
                "kys", "kill yourself", "die", "hate you",
                
                // Online threats
                "nude", "naked", "dick pic", "send nudes", "porn", "sex",
                "meet up", "where do you live", "your address",
                "phone number", "bank account", "password", "credit card"
            ),
            
            // Niemiecki + slang
            "de" to listOf(
                "drogen", "gras", "weed", "kokain", "koks", "heroin",
                "töten", "umbringen", "schlagen", "selbstmord",
                "arschloch", "schlampe", "hurensohn", "fick dich",
                "nackt", "pornografie", "treffen", "adresse"
            ),
            
            // Francuski + slang
            "fr" to listOf(
                "drogue", "cannabis", "herbe", "cocaine", "coke", "héroïne",
                "tuer", "frapper", "suicide",
                "connard", "salope", "pute", "va te faire foutre",
                "nu", "porno", "rencontre", "adresse"
            ),
            
            // Hiszpański + slang
            "es" to listOf(
                "drogas", "marihuana", "hierba", "coca", "cocaína", "heroína",
                "matar", "golpear", "suicidio",
                "idiota", "puta", "cabrón", "vete a la mierda",
                "desnudo", "porno", "encuentro", "dirección"
            ),
            
            // Włoski + slang
            "it" to listOf(
                "droga", "erba", "cocaina", "eroina",
                "uccidere", "picchiare", "suicidio",
                "idiota", "puttana", "stronzo", "vaffanculo",
                "nudo", "porno", "incontro", "indirizzo"
            )
        )
    }
    
    /**
     * Normalizuje tekst - usuwa polskie znaki i diakrytykę
     */
    fun normalizeText(text: String): String {
        // Zamień polskie znaki
        var normalized = text.map { POLISH_TO_LATIN[it] ?: it }.joinToString("")
        
        // Usuń resztę diakrytyki (dla innych języków)
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
            .replace("\\p{M}".toRegex(), "")
        
        return normalized.lowercase(Locale.ROOT)
    }
    
    /**
     * Wykrywa słowa kluczowe w tekście dla wszystkich aktywnych języków
     */
    fun detectKeywords(text: String): KeywordDetectionResult {
        val activeLanguages = getActiveLanguages()
        val normalizedText = normalizeText(text)
        val detectedKeywords = mutableListOf<DetectedKeyword>()
        
        // Przeszukaj wszystkie aktywne języki
        for (language in activeLanguages) {
            val keywords = KEYWORD_DATABASE[language] ?: continue
            
            for (keyword in keywords) {
                val normalizedKeyword = normalizeText(keyword)
                
                // Dokładne dopasowanie
                if (normalizedText.contains(normalizedKeyword)) {
                    detectedKeywords.add(
                        DetectedKeyword(
                            keyword = keyword,
                            language = language,
                            matchType = MatchType.EXACT,
                            position = normalizedText.indexOf(normalizedKeyword)
                        )
                    )
                }
                // Fuzzy matching - tolerancja 1-2 literówek
                else if (fuzzyMatch(normalizedText, normalizedKeyword)) {
                    detectedKeywords.add(
                        DetectedKeyword(
                            keyword = keyword,
                            language = language,
                            matchType = MatchType.FUZZY,
                            position = findFuzzyPosition(normalizedText, normalizedKeyword)
                        )
                    )
                }
            }
        }
        
        return KeywordDetectionResult(
            detected = detectedKeywords.isNotEmpty(),
            keywords = detectedKeywords,
            totalMatches = detectedKeywords.size,
            languages = detectedKeywords.map { it.language }.distinct()
        )
    }
    
    /**
     * Pobiera aktywne języki z preferencji
     */
    private fun getActiveLanguages(): List<String> {
        val savedLanguages = prefsManager.getString("active_languages", null)
        return if (savedLanguages != null) {
            savedLanguages.split(",")
        } else {
            DEFAULT_LANGUAGES
        }
    }
    
    /**
     * Ustawia aktywne języki
     */
    fun setActiveLanguages(languages: List<String>) {
        prefsManager.setString("active_languages", languages.joinToString(","))
    }
    
    /**
     * Pobiera dostępne języki
     */
    fun getAvailableLanguages(): List<LanguageInfo> {
        return listOf(
            LanguageInfo("pl", "Polski", "🇵🇱"),
            LanguageInfo("en", "English", "🇬🇧"),
            LanguageInfo("de", "Deutsch", "🇩🇪"),
            LanguageInfo("fr", "Français", "🇫🇷"),
            LanguageInfo("es", "Español", "🇪🇸"),
            LanguageInfo("it", "Italiano", "🇮🇹")
        )
    }
    
    /**
     * Fuzzy matching - sprawdza czy słowa są podobne (tolerancja literówek)
     */
    private fun fuzzyMatch(text: String, keyword: String, tolerance: Int = 2): Boolean {
        val words = text.split("\\s+".toRegex())
        return words.any { word ->
            levenshteinDistance(word, keyword) <= tolerance && 
            word.length >= keyword.length - 2
        }
    }
    
    /**
     * Oblicza odległość Levenshteina między dwoma stringami
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val dist = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) dist[i][0] = i
        for (j in 0..len2) dist[0][j] = j
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dist[i][j] = minOf(
                    dist[i - 1][j] + 1,      // deletion
                    dist[i][j - 1] + 1,      // insertion
                    dist[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return dist[len1][len2]
    }
    
    /**
     * Znajduje pozycję fuzzy match w tekście
     */
    private fun findFuzzyPosition(text: String, keyword: String): Int {
        val words = text.split("\\s+".toRegex())
        var position = 0
        
        for (word in words) {
            if (levenshteinDistance(word, keyword) <= 2) {
                return position
            }
            position += word.length + 1 // +1 for space
        }
        
        return 0
    }
    
    /**
     * Dodaje niestandardowe słowo kluczowe dla języka
     */
    fun addCustomKeyword(language: String, keyword: String) {
        val customKey = "custom_keywords_$language"
        val existing = prefsManager.getString(customKey, "") ?: ""
        val updated = if (existing.isEmpty()) {
            keyword
        } else {
            "$existing,$keyword"
        }
        prefsManager.setString(customKey, updated)
    }
    
    /**
     * Pobiera niestandardowe słowa kluczowe
     */
    fun getCustomKeywords(language: String): List<String> {
        val customKey = "custom_keywords_$language"
        val keywords = prefsManager.getString(customKey, "") ?: ""
        return if (keywords.isEmpty()) emptyList() else keywords.split(",")
    }
}

/**
 * Wynik detekcji słów kluczowych
 */
data class KeywordDetectionResult(
    val detected: Boolean,
    val keywords: List<DetectedKeyword>,
    val totalMatches: Int,
    val languages: List<String>
)

/**
 * Wykryte słowo kluczowe
 */
data class DetectedKeyword(
    val keyword: String,
    val language: String,
    val matchType: MatchType,
    val position: Int
)

/**
 * Typ dopasowania
 */
enum class MatchType {
    EXACT,      // Dokładne dopasowanie
    FUZZY,      // Fuzzy match (z tolerancją literówek)
    PARTIAL     // Częściowe dopasowanie
}

/**
 * Informacje o języku
 */
data class LanguageInfo(
    val code: String,
    val name: String,
    val flag: String
)
