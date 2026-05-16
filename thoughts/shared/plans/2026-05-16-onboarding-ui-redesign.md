---
date: 2026-05-16
topic: "Onboarding UI Redesign Implementation Plan"
based-on: "Design at thoughts/shared/designs/2026-05-16-onboarding-ui-redesign.md"
status: ready
---

## Implementation Order

Do these steps **sequentially** — each builds on the previous.

---

### Step 1: Add Font Files to `res/font/`

**Files to create:**
- `app/src/main/res/font/playfair_display_italic.ttf`
- `app/src/main/res/font/hanken_grotesk_regular.ttf`
- `app/src/main/res/font/hanken_grotesk_medium.ttf`

**Actions:**
- Download Playfair Display italic TTF from Google Fonts
- Download Hanken Grotesk Regular and Medium TTF from Google Fonts
- Place all three in `app/src/main/res/font/`

**Verification:** `ls app/src/main/res/font/` shows 3 files.

---

### Step 2: Update `Type.kt` — Add Font Families + Luxe Text Styles

**File:** `app/src/main/java/me/avinas/vanderwaals/ui/theme/Type.kt`

**Changes:**
1. Add `R` import for font resources
2. Add `PlayfairDisplayFamily` — `FontFamily(Font(R.font.playfair_display_italic))`
3. Add `HankenGroteskFamily` — `FontFamily(Font(R.font.hanken_grotesk_regular), Font(R.font.hanken_grotesk_medium, FontWeight.Medium))`
4. Add `LuxeHeadlineStyle` — Playfair Display italic, size 32sp, weight Normal, lineHeight 40sp
5. Add `LuxeSubheadlineStyle` — Hanken Grotesk, size 16sp, weight Normal, lineHeight 24sp, letterSpacing 0.3sp
6. Add `LuxeCardTitleStyle` — Hanken Grotesk, size 16sp, weight Medium, lineHeight 22sp
7. Add `LuxeCardBodyStyle` — Hanken Grotesk, size 14sp, weight Normal, lineHeight 20sp, letterSpacing 0.25sp
8. Keep existing `VanderwaalsTypography` unchanged — new styles are standalone vals for targeted use

**Verification:** No build errors. New text style vals are importable and usable.

---

### Step 3: Update `Color.kt` — Add Luxe-Specific Colors

**File:** `app/src/main/java/me/avinas/vanderwaals/ui/theme/Color.kt`

**Add after existing gradient definitions (before Neutral palette):**
1. `WarmBackgroundDark = Color(0xFF1A1A1A)` — warm dark bg
2. `WarmSurfaceDark = Color(0xFF1E1E1E)` — warm elevated surface
3. `FrostedGlassDark = Color.White.copy(alpha = 0.06f)` — card base
4. `FrostedGlassLight = Color.Black.copy(alpha = 0.04f)` — card base light
5. `FrostedGlassBorder = Color.White.copy(alpha = 0.10f)` — subtle border
6. `GradientBorderStart = BrandPrimary.copy(alpha = 0.7f)` + `GradientBorderEnd = BrandAccent.copy(alpha = 0.7f)` for selected card borders
7. `WarmTextPrimary = Color(0xFFF5F5F5)` — warm white text
8. `WarmTextSecondary = Color(0xFFA3A3A3)` — warm gray
9. `GlowWarm = Color(0xFFF59E0B).copy(alpha = 0.15f)` — warm ambient orb
10. `GlowWarmAccent = Color(0xFFEC4899).copy(alpha = 0.10f)` — warm pink accent
11. `GradientLuxeDark` — `Brush.verticalGradient(listOf(Color(0xFF1A1A1A), Color(0xFF141414), Color(0xFF0D0D0D)))`
12. `GradientLuxeLight` — `Brush.verticalGradient(listOf(Color(0xFFFDFBF7), Color(0xFFF8F6F0), Color(0xFFFDFBF7)))`

**Verification:** All new vals compile. No collisions with existing color names.

---

### Step 4: Update `OnboardingRoutes.kt` — Add WELCOME Route

**File:** `app/src/main/java/me/avinas/vanderwaals/ui/onboarding/OnboardingRoutes.kt`

**Changes:**
1. Add `const val WELCOME = "onboarding/welcome"` before `INITIAL_SYNC`
2. Update KDoc comment to show flow: `0. Welcome → Source Selection → ...`

**Verification:** No build errors. `OnboardingRoutes.WELCOME` resolves to `"onboarding/welcome"`.

---

### Step 5: Create `WelcomeScreen.kt` — New Screen Composable

**File:** `app/src/main/java/me/avinas/vanderwaals/ui/onboarding/WelcomeScreen.kt`

**Pattern:** Follow existing screen structure (`Scaffold + Backdrop + Column + BottomBar`), but without a StepIndicator (Welcome is Screen 0, outside step counting).

