# Contributing to Vanderwaals

Thank you for your interest in contributing to Vanderwaals! We welcome contributions from the community to help make this app better. Together, we can build something amazing while maintaining transparency and trust.

This document provides guidelines and instructions for contributing.

## Code of Conduct

- Be respectful and inclusive
- Welcome newcomers and encourage diverse perspectives
- Focus on constructive criticism
- Help create a positive community

## ⚖️ Licensing and Contributor Agreement

### Important: Please Read Before Contributing

By contributing to Vanderwaals, you agree to our **Contributor License Agreement (CLA)**. This ensures:

- **You retain ownership** of your contributions
- **The project can use** your contributions under both AGPL-3.0 and commercial licenses
- **Legal protection** for both you and the project
- **Clear rights** for everyone involved

#### Dual Licensing

Vanderwaals uses a **dual licensing model**:

1. **AGPL-3.0** for open-source community use (transparency and collaboration)
2. **Commercial License** for proprietary/commercial use (sustainability)

Your contributions will be available under both licenses. This allows:
- The community to benefit from open-source improvements
- Commercial users to support the project's development
- The project to remain sustainable long-term

For more details, see:
- [Contributor License Agreement (CLA.md)](CLA.md)
- [Commercial License Terms (COMMERCIAL_LICENSE.md)](COMMERCIAL_LICENSE.md)
- [Trademark Policy (TRADEMARK.md)](TRADEMARK.md)

### How to Accept the CLA

**First-time contributors**: When submitting your first pull request, please add a comment with:

```
I have read and agree to the Contributor License Agreement (CLA) at:
https://github.com/avinaxhroy/Vanderwaals/blob/main/CLA.md

Full Name: [Your Full Name]
GitHub Username: @[your-username]
Email: [your-email@example.com]
Date: [YYYY-MM-DD]
```

**Returning contributors**: You only need to accept once (unless the CLA is substantially updated).

**Corporate contributors**: If contributing on behalf of your employer, please have an authorized representative sign our Corporate CLA by opening an issue tagged `corporate-cla`.

## How to Contribute

### Reporting Bugs

1. Check if the bug has already been reported in [Issues](https://github.com/avinaxhroy/Vanderwaals/issues)
2. If not, create a new issue with:
   - Clear, descriptive title
   - Steps to reproduce
   - Expected vs actual behavior
   - Device info (Android version, device model)
   - Screenshots or logs if applicable

### Suggesting Features

1. Check [Issues](https://github.com/avinaxhroy/Vanderwaals/issues) for existing feature requests
2. Create a new issue with:
   - Clear description of the feature
   - Use case and benefits
   - Possible implementation approach (optional)

### Pull Requests

1. **Fork the repository** and create a new branch from `master`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**:
   - Follow the existing code style (Kotlin conventions)
   - Write clear, descriptive commit messages
   - Add comments for complex logic
   - Test your changes thoroughly

3. **Ensure your code builds**:
   ```bash
   ./gradlew clean build
   ```

4. **Run tests** (if applicable):
   ```bash
   ./gradlew test
   ```

5. **Push to your fork** and create a Pull Request:
   - Reference any related issues
   - Describe what changes you made and why
   - Include screenshots for UI changes

## Development Setup

### Prerequisites

- **Java 17** or later
- **Android Studio Ladybug | 2024.2.1** or later
- **Android SDK 31+** (minimum SDK 31)
- **Git**

### Setup Steps

1. Clone your fork:
   ```bash
   git clone https://github.com/avinaxhroy/Vanderwaals.git
   cd Vanderwaals/Vanderwaals
   ```

2. Open in Android Studio:
   - File → Open → Select the `Vanderwaals` folder
   - Wait for Gradle sync to complete

3. Build the project:
   - Build → Make Project (⌘F9 / Ctrl+F9)

4. Run on device/emulator:
   - Run → Run 'app' (⇧F10 / Shift+F10)

## Project Structure

```
Vanderwaals/
├── app/
│   ├── src/main/
│   │   ├── java/me/avinas/vanderwaals/
│   │   │   ├── algorithm/      # ML algorithms
│   │   │   ├── data/           # Database, DAOs, entities
│   │   │   ├── domain/         # Use cases
│   │   │   ├── network/        # API services
│   │   │   ├── ui/             # Compose screens
│   │   │   └── worker/         # Background workers
│   │   ├── res/                # Resources
│   │   └── assets/             # TFLite models
│   └── build.gradle.kts
├── docs/                        # Documentation
├── scripts/                     # Build scripts
└── README.md
```

## Areas for Contribution

### 🤖 Algorithm Improvements
- Better feature extraction (CLIP embeddings, vision transformers)
- Adaptive learning rates based on feedback patterns
- Multi-modal learning (text + image)
- Category weighting optimization

### 📚 Data Sources
- Integration with Unsplash API
- Reddit wallpaper scraping
- Wallhaven API support
- User-submitted collections

### ⚡ Performance
- TFLite GPU delegation
- Database query optimization
- Faster similarity calculations
- Image loading improvements

### 🎨 UI/UX
- Material You dynamic theming
- Smooth animations and transitions
- Accessibility improvements (TalkBack, screen readers)
- Tablet and landscape support
- Widget support

### 🧪 Testing
- Unit tests for algorithms
- UI tests with Compose Test
- Integration tests
- Performance benchmarks

### 📖 Documentation
- Code documentation and KDoc
- Architecture decision records
- User guides and tutorials
- API documentation

## Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Keep functions small and focused
- Add KDoc comments for public APIs
- Use Jetpack Compose best practices

### Example

```kotlin
/**
 * Calculates cosine similarity between two embedding vectors.
 *
 * @param embedding1 First embedding vector (normalized)
 * @param embedding2 Second embedding vector (normalized)
 * @return Similarity score in range [0, 1]
 */
fun calculateCosineSimilarity(
    embedding1: FloatArray,
    embedding2: FloatArray
): Float {
    require(embedding1.size == embedding2.size) {
        "Embeddings must have same dimension"
    }
    
    return embedding1.zip(embedding2)
        .sumOf { (a, b) -> (a * b).toDouble() }
        .toFloat()
        .coerceIn(0f, 1f)
}
```

## Commit Message Guidelines

Use clear, descriptive commit messages:

- `feat: Add GPU delegation for TFLite inference`
- `fix: Resolve crash on Android 12 devices`
- `refactor: Extract similarity calculation into use case`
- `docs: Update README with new features`
- `style: Format code with ktlint`
- `test: Add unit tests for PreferenceUpdater`
- `perf: Optimize database queries with indexes`

## Testing Your Changes

Before submitting a PR:

1. ✅ Build succeeds without errors
2. ✅ App runs on physical device or emulator
3. ✅ No new crashes or ANRs
4. ✅ UI looks correct on different screen sizes
5. ✅ Existing features still work
6. ✅ Performance is acceptable

## License

By contributing, you agree that your contributions will be licensed under:

- **AGPL-3.0** (GNU Affero General Public License v3.0) for open-source use
- Available for **commercial licensing** as per the dual licensing model

See the [CLA.md](CLA.md) for complete details on the rights you grant and retain.

## Questions About Contributing?

- **General questions**: Open an issue with the tag `question`
- **CLA questions**: Open an issue with the tag `cla-question`
- **Trademark questions**: Open an issue with the tag `trademark-question`
- **Commercial licensing**: Open an issue with the tag `commercial-license`

We appreciate your contributions and look forward to building Vanderwaals together! 🚀

