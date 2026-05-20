# Font Size Setting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a SeekBarPreference in Terminal View settings to adjust default terminal font size.

**Architecture:** SeekBarPreference in XML → PreferenceDataStore.getInt/putInt → TermuxAppSharedPreferences.getFontSize/setFontSize → SharedPreferences (stored as string). Min/max/default set programmatically in fragment since they depend on device DPI.

**Tech Stack:** AndroidX Preference, SeekBarPreference, SharedPreferences

---

### Task 1: Expose font size range in TermuxAppSharedPreferences

**Files:**
- Modify: `kilux-app-main/termux-shared/src/main/java/com/termux/shared/termux/settings/preferences/TermuxAppSharedPreferences.java:156-166`

- [ ] **Step 1: Add public getters for font size range**

Add three public methods after `setFontVariables()`:

```java
public int getMinFontSize() {
    return MIN_FONTSIZE;
}

public int getMaxFontSize() {
    return MAX_FONTSIZE;
}

public int getDefaultFontSize() {
    return DEFAULT_FONTSIZE;
}
```

- [ ] **Step 2: Commit**

```bash
git add kilux-app-main/termux-shared/src/main/java/com/termux/shared/termux/settings/preferences/TermuxAppSharedPreferences.java
git commit -m "feat: expose font size range getters in TermuxAppSharedPreferences

Kiluahh"
```

---

### Task 2: Add SeekBarPreference to XML and string resources

**Files:**
- Modify: `kilux-app-main/app/src/main/res/xml/termux_terminal_view_preferences.xml`
- Modify: `kilux-app-main/app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add string resource**

In `strings.xml`, add after the existing terminal margin adjustment strings (around line 202):

```xml
<string name="termux_terminal_view_fontsize_title">Font Size</string>
<string name="termux_terminal_view_fontsize_summary">Adjust terminal default font size (px)</string>
```

- [ ] **Step 2: Add SeekBarPreference to XML**

In `termux_terminal_view_preferences.xml`, add inside the existing `PreferenceCategory` after the margin adjustment preference:

```xml
<SeekBarPreference
    app:key="fontsize"
    app:title="@string/termux_terminal_view_fontsize_title"
    app:summary="@string/termux_terminal_view_fontsize_summary"
    app:seekBarIncrement="2"
    app:showSeekBarValue="true" />
```

- [ ] **Step 3: Commit**

```bash
git add kilux-app-main/app/src/main/res/xml/termux_terminal_view_preferences.xml kilux-app-main/app/src/main/res/values/strings.xml
git commit -m "feat: add font size SeekBarPreference to terminal view settings

Kiluahh"
```

---

### Task 3: Wire up DataStore and configure SeekBar programmatically

**Files:**
- Modify: `kilux-app-main/app/src/main/java/com/termux/app/fragments/settings/termux/TerminalViewPreferencesFragment.java`

- [ ] **Step 1: Override putInt/getInt in TerminalViewPreferencesDataStore**

Add these methods to `TerminalViewPreferencesDataStore`:

```java
@Override
public void putInt(String key, int value) {
    if (mPreferences == null) return;
    if (key == null) return;

    switch (key) {
        case "fontsize":
            mPreferences.setFontSize(value);
            break;
        default:
            break;
    }
}

@Override
public int getInt(String key, int defValue) {
    if (mPreferences == null) return defValue;

    switch (key) {
        case "fontsize":
            return mPreferences.getFontSize();
        default:
            return defValue;
    }
}
```

- [ ] **Step 2: Configure SeekBar min/max/default in onCreatePreferences**

In `TerminalViewPreferencesFragment.onCreatePreferences()`, after `setPreferencesFromResource`, add:

```java
import androidx.preference.SeekBarPreference;

// After setPreferencesFromResource(R.xml.termux_terminal_view_preferences, rootKey);
TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, true);
if (prefs != null) {
    SeekBarPreference fontSizePref = findPreference("fontsize");
    if (fontSizePref != null) {
        fontSizePref.setMin(prefs.getMinFontSize());
        fontSizePref.setMax(prefs.getMaxFontSize());
        fontSizePref.setDefaultValue(prefs.getDefaultFontSize());
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add kilux-app-main/app/src/main/java/com/termux/app/fragments/settings/termux/TerminalViewPreferencesFragment.java
git commit -m "feat: wire up font size SeekBar via PreferenceDataStore

Kiluahh"
```
