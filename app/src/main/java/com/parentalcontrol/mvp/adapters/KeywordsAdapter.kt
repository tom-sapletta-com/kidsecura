package com.parentalcontrol.mvp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.parentalcontrol.mvp.R

/**
 * 🔍 Adapter dla listy słów kluczowych w Keywords Tester
 * Obsługuje edycję i usuwanie słów kluczowych w czasie rzeczywistym
 */
class KeywordsAdapter(
    private val keywords: MutableList<String>,
    private val onKeywordRemove: (String) -> Unit,
    private val onKeywordEdit: (String, String) -> Unit
) : RecyclerView.Adapter<KeywordsAdapter.KeywordViewHolder>() {
    
    companion object {
        private const val TAG = "KeywordsAdapter"
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeywordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_keyword, parent, false)
        return KeywordViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: KeywordViewHolder, position: Int) {
        val keyword = keywords[position]
        holder.bind(keyword)
    }
    
    override fun getItemCount(): Int = keywords.size
    
    inner class KeywordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val keywordText: TextView = itemView.findViewById(R.id.keywordText)
        private val editButton: ImageButton = itemView.findViewById(R.id.editButton)
        private val removeButton: ImageButton = itemView.findViewById(R.id.removeButton)
        private val priorityIndicator: View = itemView.findViewById(R.id.priorityIndicator)
        
        fun bind(keyword: String) {
            keywordText.text = keyword
            
            // Set priority indicator color based on keyword type
            val priorityColor = when {
                isHighPriorityKeyword(keyword) -> R.color.status_danger
                isMediumPriorityKeyword(keyword) -> R.color.status_warning
                else -> R.color.status_safe
            }
            priorityIndicator.setBackgroundColor(itemView.context.getColor(priorityColor))
            
            // Edit button
            editButton.setOnClickListener {
                showEditDialog(keyword)
            }
            
            // Remove button
            removeButton.setOnClickListener {
                showRemoveDialog(keyword)
            }
        }
        
        private fun showEditDialog(keyword: String) {
            val context = itemView.context
            val input = EditText(context)
            input.setText(keyword)
            input.selectAll()
            
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("✏️ Edytuj słowo kluczowe")
                .setMessage("Zmień słowo kluczowe:")
                .setView(input)
                .setPositiveButton("Zapisz") { _, _ ->
                    val newKeyword = input.text.toString().trim()
                    if (newKeyword.isNotEmpty() && newKeyword != keyword) {
                        onKeywordEdit(keyword, newKeyword)
                    }
                }
                .setNegativeButton("Anuluj", null)
                .show()
        }
        
        private fun showRemoveDialog(keyword: String) {
            val context = itemView.context
            
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("🗑️ Usuń słowo kluczowe")
                .setMessage("Czy na pewno chcesz usunąć słowo \"$keyword\"?")
                .setPositiveButton("Usuń") { _, _ ->
                    onKeywordRemove(keyword)
                }
                .setNegativeButton("Anuluj", null)
                .show()
        }
        
        private fun isHighPriorityKeyword(keyword: String): Boolean {
            val highPriorityKeywords = listOf(
                "zabić", "zabije", "zabiję", "śmierć", "samobójstwo", "krzywda",
                "narkotyki", "kokaina", "heroina", "sex", "porno"
            )
            return highPriorityKeywords.any { it.equals(keyword, ignoreCase = true) }
        }
        
        private fun isMediumPriorityKeyword(keyword: String): Boolean {
            val mediumPriorityKeywords = listOf(
                "ból", "smutek", "depresja", "lęk", "strach", "alkohol", "piwo"
            )
            return mediumPriorityKeywords.any { it.equals(keyword, ignoreCase = true) }
        }
    }
}
