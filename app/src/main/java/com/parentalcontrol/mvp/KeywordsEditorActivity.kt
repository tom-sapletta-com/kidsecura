package com.parentalcontrol.mvp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.parentalcontrol.mvp.databinding.ActivityKeywordsEditorBinding
import com.parentalcontrol.mvp.utils.PreferencesManager

class KeywordsEditorActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityKeywordsEditorBinding
    private lateinit var prefsManager: PreferencesManager
    
    companion object {
        private const val TAG = "KeywordsEditorActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeywordsEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefsManager = PreferencesManager(this)
        
        setupUI()
        loadKeywords()
    }
    
    private fun setupUI() {
        binding.apply {
            // Przycisk powrotu
            btnBack.setOnClickListener {
                finish()
            }
            
            // Przycisk dodawania nowego słowa
            btnAddKeyword.setOnClickListener {
                showAddKeywordDialog()
            }
            
            // Przycisk resetowania do domyślnych
            btnResetDefaults.setOnClickListener {
                showResetDialog()
            }
            
            // Przycisk zapisywania
            btnSave.setOnClickListener {
                saveKeywords()
            }
        }
    }
    
    private fun loadKeywords() {
        val keywords = prefsManager.getThreatKeywords()
        
        binding.layoutKeywords.removeAllViews()
        
        if (keywords.isEmpty()) {
            showEmptyState()
        } else {
            keywords.forEach { keyword ->
                addKeywordView(keyword)
            }
        }
        
        updateKeywordCount()
    }
    
    private fun showEmptyState() {
        val emptyView = TextView(this).apply {
            text = getString(R.string.no_keywords_message)
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@KeywordsEditorActivity, android.R.color.darker_gray))
            setPadding(16, 32, 16, 32)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        binding.layoutKeywords.addView(emptyView)
    }
    
    private fun addKeywordView(keyword: String) {
        val keywordView = LayoutInflater.from(this)
            .inflate(R.layout.item_keyword, binding.layoutKeywords, false)
        
        val tvKeyword = keywordView.findViewById<TextView>(R.id.keywordText)
        val btnDelete = keywordView.findViewById<ImageButton>(R.id.removeButton)
        
        tvKeyword.text = keyword
        
        btnDelete.setOnClickListener {
            showDeleteConfirmDialog(keyword) {
                binding.layoutKeywords.removeView(keywordView)
                updateKeywordCount()
            }
        }
        
        binding.layoutKeywords.addView(keywordView)
    }
    
    private fun showAddKeywordDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.enter_keyword_hint)
            setPadding(32, 16, 32, 16)
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.add_keyword_title)
            .setMessage(R.string.add_keyword_message)
            .setView(input)
            .setPositiveButton(R.string.add) { _, _ ->
                val keyword = input.text.toString().trim().lowercase()
                if (keyword.isNotEmpty()) {
                    if (isKeywordAlreadyExists(keyword)) {
                        Toast.makeText(this, R.string.keyword_already_exists, Toast.LENGTH_SHORT).show()
                    } else {
                        addKeywordView(keyword)
                        updateKeywordCount()
                    }
                } else {
                    Toast.makeText(this, R.string.keyword_cannot_be_empty, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showDeleteConfirmDialog(keyword: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_keyword_title)
            .setMessage(getString(R.string.delete_keyword_message, keyword))
            .setPositiveButton(R.string.delete) { _, _ ->
                onConfirm()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showResetDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_keywords_title)
            .setMessage(R.string.reset_keywords_message)
            .setPositiveButton(R.string.reset) { _, _ ->
                resetToDefaults()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun resetToDefaults() {
        prefsManager.resetThreatKeywordsToDefault()
        loadKeywords()
        Toast.makeText(this, R.string.keywords_reset_success, Toast.LENGTH_SHORT).show()
    }
    
    private fun isKeywordAlreadyExists(keyword: String): Boolean {
        for (i in 0 until binding.layoutKeywords.childCount) {
            val child = binding.layoutKeywords.getChildAt(i)
            val tvKeyword = child.findViewById<TextView>(R.id.keywordText)
            if (tvKeyword?.text?.toString()?.lowercase() == keyword) {
                return true
            }
        }
        return false
    }
    
    private fun getCurrentKeywords(): List<String> {
        val keywords = mutableListOf<String>()
        for (i in 0 until binding.layoutKeywords.childCount) {
            val child = binding.layoutKeywords.getChildAt(i)
            val tvKeyword = child.findViewById<TextView>(R.id.keywordText)
            tvKeyword?.text?.toString()?.let { keywords.add(it) }
        }
        return keywords
    }
    
    private fun saveKeywords() {
        val keywords = getCurrentKeywords()
        prefsManager.setThreatKeywords(keywords)
        Toast.makeText(this, R.string.keywords_saved_success, Toast.LENGTH_SHORT).show()
        finish()
    }
    
    private fun updateKeywordCount() {
        val count = getCurrentKeywords().size
        binding.tvKeywordCount.text = getString(R.string.keyword_count, count)
    }
}
