# Security Policy

## Security Architecture

This app uses advanced security measures:

### Encryption
- **AES-256-GCM** encryption with hardware-backed keys
- **Android Keystore** integration for secure key storage
- **PBKDF2-SHA256** PIN hashing with 500,000 iterations for high security
- **Cryptographically secure** random number generation

### Authentication
- **Two-Factor Authentication**: Biometric (BIOMETRIC_STRONG) + 4-digit PIN required
- **No password fallback**: Only strong biometric authentication accepted
- **Session management** with 5-minute timeout for convenience
- **Dual rate limiting**: 
  - Biometric: 30-minute lockout after 5 failed attempts
  - PIN: 1-hour lockout after 3 failed attempts
- **Hardware-backed** security with Android Keystore integration
- **Authentication invalidation** on biometric enrollment changes
- **PIN security**: PBKDF2-SHA256 hashing with 500,000 iterations for high security

### Storage
- **App sandbox** isolation with no external storage access
- **EncryptedSharedPreferences** for sensitive data
- **Secure deletion** with multi-pass overwrite
- **Backup protection** disabled for encrypted files

## Recent Security Enhancements (v1.0)

### Critical Fixes Applied
- **Removed dangerous permissions**: Eliminated OEM_UNLOCK_STATE permission 
- **Enhanced ProGuard obfuscation**: Full obfuscation of security-critical classes
- **Filename sanitization**: Comprehensive path traversal prevention and input validation
- **Stable dependencies**: Updated to production-ready library versions
- **Debug logging removal**: Eliminated all debug information leakage
- **Input validation**: Advanced filename sanitization against malicious inputs

### New Security Features
- **Advanced filename protection**: Prevents path traversal, reserved names, and malicious characters
- **Enhanced memory security**: Encrypted memory buffers with 5-pass secure wiping
- **Improved code obfuscation**: Full SecurityManager obfuscation in release builds
- **Comprehensive testing**: Extensive security-focused unit and instrumentation tests
- **RASP monitoring**: Real-time threat detection every 10 seconds with emergency response
- **PBKDF2-SHA256 hashing**: High-iteration PIN protection with 500k iterations

## Security Features

### Threat Protection
- ✅ Physical device access (two-factor auth required)
- ✅ Advanced biometric spoofing (PIN required as second factor)
- ✅ App data extraction (encrypted storage + secure memory)
- ✅ Memory dumps (encrypted memory buffers + secure wiping)
- ✅ File system analysis (encrypted files + secure deletion)
- ✅ Backup/restore attacks (backup disabled + encryption)
- ✅ USB debugging (anti-debugging protection)
- ✅ Runtime instrumentation (RASP monitoring)
- ✅ Code injection (real-time detection)
- ✅ Screenshot/recording attacks (FLAG_SECURE protection)

### Security Limitations
- ❌ User-authorized access (legitimate user with both biometric + PIN can access files)
- ❌ Hardware-level biometric spoofing (specialized equipment + PIN knowledge)
- ❌ Rooted devices with runtime memory patches (detected but not prevented)
- ❌ User sharing decrypted files outside the app (user choice)
- ❌ Physical device theft with unlocked screen + PIN knowledge
- ❌ Malware with root privileges (emergency wipe triggered but may be too late)
- ❌ Advanced memory attacks (cold boot, hardware extraction)
- ❌ Supply chain attacks (compromised device firmware)

## Compliance

- **NIST Guidelines**: Compliant with NIST cryptographic standards
- **Android Security**: Follows Google Android security best practices
- **OWASP Mobile**: Addresses OWASP Mobile Top 10 security risks


## Security Resources

- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)
- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)
- [NIST Cryptographic Standards](https://csrc.nist.gov/publications/detail/sp/800-38d/final) 