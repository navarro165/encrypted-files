# Contributing to Encrypted Files

Thank you for considering contributing to Encrypted Files! This document provides guidelines for
contributing to this secure file encryption app.

## Code of Conduct

By participating in this project, you agree to abide by our Code of Conduct:

- **Be Respectful**: Treat everyone with respect
- **Be Collaborative**: Work together to resolve conflicts
- **Be Professional**: Keep discussions focused and constructive
- **Be Secure**: Always prioritize security in your contributions

## Getting Started

1. **Fork the Repository**: Click the "Fork" button on GitHub
2. **Clone Your Fork**:
   ```bash
   git clone https://github.com/yourusername/encrypted-files.git
   cd encrypted-files
   ```
3. **Add Upstream Remote**:
   ```bash
   git remote add upstream https://github.com/navarro165/encrypted-files.git
   ```

## How to Contribute

### Reporting Issues

**Security Vulnerabilities**: Please see [SECURITY.md](SECURITY.md) for reporting security issues.

For non-security bugs:

1. Check if the bug has already been reported
2. Create a new issue with:
    - Clear bug description
    - Steps to reproduce
    - Expected vs actual behavior
    - Device info (Android version, device model)

### Suggesting Features

1. Check existing issues for similar suggestions
2. Create a feature request with:
    - Clear description of the feature
    - Use cases and benefits
    - Security implications

### Code Contributions

1. **Find an Issue**: Look for issues labeled `good first issue` or `help wanted`
2. **Comment**: Let others know you're working on it
3. **Branch**: Create a feature branch from `main`
4. **Code**: Implement your changes
5. **Test**: Add/update tests
6. **Document**: Update documentation
7. **PR**: Submit a pull request

## Development Setup

### Prerequisites

- Android Studio Arctic Fox or newer
- JDK 11 or higher
- Android SDK 34
- Physical device with biometric sensor (recommended)

### Setup Steps

1. **Open in Android Studio**:
   ```
   File -> Open -> Select project directory
   ```

2. **Sync Project**: Let Gradle sync dependencies

3. **Build Configuration**:
   ```bash
   # Debug build (recommended for development)
   ./gradlew assembleDebug
   
   # Staging build (for testing)
   ./gradlew assembleStaging
   
   # Release build (requires keystore)
   ./gradlew assembleRelease
   ```

4. **Run Tests**:
   ```bash
   # All tests
   ./gradlew test
   
   # Specific test suites
   ./gradlew testDebugUnitTest
   ./gradlew test --tests "*Security*"
   ```

## Coding Standards

### Kotlin Style Guide

We follow
the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html):

```kotlin
// File header
package com.example.myapplication

import statements // (alphabetically ordered)

/**
 * Class description
 */
class ClassName {
    // Constants first
    companion object {
        private const val CONSTANT_NAME = "value"
    }
    
    // Properties
    private val property: Type
    
    // Public functions
    fun publicFunction() {
        // Implementation
    }
    
    // Private functions
    private fun privateFunction() {
        // Implementation
    }
}
```

### Best Practices

- **Meaningful Names**: Use descriptive variable and function names
- **Small Functions**: Keep functions focused and under 50 lines
- **Error Handling**: Always handle exceptions appropriately
- **Comments**: Comment complex logic, not obvious code
- **Immutability**: Prefer `val` over `var`
- **Null Safety**: Leverage Kotlin's null safety features

## Security Guidelines

### Critical Security Rules

1. **Never Log Sensitive Data**:
   ```kotlin
   // BAD
   Log.d(TAG, "Password: $password")
   
   // GOOD
   Log.d(TAG, "Authentication attempt")
   ```

2. **Always Use Secure Storage**:
   ```kotlin
   // Use EncryptedSharedPreferences for sensitive data
   // Never store encryption keys in plain SharedPreferences
   ```

3. **Validate All Input**:
   ```kotlin
   fun processFileName(name: String): String {
       return name.replace(Regex("[^a-zA-Z0-9._-]"), "")
   }
   ```

4. **Use Secure Random**:
   ```kotlin
   val secureRandom = SecureRandom()
   val iv = ByteArray(12)
   secureRandom.nextBytes(iv)
   ```

## Testing Requirements

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "*.SecurityTest"

# Run with coverage
./gradlew testDebugUnitTestCoverage
```

### Test Coverage Requirements

- All new code must have unit tests
- Security-related code must have comprehensive tests
- Integration tests for file operations
- UI tests for critical user flows

### Security Testing Standards

Following our comprehensive security audit, all contributions must meet these security testing
standards:

```bash
# Run security-specific test suites
./gradlew testDebugUnitTest --tests "*Security*"
./gradlew testDebugUnitTest --tests "*SecurityFixes*"
./gradlew testDebugUnitTest --tests "*MainActivitySecurity*"

# Validate filename sanitization
./gradlew testDebugUnitTest --tests "*testFilenameSanitization*"

# Test cryptographic implementations
./gradlew testDebugUnitTest --tests "*Cryptography*"
```

**Required for Security-Related Changes:**

- Filename sanitization tests for any file handling code
- Input validation tests for all user inputs
- Memory security tests for cryptographic operations
- ProGuard obfuscation verification tests
- Permission and manifest security tests

## Pull Request Process

### Before Submitting

1. **Update from Upstream**:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run Tests**: Ensure all tests pass

3. **Check Lint**: Fix any lint warnings
   ```bash
   ./gradlew lint
   ```

4. **Update Documentation**: If needed

### PR Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Security improvement
- [ ] Documentation update

## Testing
- [ ] Unit tests pass
- [ ] Manual testing completed
- [ ] Security tests updated

## Security Impact
- [ ] No security impact
- [ ] Security improvement
- [ ] Requires security review

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Comments added for complex code
- [ ] Documentation updated
- [ ] No sensitive data exposed
```

### Review Process

1. **Automated Checks**: CI/CD runs tests and security scans
2. **Code Review**: At least one maintainer review required
3. **Security Review**: Required for security-related changes
4. **Testing**: Reviewer tests changes locally
5. **Merge**: Squash and merge to main

## Release Process

### Version Numbering

We use Semantic Versioning (MAJOR.MINOR.PATCH):

- **MAJOR**: Breaking changes
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes

### Release Checklist

1. **Update Version**: In `app/build.gradle.kts`
2. **Update Changelog**: Document all changes
3. **Security Audit**: Run security tests
4. **Build Variants**: Test debug, staging, and release builds
5. **CI/CD Pipeline**: Ensure GitHub Actions pass
6. **Release Notes**: Prepare comprehensive notes
7. **Automated Release**: GitHub Actions creates release with signed APK

### Build Commands

```bash
# Test all build variants
./gradlew assembleDebug
./gradlew assembleStaging  
./gradlew assembleRelease

# Run security tests
./gradlew test --tests "*Security*"

# Generate release APK
./gradlew assembleRelease
```

## Getting Help

- **Documentation**: Check README.md and wiki
- **Issues**: Search existing issues
- **Discussions**: Use GitHub Discussions
- **Security**: See SECURITY.md

## Recognition

Contributors are recognized in:

- Release notes
- README.md contributors section
- GitHub contributors page

Thank you for helping make Encrypted Files more secure! 🔒 