**Structure:**
```
WelcomeScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit
)
```

**Layout:**
- `Scaffold(containerColor = Color.Transparent)`
- `Box(fillMaxSize)` with:
  - `OnboardingBackdrop` (using new warm luxe colors)
  - `Column(verticalScroll, statusBarsPadding, horizontal padding, centerAligned)`
    - Top: App logo/icon with soft glow (use `BrandPrimary` glow)
    - Spacer
    - Headline: `Text("Your phone.\nYour aesthetic.", LuxeHeadlineStyle)` centered
    - Subheadline: `Text("Wallpapers that match your taste, refreshed automatically.", LuxeSubheadlineStyle)`
    - Spacer
    - 3 Value Cards (frosted glass):
      - Each card: `Box(clip(RoundedCornerShape(16dp)), bg=FrostedGlassDark, border=1px FrostedGlassBorder)`
        - Row: Icon + Column(Title, Description)
        - Icon: `auto_awesome`, `wallpaper`, `security` material icons
        - Title: `LuxeCardTitleStyle`
        - Description: `LuxeCardBodyStyle`
    - Spacer
  - Bottom: "Get Started" gradient button + "Skip" TextButton
- "Skip" TextButton (top right) — `onSkip`

**Navigation:** Both "Get Started" and "Skip" navigate to `WALLPAPER_SOURCE_SELECTION`

**Verification:** Screen renders in light and dark theme. Cards show frosted glass effect. Fonts render correctly.

---

### Step 6: Update `OnboardingNavGraph.kt` — Add WELCOME as Start Destination

**File:** `app/src/main/java/me/avinas/vanderwaals/ui/onboarding/OnboardingNavGraph.kt`

**Changes:**
1. Change `startDestination` from `WALLPAPER_SOURCE_SELECTION` to `WELCOME`
2. Add `composable(OnboardingRoutes.WELCOME)` block before the existing WALLPAPER_SOURCE_SELECTION block:
   ```kotlin
   composable(OnboardingRoutes.WELCOME) {
       WelcomeScreen(
           onGetStarted = {
               navController.navigate(OnboardingRoutes.WALLPAPER_SOURCE_SELECTION) {
                   popUpTo(OnboardingRoutes.WELCOME) { inclusive = true }
               }
           },
           onSkip = {
               navController.navigate(OnboardingRoutes.WALLPAPER_SOURCE_SELECTION) {
                   popUpTo(OnboardingRoutes.WELCOME) { inclusive = true }
               }
           }
       )
   }
   ```
3. Renumber `currentStep` values: old Screen 1 → now step 1, etc. (update all composable calls)
   - Welcome (Screen 0): no step indicator
   - WallpaperSourceSelection: `currentStep = 1, totalSteps = 7`
   - InitialSync: `currentStep = 2, totalSteps = 7`
   - ModeSelection: `currentStep = 3, totalSteps = 7` (or 5 for Auto)
   - UploadWallpaper: `currentStep = 4, totalSteps = 7`
   - ConfirmationGallery: `currentStep = 5, totalSteps = 7`
   - ApplicationSettings: `currentStep = 6, totalSteps = 7` (or 5 for Auto)

**Verification:** Nav flow starts at Welcome. Get Started → Source Selection. Skip → Source Selection. Back navigation from Source Selection exits onboarding (since Welcome is popped).

---

### Step 7: Update Shared Components in `OnboardingUi.kt`

**File:** `app/src/main/java/me/avinas/vanderwaals/ui/onboarding/OnboardingUi.kt`

