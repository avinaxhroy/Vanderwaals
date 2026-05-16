---
date: 2026-05-16
topic: "Onboarding UI Redesign"
status: validated
---

## Problem Statement

The current onboarding screens are functional but visually flat. The app needs a premium aesthetic that matches its ML-powered wallpaper recommendation feature. Users need context about what the app does before diving into configuration. No business logic changes are needed — this is a pure UI redesign.

## Design Spec

Full spec at `docs/superpowers/specs/2026-05-16-onboarding-ui-redesign.md`.

## Approach

**UI-only redesign**, modifying existing screen composables in place. Add a Welcome screen (Screen 0) before the existing flow. Apply luxe design system (Playfair Display headlines, Hanken Grotesk body, frosted glass cards, warm ambient gradients) to all 7 screens. Keep all ViewModels, business logic, and navigation branching intact.

## Architecture

The architecture is additive to the existing onboarding structure:
- **New files**: `WelcomeScreen.kt`, font files in `res/font/`
- **Modified files**: `OnboardingRoutes.kt`, `OnboardingNavGraph.kt`, `OnboardingUi.kt`, `Type.kt`, `Color.kt`, all 6 existing screen composables
- **Untouched**: All ViewModels, repositories, domain logic, data layer

## Components

| Component | Responsibility |
|-----------|---------------|
| `PlayfairDisplayFamily` / `HankenGroteskFamily` | Font families in `Type.kt` |
| Luxe color constants | Warm dark bg, frosted glass card colors, ambient gradient colors |
| `WelcomeScreen.kt` | Screen 0 — informational splash with value cards |
| Updated `OnboardingBackdrop` | Warm ambient gradient colors with animated orbs |
| Updated `OnboardingBottomBar` | Gradient border styling, glow effects |
| Updated `OnboardingStepIndicator` | Luxe-themed active/inactive dot colors |
| Frosted glass card composable | Reusable card with `Color.White.copy(alpha = 0.06f)` bg, gradient borders |
| 6 existing screen composables | Wrapped with luxe text styles and frosted glass card patterns |

## Data Flow

- Welcome (Screen 0) is informational — no data changes
- All existing screens retain their current ViewModel interactions
- Step indicator numbering shifts: Screen 1 (ex-0) is now step 1 of 7
- Nav graph start destination changes to `WELCOME`, with `showWelcome` gate to skip for returning users

## Error Handling

- Font loading failures: gracefully fall back to system sans-serif
- Frosted glass `blur()` modifier is API 31+ (already the app's minSdk)
- No new error states introduced — all existing error handling stays

## Testing Strategy

- Visual verification on both light/dark themes
- Verify font rendering on device (Playfair Display italic + Hanken Grotesk)
- Verify frosted glass effect renders correctly (no rendering artifacts)
- Verify all 6 existing screens retain their business logic
- Verify navigation flow with Welcome → Source Selection → ... → Complete
- Verify skip functionality bypasses Welcome
