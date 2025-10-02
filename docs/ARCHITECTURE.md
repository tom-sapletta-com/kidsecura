# KidSecura - Architecture Documentation

## 🏗️ **System Overview**

KidSecura is an advanced parental control application designed for Android devices that provides comprehensive monitoring and protection capabilities for children's digital activities. The application operates in **stealth mode** with **peer-to-peer (P2P) communication** between child and parent devices.

## 📱 **Core Architecture Components**

### **1. MainActivity - Central Control Hub**
- **Purpose**: Main interface for configuration and monitoring
- **Key Features**:
  - Real-time service status monitoring
  - Incident history display with FileLogger integration
  - SystemLogger integration for comprehensive debugging
  - **NEW**: Inline Keywords Editor Dialog (replaces problematic KeywordsEditorActivity)
  - **NEW**: Messaging Integration Configuration (Telegram/WhatsApp setup)
  - P2P device pairing and management
  - Screen capture service control
  - **NEW**: Stealth Mode controls with advanced disguise options

### **2. Screen Monitoring System**
- **ScreenCaptureService**: Continuous screen monitoring and content analysis
- **ContentAnalyzer**: AI-powered threat detection using TensorFlow Lite
- **FileLogger**: Structured logging system with automatic file rotation
- **SystemLogger**: Advanced system-wide logging with crash detection and performance monitoring

### **3. P2P Communication Network**
```
Parent Device ←→ WiFi Direct/Bluetooth ←→ Child Device
     ↓                                      ↓
Configuration Sync                    Real-time Monitoring
Alert Reception                       Incident Reporting  
Remote Control                        Status Updates
```

### **4. Messaging & Alert Distribution System**
- **MessagingIntegrationManager**: Multi-platform messaging integration
  - **Telegram Bot API**: Real-time alerts via Telegram bots
  - **WhatsApp Business API**: Alert distribution via WhatsApp (placeholder)
  - **Priority-based filtering**: Configurable alert threshold (LOW/MEDIUM/HIGH)
  - **Retry queue system**: Automatic retry with exponential backoff
  - **Message formatting**: Rich text with emojis and incident details
- **Alert Channels**: 
  - P2P direct communication (primary)
  - Telegram notifications (secondary)
  - WhatsApp notifications (planned)
  - Email alerts (future enhancement)

### **5. Data Management Layer**
- **PreferencesManager**: Secure settings storage with Gson serialization
  - **NEW**: Messaging configuration persistence (bot tokens, chat IDs, priority settings)
- **IncidentManager**: Event detection, logging, and P2P alert distribution
- **PairedDevicesManager**: Device discovery, pairing, and connection management
- **Database Components**: SQLite integration for persistent data storage

### **6. Stealth & Anti-Detection System**
- **StealthManager**: Advanced application disguise and anti-tampering
  - **Application Aliasing**: Dynamic icon and name changing (Calculator, System Update, etc.)
  - **Recent Apps Hiding**: Automatic removal from task manager
  - **Anti-Tampering Protection**: Detection and response to manipulation attempts
  - **PIN-based Access Control**: Secure access with configurable PINs
  - **Disguise Mode Selection**: Multiple pre-configured camouflage options
- **Stealth Operation Features**:
  - Minimized UI presence and background operation
  - Activity lifecycle management for invisibility
  - Process name obfuscation
  - Icon switching via PackageManager

### **7. Security & Privacy Layer**
- **Encrypted Communications**: Secure P2P data transmission
- **Local Processing**: AI analysis performed on-device (no cloud dependency)
- **Permission Management**: Granular Android permission handling
- **Secure Storage**: Encrypted preferences and sensitive data protection

## 🔧 **Technical Stack**

### **Frontend**
- **Language**: Kotlin
- **UI Framework**: Android ViewBinding with Material Design
- **Architecture Pattern**: MVVM with lifecycle-aware components
- **Theme System**: Custom NoActionBar themes to prevent UI conflicts

### **Backend Services**
- **Messaging Integration**: OkHttp client for Telegram Bot API
- **JSON Processing**: Gson for serialization/deserialization
- **Coroutines**: Kotlin Coroutines for asynchronous messaging operations
- **Background Processing**: Android Services with coroutines
- **AI/ML**: TensorFlow Lite for on-device content analysis
- **Networking**: WiFi Direct + Bluetooth for P2P communication
- **Storage**: SharedPreferences + SQLite + External file storage

### **Key Libraries**
```kotlin
// AI/ML and Core Android
implementation "org.tensorflow:tensorflow-lite:2.14.0"
implementation "androidx.lifecycle:lifecycle-service:2.7.0"
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4"

// Messaging Integration (NEW)
implementation "com.squareup.okhttp3:okhttp:4.11.0"
implementation "com.google.code.gson:gson:2.10.1"

// UI and Material Design
implementation "androidx.appcompat:appcompat:1.6.1"
implementation "com.google.android.material:material:1.9.0"
implementation "androidx.constraintlayout:constraintlayout:2.1.4"
implementation "com.google.code.gson:gson:2.10.1"
implementation "androidx.room:room-runtime:2.5.0"
```

## 📊 **Data Flow Architecture**

