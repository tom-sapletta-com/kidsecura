package com.parentalcontrol.mvp

import android.os.Bundle
import com.parentalcontrol.mvp.adapters.KeywordsAdapter
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.parentalcontrol.mvp.utils.PreferencesManager
import com.parentalcontrol.mvp.utils.SystemLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 🔍 Advanced Keywords Testing UI
 * Interaktywny tester słów kluczowych z live preview wyników
 * Pozwala testować wykrywanie zagrożeń w czasie rzeczywistym
 */
class KeywordsTesterActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "KeywordsTesterActivity"
        private const val DEBOUNCE_DELAY = 300L // ms
    }
    
    // Core components
    private lateinit var systemLogger: SystemLogger
    private lateinit var preferencesManager: PreferencesManager
    
    // UI components
    private lateinit var testInput: EditText
    private lateinit var resultContainer: LinearLayout
    private lateinit var threatLevelIcon: ImageView
    private lateinit var threatLevelText: TextView
    private lateinit var detectedKeywordsText: TextView
    private lateinit var suggestionsText: TextView
    private lateinit var keywordsRecyclerView: RecyclerView
    private lateinit var addKeywordButton: Button
    private lateinit var resetTestButton: Button
    private lateinit var exportResultsButton: Button
    
    // Adapters
    private lateinit var keywordsAdapter: KeywordsAdapter
    
    // State
    private var currentKeywords: MutableList<String> = mutableListOf()
    private var testingJob: Job? = null
    private var lastTestResult: ThreatTestResult? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keywords_tester)
        
        // Enable back button in action bar with custom icon
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🔍 Tester Słów Kluczowych"
        
        systemLogger = SystemLogger(this)
        preferencesManager = PreferencesManager(this)
        
        systemLogger.d(TAG, "🔍 Starting Keywords Tester Activity")
        
        initializeViews()
        setupRecyclerView()
        setupListeners()
        loadCurrentKeywords()
        
        // Show sample test text
        testInput.setText("Przykładowy tekst do testowania wykrywania słów kluczowych...")
        performThreatAnalysis()
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Navigate back to MainActivity
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun initializeViews() {
        // Back button
        val btnBack = findViewById<android.widget.Button>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish() // Zamknij activity i wróć do MainActivity
        }
        
        // Test input
        testInput = findViewById(R.id.testInput)
        
        // Results container
        resultContainer = findViewById(R.id.resultContainer)
        threatLevelIcon = findViewById(R.id.threatLevelIcon)
        threatLevelText = findViewById(R.id.threatLevelText)
        detectedKeywordsText = findViewById(R.id.detectedKeywordsText)
        suggestionsText = findViewById(R.id.suggestionsText)
        
        // Keywords management
        keywordsRecyclerView = findViewById(R.id.keywordsRecyclerView)
        addKeywordButton = findViewById(R.id.addKeywordButton)
        
        // Action buttons
        resetTestButton = findViewById(R.id.resetTestButton)
        exportResultsButton = findViewById(R.id.exportResultsButton)
        
        // Set up toolbar (may not be visible due to theme, but keep for compatibility)
        supportActionBar?.title = "🔍 Tester słów kluczowych"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    
    private fun setupRecyclerView() {
        keywordsAdapter = KeywordsAdapter(
            keywords = currentKeywords,
            onKeywordRemove = { keyword ->
                removeKeyword(keyword)
            },
            onKeywordEdit = { oldKeyword, newKeyword ->
                editKeyword(oldKeyword, newKeyword)
            }
        )
        
        keywordsRecyclerView.layoutManager = LinearLayoutManager(this)
        keywordsRecyclerView.adapter = keywordsAdapter
    }
    
    private fun setupListeners() {
        // Real-time testing with debounce
        testInput.addTextChangedListener { editable ->
            testingJob?.cancel()
            testingJob = lifecycleScope.launch {
                delay(DEBOUNCE_DELAY)
                if (editable != null) {
                    performThreatAnalysis()
                }
            }
        }
        
        // Add new keyword
        addKeywordButton.setOnClickListener {
            showAddKeywordDialog()
        }
        
        // Reset test input
        resetTestButton.setOnClickListener {
            testInput.setText("")
            clearResults()
        }
        
        // Export test results
        exportResultsButton.setOnClickListener {
            exportTestResults()
        }
    }
    
    private fun loadCurrentKeywords() {
        currentKeywords.clear()
        currentKeywords.addAll(preferencesManager.getThreatKeywords())
        keywordsAdapter.notifyDataSetChanged()
        
        systemLogger.d(TAG, "📋 Loaded ${currentKeywords.size} keywords")
    }
    
    private fun performThreatAnalysis() {
        val testText = testInput.text.toString().trim()
        
        if (testText.isEmpty()) {
            clearResults()
            return
        }
        
        systemLogger.d(TAG, "🔍 Performing threat analysis on: ${testText.take(50)}...")
        
        // Perform keyword matching
        val detectedKeywords = mutableListOf<String>()
        val testTextLower = testText.lowercase()
        
        for (keyword in currentKeywords) {
            if (testTextLower.contains(keyword.lowercase())) {
                detectedKeywords.add(keyword)
            }
        }
        
        // Calculate threat level
        val threatLevel = when {
            detectedKeywords.isEmpty() -> ThreatLevel.SAFE
            detectedKeywords.size <= 2 -> ThreatLevel.LOW
            detectedKeywords.size <= 4 -> ThreatLevel.MEDIUM
            else -> ThreatLevel.HIGH
        }
        
        val result = ThreatTestResult(
            inputText = testText,
            detectedKeywords = detectedKeywords,
            threatLevel = threatLevel,
            suggestions = generateSuggestions(detectedKeywords, threatLevel)
        )
        
        lastTestResult = result
        displayResults(result)
    }
    
    private fun displayResults(result: ThreatTestResult) {
        // Show results container
        resultContainer.visibility = LinearLayout.VISIBLE
        
        // Update threat level
        when (result.threatLevel) {
            ThreatLevel.SAFE -> {
                threatLevelIcon.setImageResource(R.drawable.ic_safe)
                threatLevelText.text = "✅ BEZPIECZNY"
                threatLevelText.setTextColor(getColor(R.color.status_safe))
            }
            ThreatLevel.LOW -> {
                threatLevelIcon.setImageResource(R.drawable.ic_warning_low)
                threatLevelText.text = "⚠️ NISKIE RYZYKO"
                threatLevelText.setTextColor(getColor(R.color.status_warning))
            }
            ThreatLevel.MEDIUM -> {
                threatLevelIcon.setImageResource(R.drawable.ic_warning_medium)
                threatLevelText.text = "🔶 ŚREDNIE RYZYKO"
                threatLevelText.setTextColor(getColor(R.color.status_medium))
            }
            ThreatLevel.HIGH -> {
                threatLevelIcon.setImageResource(R.drawable.ic_danger)
                threatLevelText.text = "🚨 WYSOKIE RYZYKO"
                threatLevelText.setTextColor(getColor(R.color.status_danger))
            }
        }
        
        // Update detected keywords
        if (result.detectedKeywords.isNotEmpty()) {
            detectedKeywordsText.text = "🎯 Wykryte słowa: ${result.detectedKeywords.joinToString(", ")}"
            detectedKeywordsText.visibility = TextView.VISIBLE
        } else {
            detectedKeywordsText.visibility = TextView.GONE
        }
        
        // Update suggestions
        if (result.suggestions.isNotEmpty()) {
            suggestionsText.text = "💡 Sugestie:\n${result.suggestions.joinToString("\n• ", "• ")}"
            suggestionsText.visibility = TextView.VISIBLE
        } else {
            suggestionsText.visibility = TextView.GONE
        }
        
        systemLogger.d(TAG, "📊 Displayed results: ${result.threatLevel}, keywords: ${result.detectedKeywords.size}")
    }
    
    private fun clearResults() {
        resultContainer.visibility = LinearLayout.GONE
        lastTestResult = null
    }
    
    private fun generateSuggestions(detectedKeywords: List<String>, threatLevel: ThreatLevel): List<String> {
        val suggestions = mutableListOf<String>()
        
        when (threatLevel) {
            ThreatLevel.SAFE -> {
                suggestions.add("Tekst wydaje się bezpieczny")
                suggestions.add("Brak wykrytych słów kluczowych")
            }
            ThreatLevel.LOW -> {
                suggestions.add("Monitoruj dalsze rozmowy")
                suggestions.add("Rozważ rozmowę z dzieckiem o bezpieczeństwie")
            }
            ThreatLevel.MEDIUM -> {
                suggestions.add("Zalecana interwencja rodzica")
                suggestions.add("Sprawdź kontekst rozmowy")
                suggestions.add("Rozważ ograniczenie dostępu do aplikacji")
            }
            ThreatLevel.HIGH -> {
                suggestions.add("NATYCHMIASTOWA INTERWENCJA WYMAGANA")
                suggestions.add("Skontaktuj się z dzieckiem")
                suggestions.add("Rozważ kontakt z odpowiednimi służbami")
                suggestions.add("Zachowaj dowody rozmowy")
            }
        }
        
        return suggestions
    }
    
    private fun showAddKeywordDialog() {
        val input = EditText(this)
        input.hint = "Wprowadź nowe słowo kluczowe"
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("➕ Dodaj słowo kluczowe")
            .setMessage("Dodaj nowe słowo do listy monitorowanych:")
            .setView(input)
            .setPositiveButton("Dodaj") { _, _ ->
                val keyword = input.text.toString().trim()
                if (keyword.isNotEmpty() && !currentKeywords.contains(keyword)) {
                    addKeyword(keyword)
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }
    
    private fun addKeyword(keyword: String) {
        currentKeywords.add(keyword)
        keywordsAdapter.notifyItemInserted(currentKeywords.size - 1)
        saveKeywords()
        
        // Re-run analysis with new keyword
        performThreatAnalysis()
        
        systemLogger.d(TAG, "➕ Added keyword: $keyword")
        Toast.makeText(this, "Dodano słowo: $keyword", Toast.LENGTH_SHORT).show()
    }
    
    private fun removeKeyword(keyword: String) {
        val index = currentKeywords.indexOf(keyword)
        if (index != -1) {
            currentKeywords.removeAt(index)
            keywordsAdapter.notifyItemRemoved(index)
            saveKeywords()
            
            // Re-run analysis without removed keyword
            performThreatAnalysis()
            
            systemLogger.d(TAG, "🗑️ Removed keyword: $keyword")
            Toast.makeText(this, "Usunięto słowo: $keyword", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun editKeyword(oldKeyword: String, newKeyword: String) {
        val index = currentKeywords.indexOf(oldKeyword)
        if (index != -1 && newKeyword.isNotEmpty()) {
            currentKeywords[index] = newKeyword
            keywordsAdapter.notifyItemChanged(index)
            saveKeywords()
            
            // Re-run analysis with updated keyword
            performThreatAnalysis()
            
            systemLogger.d(TAG, "✏️ Edited keyword: $oldKeyword -> $newKeyword")
            Toast.makeText(this, "Zaktualizowano słowo", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveKeywords() {
        preferencesManager.setThreatKeywords(currentKeywords)
        systemLogger.d(TAG, "💾 Saved ${currentKeywords.size} keywords")
    }
    
    private fun exportTestResults() {
        val result = lastTestResult
        if (result == null) {
            Toast.makeText(this, "Brak wyników do eksportu", Toast.LENGTH_SHORT).show()
            return
        }
        
        val exportText = buildString {
            appendLine("=== WYNIKI TESTU SŁÓW KLUCZOWYCH ===")
            appendLine("Data: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
            appendLine()
            appendLine("TEKST TESTOWY:")
            appendLine(result.inputText)
            appendLine()
            appendLine("POZIOM ZAGROŻENIA: ${result.threatLevel}")
            appendLine("WYKRYTE SŁOWA: ${result.detectedKeywords.joinToString(", ")}")
            appendLine()
            appendLine("SUGESTIE:")
            result.suggestions.forEach { appendLine("• $it") }
        }
        
        // For now, just copy to clipboard
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Test Results", exportText)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, "📋 Wyniki skopiowane do schowka", Toast.LENGTH_SHORT).show()
        systemLogger.d(TAG, "📤 Exported test results")
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    // Data classes
    data class ThreatTestResult(
        val inputText: String,
        val detectedKeywords: List<String>,
        val threatLevel: ThreatLevel,
        val suggestions: List<String>
    )
    
    enum class ThreatLevel {
        SAFE, LOW, MEDIUM, HIGH
    }
}
