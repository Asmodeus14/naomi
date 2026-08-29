# Contributing to Naomi

Thank you for your interest in contributing to **Naomi**! We welcome contributions from developers, designers, and testers of all skill levels.

---

## ✦ Getting Started

1. **Fork the repository** on GitHub.
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/<your-username>/naomi.git
   cd naomi
   ```
3. **Open the project in Android Studio** (Ladybug / Iguana or newer recommended).
4. Ensure you have **JDK 17** configured.

---

## ✦ Development Workflow

1. **Create a topic branch**:
   ```bash
   git checkout -b feature/my-new-feature
   ```
2. **Make your changes**:
   - Follow Kotlin and Jetpack Compose best practices.
   - Keep UI aligned with the Naomi design system (typography-first, minimalist dot markers, clean non-boxed surfaces).
   - Maintain local-first guarantees: never add unauthorized network tracking or telemetry.
3. **Run local verification**:
   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew assembleDebug
   ```
4. **Commit your changes**:
   - Write clear, concise commit messages.
   - Example: `feat(intelligence): improve subtopic clustering logic` or `fix(widget): prevent clipping on small screen widths`.
5. **Push and create a Pull Request**:
   - Push your branch to GitHub and submit a PR using the provided pull request template.

---

## ✦ Coding Guidelines

- **Architecture:** Clean Architecture + MVVM + UDF (Unidirectional Data Flow) with Kotlin Coroutines and StateFlow.
- **UI:** Jetpack Compose with Material 3 and custom Stitch tokens (`Color.kt`, `Type.kt`, `NaomiComponents.kt`).
- **Database:** Android Room with SQLite for local persistence.
- **Privacy:** Always default to on-device processing. Never log sensitive memory contents or transcripts in release logs.

---

## ✦ Code of Conduct

Please adhere to our [Code of Conduct](CODE_OF_CONDUCT.md) in all project interactions.