### **Monitoring Pipeline**
```
Screen Content → Content Analyzer → Threat Detection → Incident Manager → Alert Distribution
     ↓                                                      ↓               ↓
 SystemLogger                                        P2P Communication  Messaging Integration
     ↓                                                      ↓               ↓
 File Storage                                          Parent Device   Telegram/WhatsApp Bots
                                                            ↓               ↓
                                                      Real-time UI    Mobile Notifications
File Logger → System Logger → Local Storage → Remote Sync
```

### **Configuration Sync**
```
Parent Device → P2P Command → Child Device → Settings Update → Confirmation → Parent Notification
```

## 🛡️ **Security Design**

### **Stealth Operation Modes**
1. **Invisible Mode**: No app icon, runs as system service
2. **Disguised Mode**: Appears as system utility or educational app
3. **Hidden UI**: Admin interface accessible via secret gesture/code

### **Data Protection**
- **Local Encryption**: AES-256 for sensitive data storage
- **P2P Security**: TLS encryption for device communication
- **Access Control**: PIN/biometric protection for parent interface
- **Data Retention**: Automatic cleanup with configurable retention periods

## 🔄 **Service Lifecycle**

### **Application States**
```
App Launch → Permission Check → Service Registration → P2P Discovery → Monitoring Active
     ↓                ↓                ↓                 ↓              ↓
Error Handling → Permission Request → Service Start → Device Pairing → Content Analysis
```

### **Background Operation**
- **Persistent Service**: Survives app closure and device restarts
- **Battery Optimization**: Intelligent scheduling to minimize power consumption
- **Memory Management**: Automatic cleanup and resource optimization
- **Crash Recovery**: SystemLogger integration for automatic error recovery

## 🔍 **Advanced Keywords Testing System**

### **KeywordsTesterActivity Architecture**
**KeywordsTesterActivity** zapewnia kompletny system testowania i zarządzania słowami kluczowymi:

- **Real-time Analysis Engine**: Analiza tekstu w czasie rzeczywistym z debounce (300ms)
- **Multi-level Threat Detection**: Automatyczne określanie poziomu zagrożenia (SAFE/LOW/MEDIUM/HIGH)
- **Interactive Keywords Management**: CRUD operations z KeywordsAdapter i RecyclerView
- **Priority Classification**: Wizualny system priorytetów z kolorową klasyfikacją
- **Intelligent Suggestions**: Kontekstowe rekomendacje działań dla każdego poziomu zagrożenia
- **Export & Persistence**: Integracja z PreferencesManager i możliwość eksportu

### **Technical Implementation**
```kotlin
// Core Components
KeywordsTesterActivity -> KeywordsAdapter -> PreferencesManager
ThreatTestResult -> ThreatLevel.enum -> SuggestionsEngine
LiveAnalysis.coroutines -> DebounceLogic -> UI.updates
```

## 📱 **Multi-Device Communication**

### **Device Roles**
- **Parent Device**: Configuration management, alert reception, remote control
- **Child Device**: Content monitoring, incident detection, status reporting
- **Admin Device**: Full system configuration, analytics, device management

### **Communication Protocols**
1. **Device Discovery**: WiFi Direct service broadcasting
2. **Initial Pairing**: QR code or PIN-based secure handshake
3. **Ongoing Sync**: Encrypted JSON message exchange
4. **Emergency Alerts**: High-priority push notifications

## 🎯 **Scalability & Performance**

### **Optimization Strategies**
- **Lazy Loading**: On-demand resource initialization
- **Caching System**: Intelligent data caching with TTL
- **Background Threading**: Non-blocking UI with coroutine-based async operations
- **Memory Pooling**: Reusable object pools for frequent operations

### **Monitoring & Analytics**
- **Performance Metrics**: Response time, resource usage, error rates
- **Usage Analytics**: Feature adoption, user behavior patterns
- **System Health**: Device performance impact, battery usage statistics
- **Alert Effectiveness**: Threat detection accuracy, false positive rates

## 🚀 **Deployment Architecture**

### **Installation Modes**
1. **Standard Install**: Google Play Store distribution
2. **Sideload Install**: Direct APK installation for enhanced privacy
3. **Enterprise Install**: MDM (Mobile Device Management) integration
4. **Family Install**: Multi-device package with automatic pairing

### **Update Strategy**
- **Automatic Updates**: Background update mechanism with rollback capability
- **Staged Rollout**: Gradual deployment to detect issues early
- **Configuration Sync**: Settings preservation across updates
- **Emergency Patches**: Fast-track security updates

---

## 📈 **Future Architecture Enhancements**

### **Planned Integrations**
- **WhatsApp/Telegram Bots**: Automated alert distribution
- **Cloud Backup**: Optional encrypted cloud storage
- **AI Model Updates**: Over-the-air ML model improvements
- **Cross-Platform**: iOS companion app development

### **Advanced Features**
- **Geo-fencing**: Location-based monitoring rules
- **Time-based Controls**: Scheduled monitoring periods
- **Content Categories**: Granular threat classification
- **Behavioral Analysis**: Long-term pattern detection

This architecture ensures **scalable**, **secure**, and **maintainable** parental control solution with advanced stealth capabilities and comprehensive monitoring features.
