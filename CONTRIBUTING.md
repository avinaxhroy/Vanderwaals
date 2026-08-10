# Contributing to Vanderwaals

Guidelines for submitting bug reports, feature requests, code contributions, and licensing agreements.

## Code of conduct

- Be respectful and constructive.
- Focus on technical feedback and problem solving.
- Help maintain a supportive community.

---

## Licensing and contributor agreement

### Contributor License Agreement (CLA)

By submitting code or documentation to Vanderwaals, you agree to the terms of the [Contributor License Agreement (CLA)](CLA.md).

Key CLA points:
- You retain copyright ownership of your contributions.
- You grant the project a perpetual, worldwide, royalty-free license to use, distribute, and sublicense your contributions under AGPL-3.0 and Commercial licenses.
- You confirm that your contributions are your original creation.

### Dual licensing

Vanderwaals uses a dual licensing model:
1. **AGPL-3.0** for open-source community distribution.
2. **Commercial License** for proprietary applications, enterprise distribution, and commercial monetization.

### Accepting the CLA

When opening your first pull request, include the following statement in your description:

```
I have read and agree to the Contributor License Agreement (CLA) at:
https://github.com/avinaxhroy/Vanderwaals/blob/main/CLA.md

Full Name: [Your Full Name]
GitHub Username: @[your-username]
Email: [your-email]
Date: [YYYY-MM-DD]
```

---

## Workflow guidelines

### Reporting bugs

1. Search existing [GitHub Issues](https://github.com/avinaxhroy/Vanderwaals/issues) to avoid duplicate reports.
2. Open a new issue including:
   - Expected versus actual behavior
   - Android OS version, device model, and app version
   - Reproduction steps and relevant logcat output

### Suggesting features

1. Search existing issues and discussions.
2. Open a new issue detailing:
   - The core problem or feature requirement
   - Proposed design or technical implementation approach

### Submitting pull requests

1. Fork the repository and create a branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```
2. Make targeted changes following Kotlin coding conventions.
3. Verify that the build and tests pass:
   ```bash
   ./gradlew clean test assembleDebug
   ```
4. Push to your fork and submit a pull request referencing any related issue IDs.

---

## Development environment

### Requirements

- Java 17 JDK
- Android Studio Ladybug (2024.2.1) or higher
- Android SDK 36 (Minimum SDK 30, Target SDK 36)
- Git

### Setup steps

1. Clone your fork:
   ```bash
   git clone https://github.com/avinaxhroy/Vanderwaals.git
   cd Vanderwaals
   ```
2. Open `Vanderwaals` in Android Studio and wait for Gradle sync to complete.
3. Run debug build:
   ```bash
   ./gradlew installDebug
   ```

---

## Code style and conventions

- Follow official [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Use sentence case for documentation headings and UI strings.
- Avoid decorative emojis and marketing buzzwords in code comments or documentation.
- Provide KDoc for public API functions and use cases.

### Commit message format

Use standard structured commit subjects:
- `feat: Add GPU delegate support for TFLite inference`
- `fix: Resolve background service crash on Android 15`
- `refactor: Extract similarity calculation into use case`
- `docs: Update API documentation for version 4.6.3`
- `test: Add unit tests for PreferenceUpdater`

---

## Questions

For licensing or contribution questions, open an issue tagged `question` or contact `hi@avinas.me`.