**OnboardingBackdrop changes:**
- Add a 5th orb: warm amber glow (`GlowWarm`) for luxe aesthetic
- Adjust existing orb colors to be warmer in dark mode:
  - Primary blue orb: reduce alpha slightly, add warm tint
  - Violet orb: keep similar but warmer
  - Orange orb: keep as-is (it's already warm)
  - Pink orb: keep as-is
- In light mode: use warm cream tones instead of cool grays
  - Background: `Color(0xFFFDFBF7)`, `Color(0xFFF8F6F0)`
  - Orbs: softer warm pastels

**OnboardingBottomBar changes:**
- Add optional `glowModifier` parameter — subtle glow behind the CTA button
- No structural changes needed (button already uses gradient)

**OnboardingStepIndicator changes:**
- Update active dot color to use warm accent when in luxe mode
- Keep existing behavior as default, add `luxeColors: Boolean = false` parameter
- When `luxeColors = true`: active = warm accent, completed = warm accent muted, inactive = warm gray

**Verification:** Backdrop shows warm tones. Step indicators look correct in both modes.

---

### Step 8: Redesign `WallpaperSourceSelectionScreen.kt`

**File:** `app/src/main/java/me/avinas/vanderwaals/ui/onboarding/WallpaperSourceSelectionScreen.kt`

**Changes:**
- **Headline:** Replace with `Text("Choose Your Sources", LuxeHeadlineStyle)`
- **Subheadline:** Replace with `Text("Select where Vanderwaals finds your wallpapers", LuxeSubheadlineStyle)`
- **Source cards:** Wrap each card in frosted glass box:
  - `Box(Modifier.clip(RoundedCornerShape(16.dp)).background(FrostedGlassDark).border(1.dp, FrostedGlassBorder, RoundedCornerShape(16.dp)))`
  - Selected state: gradient border via `Modifier.border(1.5.dp, Brush.horizontalGradient(listOf(GradientBorderStart, GradientBorderEnd)), RoundedCornerShape(16.dp))` + subtle glow shadow
- **Card content:** Row with icon + Column(title, description) — keep existing icon/title/description data, just wrap in new styling
- **Bing sub-options (Lite/Full):** Keep existing radio buttons but style with luxe colors
- **Text styling:** Card titles in `LuxeCardTitleStyle`, descriptions in `LuxeCardBodyStyle`
- **Bottom bar:** Use existing `OnboardingBottomBar` (already handles gradients)
- **Step indicator:** Pass `luxeColors = true` to `OnboardingStepIndicator`

**Business logic preserved:** Source selection state, toggle logic, continue-enable when >=1 selected

**Verification:** Cards show frosted glass effect. Selection shows gradient border + glow. All 3 cards toggle correctly.

---

### Step 9: Redesign `InitialSyncScreen.kt`

**File:** `app/src/main/java/me/avinas/vanderwaals/ui/onboarding/InitialSyncScreen.kt`

**Changes:**
- **Headline:** `Text("Syncing Wallpapers", LuxeHeadlineStyle)`
- **Progress indicator area:** Wrap in frosted glass card:
  - `Box(clip(RoundedCornerShape(16.dp)).background(FrostedGlassDark).border(1.dp, FrostedGlassBorder, RoundedCornerShape(16.dp)))`
  - Inside: existing `LinearProgressIndicator` (keep progress logic) with updated colors
  - Status text in `LuxeCardBodyStyle`
- **Shimmer/loading animation:** Keep existing — it already looks premium
- **Completion state:** Keep existing checkmark animation — it's already polished
- **Bottom bar:** Keep existing `OnboardingBottomBar` (gradient button)
- **Step indicator:** `luxeColors = true`

**Business logic preserved:** Sync state machine (Downloading → Processing → Complete), auto-advance, error handling, retry

**Verification:** Progress card shows frosted glass. Status text updates correctly. Auto-advance works.

---

### Step 10: Redesign `ModeSelectionScreen.kt`

**File:** `app/src/main/java/me/avinas/vanderwaals/ui/onboarding/ModeSelectionScreen.kt`

**Changes:**
- **Headline:** `Text("Choose Your Mode", LuxeHeadlineStyle)`
- **Subheadline:** `Text("How Vanderwaals learns your taste", LuxeSubheadlineStyle)`
- **Mode cards:** Frosted glass wrapping:
  - `Box(clip(RoundedCornerShape(16.dp)).background(FrostedGlassDark).border(...))`
  - Default border: 1dp `FrostedGlassBorder`
  - Selected border: gradient border + glow shadow
- **Card content:** Keep existing Auto/Personalize icon + title + description + badge
- **Text styling:** Titles `LuxeCardTitleStyle`, descriptions `LuxeCardBodyStyle`, badge `LuxeCardBodyStyle` with accent color
- **Bottom bar:** Existing `OnboardingBottomBar`
- **Step indicator:** `luxeColors = true`

**Business logic preserved:** `ModeSelectionViewModel`, `selectedMode` state, `onModeSelected` navigation branching

**Verification:** Two cards with frosted glass. Selection shows gradient border + glow. Badge renders. Mode selection triggers correct navigation.

---

### Step 11: Redesign `UploadWallpaperScreen.kt`

**File:** `app/src/main/java/me/avinas/vanderwaals/ui/onboarding/UploadWallpaperScreen.kt`

**Changes:**
- **Headline:** Luxe headline (varies by mode)
- **Subheadline:** Luxe subheadline
- **Upload area (Personalize mode):** Frosted glass drop zone
  - `Box(clip(RoundedCornerShape(16.dp)).background(FrostedGlassDark).border(1.dp, FrostedGlassBorder, RoundedCornerShape(16.dp)))`
  - Keep existing camera icon + "Tap to upload" text with luxe styling
- **Style grid (Personalize mode):** 6 style cards — wrap each in frosted glass
  - Same card pattern: 16dp radius, frosted bg, subtle border
  - Selected: gradient border + glow
- **Auto mode tutorial:** 3 mini-cards in frosted glass
- **Loading overlay:** Keep existing — just update text colors to warm white
- **Bottom bar:** Existing `OnboardingBottomBar`
- **Step indicator:** `luxeColors = true`

**Business logic preserved:** Upload state machine, `UploadWallpaperViewModel`, file picker, embedding extraction, style grid selection

**Verification:** Upload area shows frosted glass. Style grid cards select correctly. Loading overlay looks correct. File picker still works.

---

### Step 12: Redesign `ConfirmationGalleryScreen.kt`

**File:** `app/src/main/java/me/avinas/vanderwaals/ui/onboarding/ConfirmationGalleryScreen.kt`

**Changes:**
- **Headline:** Luxe headline
- **Subheadline:** Luxe subheadline
- **Wallpaper grid items:** Each item wrapped in frosted glass card:
  - `Box(clip(RoundedCornerShape(12.dp)).background(FrostedGlassDark).border(1.dp, FrostedGlassBorder, RoundedCornerShape(12.dp)))`
  - Selected (liked): gradient border + glow
- **Like/dislike icons:** Keep existing but style with warm colors
- **Progress bar:** Keep existing but use `BrandPrimary` color
- **Bottom bar:** Existing `OnboardingBottomBar`
- **Step indicator:** `luxeColors = true`

**Business logic preserved:** `ConfirmationGalleryViewModel`, like/dislike logic, continue-enable after 3+ likes, back navigation data handling

**Verification:** Grid items show frosted glass. Like/dislike works. Progress updates. Continue enables after threshold.

---

### Step 13: Redesign `ApplicationSettingsScreen.kt`

**File:** `app/src/main/java/me/avinas/vanderwaals/ui/onboarding/ApplicationSettingsScreen.kt`

**Changes:**
- **Headline:** `Text("Final Touches", LuxeHeadlineStyle)`
- **Subheadline:** `Text("Set how and when wallpapers change", LuxeSubheadlineStyle)`
- **Settings groups:** Each group wrapped in frosted glass card:
  - "Apply To" card, "Change Frequency" card, "Daily Time" card
  - Standard frosted glass: 16dp radius, frosted bg, subtle border
- **Toggle chips (Apply To):** Keep existing chip UI but style with warm accent colors
- **Frequency options:** Keep existing list, update text to luxe styles
- **Time picker:** Keep existing, update styling
- **Bottom bar:** Existing `OnboardingBottomBar` with "Start Using Vanderwaals"
- **Step indicator:** `luxeColors = true`

**Business logic preserved:** Settings state, time picker, frequency selection, apply-to selection, ViewModel interactions

**Verification:** Settings cards show frosted glass. Toggle chips work. Frequency options selectable. Time picker functional.

---

## Files Summary

### New Files
| File | Purpose |
|------|---------|
| `app/src/main/res/font/playfair_display_italic.ttf` | Headline font |
| `app/src/main/res/font/hanken_grotesk_regular.ttf` | Body font regular |
| `app/src/main/res/font/hanken_grotesk_medium.ttf` | Body font medium weight |
| `app/src/main/java/.../onboarding/WelcomeScreen.kt` | Screen 0 composable |

### Modified Files
| File | Changes |
|------|---------|
| `Type.kt` | +2 font families, +5 luxe text style vals |
| `Color.kt` | +12 luxe color constants |
| `OnboardingRoutes.kt` | +WELCOME route, updated KDoc |
| `OnboardingNavGraph.kt` | +WELCOME composable block, updated startDestination, renumbered steps |
| `OnboardingUi.kt` | Updated Backdrop colors, BottomBar optional glow, StepIndicator luxe mode |
| `WallpaperSourceSelectionScreen.kt` | Luxe text, frosted glass cards, gradient selection borders |
| `InitialSyncScreen.kt` | Luxe text, frosted glass progress card |
| `ModeSelectionScreen.kt` | Luxe text, frosted glass cards, gradient selection |
| `UploadWallpaperScreen.kt` | Luxe text, frosted glass upload zone + style grid |
| `ConfirmationGalleryScreen.kt` | Luxe text, frosted glass grid items |
| `ApplicationSettingsScreen.kt` | Luxe text, frosted glass settings groups |

### Untouched Files
All ViewModels, repositories, domain layer, data layer, build files, theme (structure preserved), and any screen outside onboarding.

## Risk Areas

1. **Font files** — Must be valid TTF files, correct filenames matching R reference
2. **Frosted glass on API 31** — `blur()` modifier requires minSdk 31, which is already the app's minimum
3. **Step renumbering** — `currentStep` values change across all screens in nav graph; must update every `composable()` call
4. **Back navigation** — Welcome is popped from back stack on navigate, so back from Source Selection should exit onboarding
5. **Mode branching** — Auto vs Personalize affects step count and screen visibility; this must remain intact
