package com.parentalcontrol.mvp

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.parentalcontrol.mvp.model.DeviceType
import com.parentalcontrol.mvp.service.PairingService
import com.parentalcontrol.mvp.utils.PreferencesManager
import com.parentalcontrol.mvp.utils.SystemLogger
import kotlinx.coroutines.launch

/**
 * 🧙‍♂️ PairingWizardActivity - Step-by-step pairing wizard for non-technical users
 * 
 * Guides users through the complete pairing process with simple, intuitive steps:
 * 1. Device type selection (Parent/Child)
 * 2. Contact information setup
 * 3. Connection establishment
 * 4. Pairing code exchange
 * 5. Success confirmation
 */
class PairingWizardActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "PairingWizardActivity"
    }
    
    // UI Components
    private lateinit var progressBar: ProgressBar
    private lateinit var stepTitle: TextView
    private lateinit var stepDescription: TextView
    private lateinit var stepContainer: LinearLayout
    private lateinit var nextButton: Button
    private lateinit var backButton: Button
    private lateinit var skipButton: Button
    
    // Wizard state
    private var currentStep = 1
    private val totalSteps = 5
    
    // Device type selection
    private var selectedDeviceType: DeviceType? = null
    private lateinit var parentRadioButton: RadioButton
    private lateinit var childRadioButton: RadioButton
    
    // Contact information
    private lateinit var parentPhoneInput: EditText
    private lateinit var parentEmailInput: EditText
    
    // Pairing components
    private lateinit var systemLogger: SystemLogger
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var pairingService: PairingService
    
    // Pairing state
    private var generatedPairingCode: String? = null
    private var isParingInProgress = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing_wizard)
        
        systemLogger = SystemLogger(this)
        preferencesManager = PreferencesManager(this)
        pairingService = PairingService(this, systemLogger, preferencesManager)
        
        systemLogger.d(TAG, "🧙‍♂️ Starting Pairing Wizard")
        
        initializeViews()
        setupClickListeners()
        showStep(currentStep)
    }
    
    private fun initializeViews() {
        // Progress and navigation
        progressBar = findViewById(R.id.progressBar)
        stepTitle = findViewById(R.id.stepTitle)
        stepDescription = findViewById(R.id.stepDescription)
        stepContainer = findViewById(R.id.stepContainer)
        
        nextButton = findViewById(R.id.nextButton)
        backButton = findViewById(R.id.backButton)
        skipButton = findViewById(R.id.skipButton)
        
        // Set progress bar max
        progressBar.max = totalSteps
        progressBar.progress = currentStep
    }
    
    private fun setupClickListeners() {
        nextButton.setOnClickListener {
            handleNextStep()
        }
        
        backButton.setOnClickListener {
            handlePreviousStep()
        }
        
        skipButton.setOnClickListener {
            handleSkipWizard()
        }
    }
    
    private fun showStep(step: Int) {
        currentStep = step
        progressBar.progress = step
        
        // Clear previous step content
        stepContainer.removeAllViews()
        
        when (step) {
            1 -> showWelcomeStep()
            2 -> showDeviceTypeStep()
            3 -> showContactInfoStep()
            4 -> showPairingStep()
            5 -> showSuccessStep()
        }
        
        updateNavigationButtons()
        systemLogger.d(TAG, "📍 Showing wizard step: $step/$totalSteps")
    }
    
    // ===== STEP 1: WELCOME =====
    private fun showWelcomeStep() {
        stepTitle.text = "🎉 Witaj w KidSecura!"
        stepDescription.text = "Ten krótki kreator pomoże Ci skonfigurować aplikację krok po krok. Zajmie to tylko kilka minut."
        
        val welcomeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        val featuresList = TextView(this).apply {
            text = """
                ✅ Bezpieczne monitorowanie urządzenia dziecka
                ✅ Natychmiastowe alerty o zagrożeniach
                ✅ Komunikacja przez Telegram/WhatsApp
                ✅ Tryb ukryty dla dyskretnego działania
                ✅ Bezpieczne połączenie między urządzeniami
                
                📱 Potrzebujesz dwóch urządzeń Android:
                • Urządzenie rodzica (to urządzenie)
                • Urządzenie dziecka (do monitorowania)
            """.trimIndent()
            textSize = 16f
            setPadding(16, 16, 16, 16)
        }
        
        welcomeLayout.addView(featuresList)
        stepContainer.addView(welcomeLayout)
        
        nextButton.text = "🚀 Rozpocznij konfigurację"
    }
    
    // ===== STEP 2: DEVICE TYPE SELECTION =====
    private fun showDeviceTypeStep() {
        stepTitle.text = "📱 Wybierz typ urządzenia"
        stepDescription.text = "Czy konfigurujesz urządzenie rodzica czy dziecka?"
        
        val deviceTypeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        val radioGroup = RadioGroup(this)
        
        parentRadioButton = RadioButton(this).apply {
            text = "👨‍👩‍👧‍👦 Urządzenie RODZICA\n• Będzie odbierać alerty\n• Może zdalnie kontrolować ustawienia"
            textSize = 16f
            setPadding(16, 16, 16, 16)
            id = R.id.radioParent
        }
        
        childRadioButton = RadioButton(this).apply {
            text = "🧒 Urządzenie DZIECKA\n• Będzie monitorowane\n• Wyśle alerty do rodzica"
            textSize = 16f
            setPadding(16, 16, 16, 16)
            id = R.id.radioChild
        }
        
        radioGroup.addView(parentRadioButton)
        radioGroup.addView(childRadioButton)
        
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedDeviceType = when (checkedId) {
                R.id.radioParent -> DeviceType.PARENT
                R.id.radioChild -> DeviceType.CHILD
                else -> null
            }
            updateNavigationButtons()
        }
        
        deviceTypeLayout.addView(radioGroup)
        stepContainer.addView(deviceTypeLayout)
        
        nextButton.text = "➡️ Dalej"
    }
    
    // ===== STEP 3: CONTACT INFORMATION =====
    private fun showContactInfoStep() {
        stepTitle.text = "📞 Informacje kontaktowe"
        stepDescription.text = "Podaj dane rodzica dla powiadomień i komunikacji"
        
        val contactLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // Parent phone number
        val phoneLabel = TextView(this).apply {
            text = "📱 Numer telefonu rodzica *"
            textSize = 16f
            setPadding(8, 8, 8, 8)
        }
        
        parentPhoneInput = EditText(this).apply {
            hint = "+48 123 456 789"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setPadding(16, 16, 16, 16)
            text.insert(0, preferencesManager.getParentPhone() ?: "")
        }
        
        // Parent email (optional)
        val emailLabel = TextView(this).apply {
            text = "📧 Email rodzica (opcjonalnie)"
            textSize = 16f
            setPadding(8, 16, 8, 8)
        }
        
        parentEmailInput = EditText(this).apply {
            hint = "rodzic@example.com"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setPadding(16, 16, 16, 16)
            text.insert(0, preferencesManager.getParentEmail() ?: "")
        }
        
        val infoNote = TextView(this).apply {
            text = "ℹ️ Te dane będą używane do powiadomień o alertach bezpieczeństwa"
            textSize = 14f
            setPadding(8, 16, 8, 8)
            setTextColor(getColor(android.R.color.darker_gray))
        }
        
        contactLayout.addView(phoneLabel)
        contactLayout.addView(parentPhoneInput)
        contactLayout.addView(emailLabel)
        contactLayout.addView(parentEmailInput)
        contactLayout.addView(infoNote)
        
        stepContainer.addView(contactLayout)
        nextButton.text = "💾 Zapisz i dalej"
    }
    
    // ===== STEP 4: PAIRING PROCESS =====
    private fun showPairingStep() {
        stepTitle.text = "🔗 Parowanie urządzeń"
        
        val pairingLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        if (selectedDeviceType == DeviceType.PARENT) {
            showParentPairingStep(pairingLayout)
        } else {
            showChildPairingStep(pairingLayout)
        }
        
        stepContainer.addView(pairingLayout)
    }
    
    private fun showParentPairingStep(container: LinearLayout) {
        stepDescription.text = "Wprowadź kod parowania z urządzenia dziecka"
        
        val instructionText = TextView(this).apply {
            text = """
                📱 Na urządzeniu dziecka:
                1. Uruchom aplikację KidSecura
                2. Przejdź przez kreatora wybierając "Urządzenie DZIECKA"
                3. Skopiuj 6-cyfrowy kod parowania
                
                🔢 Wprowadź kod poniżej:
            """.trimIndent()
            textSize = 16f
            setPadding(16, 16, 16, 16)
        }
        
        val pairingCodeInput = EditText(this).apply {
            hint = "123456"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(16, 16, 16, 16)
            textSize = 24f
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }
        
        val connectButton = Button(this).apply {
            text = "🔗 Połącz urządzenia"
            textSize = 16f
            setOnClickListener {
                val code = pairingCodeInput.text.toString().trim()
                if (code.length == 6) {
                    startPairingAsParent(code)
                } else {
                    Toast.makeText(this@PairingWizardActivity, "Wprowadź 6-cyfrowy kod", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        container.addView(instructionText)
        container.addView(pairingCodeInput)
        container.addView(connectButton)
        
        nextButton.text = "⏭️ Pomiń na razie"
    }
    
    private fun showChildPairingStep(container: LinearLayout) {
        stepDescription.text = "Wygeneruj kod parowania dla urządzenia rodzica"
        
        // Generate pairing code
        generatedPairingCode = generatePairingCode()
        
        val instructionText = TextView(this).apply {
            text = """
                🔢 Twój kod parowania:
                
                Przepisz ten kod na urządzeniu rodzica
            """.trimIndent()
            textSize = 16f
            setPadding(16, 16, 16, 16)
        }
        
        val codeDisplay = TextView(this).apply {
            text = generatedPairingCode
            textSize = 48f
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundResource(android.R.drawable.edit_text)
            setTextColor(getColor(android.R.color.holo_blue_dark))
        }
        
        val waitingText = TextView(this).apply {
            text = "⏳ Oczekiwanie na połączenie z urządzeniem rodzica..."
            textSize = 14f
            setPadding(16, 16, 16, 16)
            setTextColor(getColor(android.R.color.darker_gray))
        }
        
        container.addView(instructionText)
        container.addView(codeDisplay)
        container.addView(waitingText)
        
        // Start pairing service as child
        startPairingAsChild()
        
        nextButton.text = "✅ Parowanie ukończone"
        nextButton.isEnabled = false // Will be enabled when pairing succeeds
    }
    
    // ===== STEP 5: SUCCESS =====
    private fun showSuccessStep() {
        stepTitle.text = "🎉 Konfiguracja ukończona!"
        stepDescription.text = "KidSecura jest gotowa do użycia"
        
        val successLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        val successMessage = TextView(this).apply {
            text = """
                ✅ Urządzenia zostały pomyślnie sparowane
                ✅ Dane kontaktowe zostały zapisane
                ✅ System monitorowania jest aktywny
                
                🚀 Następne kroki:
                • Skonfiguruj powiadomienia Telegram/WhatsApp
                • Dostosuj słowa kluczowe do wieku dziecka
                • Aktywuj tryb ukryty na urządzeniu dziecka
                • Przetestuj system alertów
                
                📖 Szczegółowe instrukcje znajdziesz w dokumentacji aplikacji.
            """.trimIndent()
            textSize = 16f
            setPadding(16, 16, 16, 16)
        }
        
        val finishButton = Button(this).apply {
            text = "🏁 Przejdź do aplikacji"
            textSize = 16f
            setOnClickListener {
                finishWizard()
            }
        }
        
        successLayout.addView(successMessage)
        successLayout.addView(finishButton)
        stepContainer.addView(successLayout)
        
        nextButton.visibility = Button.GONE
        backButton.visibility = Button.GONE
        skipButton.visibility = Button.GONE
    }
    
    // ===== NAVIGATION HANDLERS =====
    
    private fun handleNextStep() {
        when (currentStep) {
            1 -> {
                // Welcome -> Device Type
                showStep(2)
            }
            2 -> {
                // Device Type -> Contact Info
                if (selectedDeviceType != null) {
                    showStep(3)
                } else {
                    Toast.makeText(this, "Wybierz typ urządzenia", Toast.LENGTH_SHORT).show()
                }
            }
            3 -> {
                // Contact Info -> Pairing
                if (saveContactInfo()) {
                    showStep(4)
                }
            }
            4 -> {
                // Pairing -> Success (only if pairing completed)
                if (preferencesManager.isDevicePaired()) {
                    showStep(5)
                } else {
                    Toast.makeText(this, "Ukończ proces parowania", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun handlePreviousStep() {
        if (currentStep > 1) {
            showStep(currentStep - 1)
        }
    }
    
    private fun handleSkipWizard() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Pominąć kreator?")
            .setMessage("Możesz skonfigurować aplikację później w ustawieniach.")
            .setPositiveButton("Tak, pomiń") { _, _ ->
                finishWizard()
            }
            .setNegativeButton("Kontynuuj kreator", null)
            .show()
    }
    
    private fun updateNavigationButtons() {
        backButton.visibility = if (currentStep > 1) Button.VISIBLE else Button.GONE
        
        // Enable/disable next button based on step requirements
        nextButton.isEnabled = when (currentStep) {
            2 -> selectedDeviceType != null
            3 -> parentPhoneInput.text.toString().trim().isNotEmpty()
            4 -> preferencesManager.isDevicePaired() || !isParingInProgress
            else -> true
        }
    }
    
    // ===== PAIRING LOGIC =====
    
    private fun startPairingAsParent(code: String) {
        isParingInProgress = true
        systemLogger.d(TAG, "🔗 Starting pairing as parent with code: $code")
        
        lifecycleScope.launch {
            try {
                val success = pairingService.connectToChild(code)
                if (success) {
                    systemLogger.d(TAG, "✅ Pairing successful as parent")
                    Toast.makeText(this@PairingWizardActivity, "✅ Połączenie nawiązane!", Toast.LENGTH_SHORT).show()
                    nextButton.isEnabled = true
                    nextButton.text = "🎉 Przejdź do podsumowania"
                } else {
                    systemLogger.w(TAG, "❌ Pairing failed as parent")
                    Toast.makeText(this@PairingWizardActivity, "❌ Nie udało się połączyć. Sprawdź kod.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                systemLogger.e(TAG, "💥 Exception during pairing as parent", e)
                Toast.makeText(this@PairingWizardActivity, "Błąd: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isParingInProgress = false
            }
        }
    }
    
    private fun startPairingAsChild() {
        isParingInProgress = true
        systemLogger.d(TAG, "🔗 Starting pairing as child with code: $generatedPairingCode")
        
        lifecycleScope.launch {
            try {
                val success = pairingService.waitForParentConnection(generatedPairingCode!!)
                if (success) {
                    systemLogger.d(TAG, "✅ Pairing successful as child")
                    Toast.makeText(this@PairingWizardActivity, "✅ Rodzic połączył się pomyślnie!", Toast.LENGTH_SHORT).show()
                    nextButton.isEnabled = true
                } else {
                    systemLogger.w(TAG, "❌ Pairing failed as child")
                    Toast.makeText(this@PairingWizardActivity, "❌ Brak połączenia z rodzicem", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                systemLogger.e(TAG, "💥 Exception during pairing as child", e)
                Toast.makeText(this@PairingWizardActivity, "Błąd: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isParingInProgress = false
            }
        }
    }
    
    private fun generatePairingCode(): String {
        val code = (100000..999999).random().toString()
        preferencesManager.setPairingCode(code)
        systemLogger.d(TAG, "🔢 Generated pairing code: $code")
        return code
    }
    
    // ===== UTILITY METHODS =====
    
    private fun saveContactInfo(): Boolean {
        val phone = parentPhoneInput.text.toString().trim()
        val email = parentEmailInput.text.toString().trim()
        
        if (phone.isEmpty()) {
            Toast.makeText(this, "Numer telefonu jest wymagany", Toast.LENGTH_SHORT).show()
            return false
        }
        
        preferencesManager.setParentPhone(phone)
        if (email.isNotEmpty()) {
            preferencesManager.setParentEmail(email)
        }
        
        systemLogger.d(TAG, "💾 Contact info saved: phone=$phone, email=$email")
        return true
    }
    
    private fun finishWizard() {
        systemLogger.d(TAG, "🏁 Finishing pairing wizard")
        
        // Mark wizard as completed
        preferencesManager.prefs.edit().putBoolean("wizard_completed", true).apply()
        
        // Launch main activity
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
    
    override fun onBackPressed() {
        if (currentStep > 1) {
            handlePreviousStep()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        systemLogger.d(TAG, "🧹 PairingWizardActivity destroyed")
    }
}
