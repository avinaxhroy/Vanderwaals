# F-Droid Submission Files

This directory contains all files necessary for F-Droid submission.

## Directory Structure

```
.fdroid/
├── metadata/
│   ├── me.avinas.vanderwaals.yml ............ Complete F-Droid metadata
│   └── fdroiddata_submission.yml ........... Alternative format for fdroiddata
└── FDROID_ISSUE_TEMPLATE.md ............... GitHub issue template

```

## Files Explanation

### 1. `metadata/me.avinas.vanderwaals.yml`

**Purpose:** Complete F-Droid metadata file with all required information

**Contains:**
- App identification (package ID, categories)
- App name and descriptions
- License and source code information
- Build configuration
- Version information
- Android requirements
- Permissions justification
- Signing key
- Maintainer notes
- Screenshots and icon references
- Release information
- Compliance verification

**Usage:** Reference file for understanding F-Droid requirements

### 2. `metadata/fdroiddata_submission.yml`

**Purpose:** Simplified version for fdroiddata repository submission

**Contains:**
- Essential metadata only
- Build information
- License and source code
- Maintainer notes (concise)
- Signing key

**Usage:** When submitting to https://gitlab.com/fdroid/fdroiddata

### 3. `FDROID_ISSUE_TEMPLATE.md`

**Purpose:** Template for creating F-Droid "Request for Packaging" issue

**Contains:**
- Complete issue body template
- Copy & paste ready version
- Step-by-step submission guide
- What to expect after submission

**Usage:** When submitting issue to https://github.com/f-droid/fdroiddata/issues

## Submission Process

### Step 1: Prepare Repository
```bash
# Ensure all metadata is committed
git add .fdroid/
git commit -m "Add F-Droid submission metadata"
git push origin main

# Create version tag
git tag v2.7.0
git push origin v2.7.0
```

### Step 2: Build & Sign APK
```bash
./gradlew clean assembleRelease
```

### Step 3: Choose Submission Method

#### Method A: GitHub Issue (Easier)
1. Go to: https://github.com/f-droid/fdroiddata/issues
2. Copy content from `FDROID_ISSUE_TEMPLATE.md`
3. Create new issue with template
4. Submit and wait for F-Droid team response

#### Method B: GitLab Merge Request (Direct)
1. Fork: https://gitlab.com/fdroid/fdroiddata
2. Create directory: `metadata/me/avinas/vanderwaals/`
3. Add files:
   - `metadata/me/avinas/vanderwaals/en-US/`
     - `title.txt`
     - `short_description.txt`
     - `full_description.txt`
     - `video.txt` (optional)
   - `metadata/me/avinas/vanderwaals/en-US/images/`
     - `icon.png`
     - `phoneScreenshots/` (with 7 PNGs)
4. Add build metadata file (see below)
5. Create merge request

### Step 4: Build Metadata File for fdroiddata

Create: `metadata/me/avinas/vanderwaals.yml`

```yaml
---
Categories:
  - Personalization
License: AGPL-3.0-or-later
AuthorName: Confused Coconut / Avinas
AuthorEmail: hi@avinas.me
WebSite: https://github.com/avinaxhroy/Vanderwaals
SourceCode: https://github.com/avinaxhroy/Vanderwaals
IssueTracker: https://github.com/avinaxhroy/Vanderwaals/issues
Changelog: https://github.com/avinaxhroy/Vanderwaals/releases

Build:
  - versionCode: 270
    versionName: 2.7.0
    commit: v2.7.0
    gradle: yes

MaintainerNotes: |
  AI-powered wallpaper app with on-device ML.
  Uses TensorFlow Lite locally.
  Zero cloud analytics or tracking.
  Privacy-first architecture.
```

## F-Droid Metadata Requirements Checklist

### ✅ Basic Information
- [x] App name: "Vanderwaals: Learns You"
- [x] Package ID: "me.avinas.vanderwaals"
- [x] Version: 2.7.0 (versionCode: 270)
- [x] License: AGPL-3.0-or-later

### ✅ Descriptions
- [x] Title (short)
- [x] Summary (one-liner)
- [x] Full description (detailed)
- [x] Category: Personalization

### ✅ Links
- [x] Source code: GitHub URL
- [x] Issue tracker: GitHub issues
- [x] Changelog: GitHub releases
- [x] Website: GitHub repository

### ✅ Build Information
- [x] Build configuration (gradle)
- [x] Minimum Android SDK: 31
- [x] Target Android SDK: 36
- [x] Version tags: v2.7.0

### ✅ Images
- [x] App icon: icon.png (140 KB)
- [x] Screenshots: 7 PNG files in phoneScreenshots/

### ✅ Developer
- [x] Name: Confused Coconut / Avinas
- [x] Email: hi@avinas.me
- [x] Contact information provided

### ✅ Security
- [x] License information
- [x] Signing key
- [x] No proprietary dependencies
- [x] No tracking/analytics
- [x] Privacy-first design

## What F-Droid Will Check

1. **License Compliance**
   - ✓ AGPL-3.0-or-later is acceptable
   - ✓ Source code is public
   - ✓ License file in repo

2. **Code Quality**
   - ✓ No proprietary code
   - ✓ No Google Play Services
   - ✓ No Firebase
   - ✓ No tracking SDKs
   - ✓ Open-source dependencies only

3. **Build Verification**
   - ✓ Builds from source
   - ✓ Proper signing
   - ✓ Reproducible builds
   - ✓ Correct Android API levels

4. **Functionality**
   - ✓ App launches
   - ✓ Features work
   - ✓ No crashes
   - ✓ Permissions justified

5. **Metadata**
   - ✓ Complete descriptions
   - ✓ Quality screenshots
   - ✓ App icon provided
   - ✓ Developer info

## Common Questions

**Q: How long does submission take?**
A: Typically 2-4 weeks from submission to app going live.

**Q: What if they ask questions?**
A: Be responsive and helpful. They're usually friendly and accommodating.

**Q: Can I update the app after it's on F-Droid?**
A: Yes. New releases with bumped version codes are auto-detected.

**Q: What if the build fails?**
A: F-Droid will tell you. Fix issues in build.gradle.kts and resubmit.

**Q: Do I need a signing key?**
A: Yes. You should have one. Add fingerprint to metadata.

**Q: Is my app perfect for F-Droid?**
A: Yes! It's open-source, privacy-first, no tracking, no ads, and well-built.

## Next Steps

1. **Commit metadata:**
   ```bash
   git add .fdroid/
   git commit -m "Add F-Droid submission metadata"
   ```

2. **Tag release:**
   ```bash
   git tag v2.7.0
   git push origin main --tags
   ```

3. **Choose submission method:**
   - **Easy:** Use FDROID_ISSUE_TEMPLATE.md on GitHub
   - **Direct:** Submit to fdroiddata on GitLab

4. **Wait for approval:**
   - F-Droid team reviews (1-2 weeks)
   - F-Droid builds (1-2 weeks)
   - App goes live 🎉

## Support

For questions:
- F-Droid Forum: https://forum.f-droid.org
- F-Droid Docs: https://f-droid.org/en/docs/
- GitHub Issues: https://github.com/avinaxhroy/Vanderwaals/issues
- Email: hi@avinas.me

---

**Status:** ✅ Ready for F-Droid submission!

All files are prepared and metadata is complete. Your app is an excellent fit for F-Droid. 🚀
