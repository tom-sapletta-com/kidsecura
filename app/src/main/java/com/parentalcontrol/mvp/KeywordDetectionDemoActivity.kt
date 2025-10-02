package com.parentalcontrol.mvp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.parentalcontrol.mvp.utils.MultilingualKeywordDetector
import com.parentalcontrol.mvp.utils.KeywordDetectionResult
import com.parentalcontrol.mvp.utils.DetectedKeyword
import com.parentalcontrol.mvp.utils.MatchType

/**
 * Strona demonstracyjna wielojęzycznej detekcji słów kluczowych
 * Pokazuje jak aplikacja wykrywa zabronione słowa w różnych językach
 */
class KeywordDetectionDemoActivity : AppCompatActivity() {
    
    private lateinit var detector: MultilingualKeywordDetector
    private lateinit var etTestInput: EditText
    private lateinit var tvNormalizedText: TextView
    private lateinit var tvDetectionResult: TextView
    private lateinit var recyclerDetectedKeywords: RecyclerView
    private lateinit var recyclerAllKeywords: RecyclerView
    private lateinit var spinnerLanguage: Spinner
    private lateinit var switchShowAll: Switch
    private lateinit var tvStats: TextView
    
    private var currentLanguages = listOf("pl", "en")
    private val detectedKeywordsAdapter = DetectedKeywordsAdapter()
    private val allKeywordsAdapter = AllKeywordsAdapter()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keyword_detection_demo)
        
        // Enable back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🔍 Demo Wykrywania Słów"
        
        detector = MultilingualKeywordDetector(this)
        
        initializeViews()
        setupLanguageSelection()
        setupTestInput()
        loadKeywordsForLanguages()
    }
    
    private fun initializeViews() {
        // Back button
        findViewById<Button>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
        
        etTestInput = findViewById(R.id.etTestInput)
        tvNormalizedText = findViewById(R.id.tvNormalizedText)
        tvDetectionResult = findViewById(R.id.tvDetectionResult)
        recyclerDetectedKeywords = findViewById(R.id.recyclerDetectedKeywords)
        recyclerAllKeywords = findViewById(R.id.recyclerAllKeywords)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        switchShowAll = findViewById(R.id.switchShowAll)
        tvStats = findViewById(R.id.tvStats)
        
        // Setup RecyclerViews
        recyclerDetectedKeywords.layoutManager = LinearLayoutManager(this)
        recyclerDetectedKeywords.adapter = detectedKeywordsAdapter
        
        recyclerAllKeywords.layoutManager = LinearLayoutManager(this)
        recyclerAllKeywords.adapter = allKeywordsAdapter
        
        // Toggle keywords list visibility
        switchShowAll.setOnCheckedChangeListener { _, isChecked ->
            recyclerAllKeywords.visibility = if (isChecked) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }
    }
    
    private fun setupLanguageSelection() {
        val languages = detector.getAvailableLanguages()
        val languageNames = languages.map { "${it.flag} ${it.name}" }
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languageNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = adapter
        
        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedLanguage = languages[position].code
                currentLanguages = if (selectedLanguage == "all") {
                    languages.map { it.code }.filter { it != "all" }
                } else {
                    listOf(selectedLanguage, "pl", "en").distinct()
                }
                detector.setActiveLanguages(currentLanguages)
                loadKeywordsForLanguages()
                performDetection()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupTestInput() {
        etTestInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performDetection()
            }
        })
        
        // Przykładowy tekst
        etTestInput.setText("Spróbuj wpisać: trawka, drugs, kokaina, cocaine, narkotyki...")
    }
    
    private fun performDetection() {
        val inputText = etTestInput.text.toString()
        
        if (inputText.isEmpty()) {
            tvNormalizedText.text = "Znormalizowany tekst pojawi się tutaj..."
            tvDetectionResult.text = ""
            detectedKeywordsAdapter.updateKeywords(emptyList())
            return
        }
        
        // Pokaż znormalizowany tekst
        val normalized = detector.normalizeText(inputText)
        tvNormalizedText.text = "Znormalizowany: $normalized"
        
        // Wykonaj detekcję
        val result = detector.detectKeywords(inputText)
        
        // Wyświetl wyniki
        displayDetectionResult(result)
        
        // Aktualizuj listę wykrytych słów
        detectedKeywordsAdapter.updateKeywords(result.keywords)
    }
    
    private fun displayDetectionResult(result: KeywordDetectionResult) {
        if (result.detected) {
            val exactMatches = result.keywords.count { it.matchType == MatchType.EXACT }
            val fuzzyMatches = result.keywords.count { it.matchType == MatchType.FUZZY }
            
            tvDetectionResult.text = buildString {
                append("⚠️ WYKRYTO ${result.totalMatches} ZAGROŻEŃ!\n")
                append("Dokładnych: $exactMatches, Podobnych: $fuzzyMatches\n")
                append("Języki: ${result.languages.joinToString(", ")}")
            }
            tvDetectionResult.setTextColor(getColor(R.color.danger))
            tvDetectionResult.setBackgroundColor(getColor(R.color.danger_container))
        } else {
            tvDetectionResult.text = "✅ Tekst bezpieczny - nie wykryto zagrożeń"
            tvDetectionResult.setTextColor(getColor(R.color.success))
            tvDetectionResult.setBackgroundColor(getColor(R.color.success_container))
        }
    }
    
    private fun loadKeywordsForLanguages() {
        // Załaduj wszystkie słowa kluczowe dla aktywnych języków
        val allKeywords = mutableListOf<KeywordInfo>()
        
        currentLanguages.forEach { langCode ->
            val langInfo = detector.getAvailableLanguages().find { it.code == langCode }
            if (langInfo != null) {
                // Tutaj można by pobrać słowa z detektora, ale na razie pokazujemy przykłady
                allKeywords.add(KeywordInfo(langCode, langInfo.name, getExampleKeywords(langCode)))
            }
        }
        
        allKeywordsAdapter.updateKeywords(allKeywords)
        
        // Aktualizuj statystyki
        val totalKeywords = allKeywords.sumOf { it.keywords.size }
        tvStats.text = "Aktywne języki: ${currentLanguages.size} | Słowa kluczowe: $totalKeywords"
    }
    
    private fun getExampleKeywords(langCode: String): List<String> {
        return when (langCode) {
            "pl" -> listOf(
                "narkotyki", "marihuana", "trawka", "zioło", "gandzia", "kokaina", 
                "koka", "amfa", "speed", "ecstasy", "lsd", "zabić", "samobójstwo",
                "gnojek", "frajer", "ciota", "pedał", "debil", "kurwa"
            )
            "en" -> listOf(
                "drugs", "weed", "marijuana", "pot", "dope", "cocaine", "coke",
                "heroin", "meth", "ecstasy", "lsd", "kill", "suicide", 
                "loser", "idiot", "stupid", "fag", "bitch", "fuck"
            )
            "de" -> listOf(
                "drogen", "gras", "weed", "kokain", "heroin", "töten",
                "selbstmord", "arschloch", "schlampe", "fick"
            )
            "fr" -> listOf(
                "drogue", "cannabis", "herbe", "cocaine", "héroïne", "tuer",
                "suicide", "connard", "salope", "pute"
            )
            "es" -> listOf(
                "drogas", "marihuana", "hierba", "cocaína", "heroína", "matar",
                "suicidio", "idiota", "puta", "cabrón"
            )
            "it" -> listOf(
                "droga", "erba", "cocaina", "eroina", "uccidere",
                "suicidio", "idiota", "puttana", "stronzo"
            )
            else -> emptyList()
        }
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    // Adapter dla wykrytych słów
    inner class DetectedKeywordsAdapter : RecyclerView.Adapter<DetectedKeywordsAdapter.ViewHolder>() {
        
        private var keywords = listOf<DetectedKeyword>()
        
        fun updateKeywords(newKeywords: List<DetectedKeyword>) {
            keywords = newKeywords
            notifyDataSetChanged()
        }
        
        inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val tvKeyword: TextView = view.findViewById(android.R.id.text1)
            val tvDetails: TextView = view.findViewById(android.R.id.text2)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(android.R.layout.two_line_list_item, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val keyword = keywords[position]
            val matchIcon = when (keyword.matchType) {
                MatchType.EXACT -> "✓"
                MatchType.FUZZY -> "≈"
                MatchType.PARTIAL -> "~"
            }
            
            holder.tvKeyword.text = "$matchIcon ${keyword.keyword}"
            holder.tvDetails.text = "Język: ${keyword.language.uppercase()} | Typ: ${keyword.matchType}"
            holder.tvKeyword.setTextColor(getColor(R.color.danger))
            holder.tvDetails.setTextColor(getColor(R.color.text_secondary))
        }
        
        override fun getItemCount() = keywords.size
    }
    
    // Adapter dla wszystkich słów kluczowych
    inner class AllKeywordsAdapter : RecyclerView.Adapter<AllKeywordsAdapter.ViewHolder>() {
        
        private var keywordsByLanguage = listOf<KeywordInfo>()
        
        fun updateKeywords(keywords: List<KeywordInfo>) {
            keywordsByLanguage = keywords
            notifyDataSetChanged()
        }
        
        inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val tvLanguage: TextView = view.findViewById(R.id.tvLanguage)
            val tvKeywords: TextView = view.findViewById(R.id.tvKeywords)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_language_keywords, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val info = keywordsByLanguage[position]
            holder.tvLanguage.text = "${info.languageName} (${info.keywords.size} słów)"
            holder.tvKeywords.text = info.keywords.take(10).joinToString(", ") + 
                if (info.keywords.size > 10) "..." else ""
        }
        
        override fun getItemCount() = keywordsByLanguage.size
    }
    
    data class KeywordInfo(
        val languageCode: String,
        val languageName: String,
        val keywords: List<String>
    )
}
