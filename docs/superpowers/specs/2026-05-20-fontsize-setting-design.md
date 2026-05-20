# Font Size Setting in Preferences

Add a `SeekBarPreference` to Terminal View settings for adjusting default terminal font size.

## Changes

- **`termux_terminal_view_preferences.xml`**: Add `SeekBarPreference` with key=`fontsize`, `seekBarIncrement=2`
- **`TerminalViewPreferencesFragment.java`**: Dynamically set min/max/default from `TermuxAppSharedPreferences`
- **`TerminalViewPreferencesDataStore`**: Override `putInt`/`getInt` for key `fontsize`, delegate to `TermuxAppSharedPreferences`
- **`TermuxAppSharedPreferences.java`**: Expose `getMinFontSize()` / `getMaxFontSize()` / `getDefaultFontSize()`
- **`strings.xml`**: Add title string

## Range

min = 4*dip, max = 256, step = 2, default = 12*dip (rounded to even)
