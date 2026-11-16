# 🤝 Contributing to BitTune

Thank you for your interest in contributing to **BitTune (OfflineMusic)**! Whether you are fixing bugs, improving documentation, designing new themes, or proposing new features, your help is welcome.

---

## 📑 Table of Contents
- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Coding Guidelines](#coding-guidelines)
  - [Kotlin & Jetpack Compose](#kotlin--jetpack-compose)
  - [ExoPlayer & Media3](#exoplayer--media3)
  - [State Management](#state-management)
- [Git Workflow & Branch Conventions](#git-workflow--branch-conventions)
- [Submitting a Pull Request](#submitting-a-pull-request)

---

## Code of Conduct

Please be respectful, constructive, and considerate in all communications, issues, and pull requests.

---

## Getting Started

1. **Fork the repository** on GitHub.
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/<your-username>/OfflineMusic.git
   cd OfflineMusic
   ```
3. Open the project in **Android Studio Hedgehog (2023.1.1)** or newer.

---

## Development Setup

### Android App
- **JDK**: Java 17 or Java 21.
- **Android SDK**: Compile SDK `34`, Min SDK `26`.
- Verify build and run unit tests:
  ```bash
  # Windows
  .\gradlew.bat compileDebugKotlin testDebugUnitTest
  
  # macOS / Linux
  ./gradlew compileDebugKotlin testDebugUnitTest
  ```

### Optional: Lucida NPM REST Bridge (`lucida-server`)
If you want to test the full Lucida multi-streamer bridge locally:
1. Ensure **Node.js 20+** is installed:
   ```bash
   node --version
   ```
2. Navigate to `lucida-server`:
   ```bash
   cd lucida-server
   npm install
   npm start
   ```
   The bridge will run on `http://localhost:3000` (accessible via `http://10.0.2.2:3000` in the Android Emulator).

---

## Coding Guidelines

### Kotlin & Jetpack Compose
- Use **declarative, idiomatic Kotlin**: prefer `val` over `var`, immutable collections, and expression bodies.
- Follow **Material 3 guidelines**: use `MaterialTheme.colorScheme` and `MaterialTheme.typography` rather than hardcoded colors or text styles.
- Mark composables that do not modify state with `@Stable` or pass lambdas/lambdas with receiver.
- Provide sensible default parameters for modifiers (`modifier: Modifier = Modifier`).

### ExoPlayer & Media3
- Always release ExoPlayer instances in `onDestroy()` or background service termination.
- Perform heavy file operations and audio stream buffering strictly on `Dispatchers.IO`.

### State Management
- Follow **Unidirectional Data Flow (UDF)**:
  - Events go **up** from Composables to `MainViewModel`.
  - State flows **down** from `MainViewModel` to Composables via `StateFlow<T>`.

---

## Git Workflow & Branch Conventions

- Create descriptive feature branches from `main`:
  ```bash
  git checkout -b feature/your-feature-name
  # or
  git checkout -b fix/issue-description
  ```
- Write concise, conventional commit messages:
  - `feat: add equalizer preset import/export`
  - `fix: prevent duplicate prompt on track retry`
  - `docs: update download engine specs`

---

## Submitting a Pull Request

1. Run all tests and verify APK packaging:
   ```bash
   .\gradlew.bat testDebugUnitTest packageDebug
   ```
2. Push your branch to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```
3. Open a Pull Request against `main`.
4. Provide a clear description of changes, screenshots/GIFs for UI changes, and linked issues.
