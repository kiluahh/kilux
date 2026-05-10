# File Manager — Design Spec

## Overview

Built-in file manager for Kilux to manage app internal files (`/data/data/com.termux/files/`).
Entry via a folder icon in the left drawer, same row as the Settings button.
Full file management: browse, open, rename, delete, create folders, copy/paste, multi-select, search.

## Sidebar Entry

- Add a 40×40dp `ImageButton` to the right of the existing Settings button in `activity_termux.xml`
- Icon: `ic_folder.xml` VectorDrawable (Material Design "folder" glyph)
- Tint: `?attr/termuxActivityDrawerImageTint` (theme-aware: black in light, white in dark)
- No text label — icon only
- Content description: `@string/action_open_file_manager`
- On click: launch `FileManagerActivity` via Intent

## FileManagerActivity

### Layout (`activity_file_manager.xml`)

```
LinearLayout (vertical)
├── include: partial_primary_toolbar   ← shared toolbar, consistent with Settings
├── LinearLayout (breadcrumb bar)      ← horizontal scrollable path segments
└── RecyclerView (file list)           ← files and folders
```

### Toolbar

- Reuses `@layout/partial_primary_toolbar` from termux-shared
- Background via `?attr/termuxToolbarBackground`
- Navigation icon: back arrow (up button), closes activity
- Title: current directory path (abbreviated)
- Overflow menu: search, new folder, select all

### Breadcrumb Bar

- Horizontal `ScrollView` with clickable path segments
- Each segment is a tappable `TextView` (blue for parent dirs, white for current)
- Segments separated by "/" dividers
- Tapping a parent segment navigates to that directory

### File List

- `RecyclerView` with `FileListAdapter`
- Each row: folder/file icon (24dp VectorDrawable) + name + metadata (size, item count)
- Folders sorted first, then files, both alphabetically (case-insensitive)
- Tap folder → navigate into it
- Tap file → open with external app via `Intent.ACTION_VIEW` + `FileProvider`
- Long-press → enter multi-select mode (contextual action bar with delete/copy/cut)

### Contextual Actions (multi-select mode)

- Toolbar transforms to show: count of selected items, delete, copy, move, share
- Checkboxes appear next to each item
- Operations apply to all selected items

### File Operations

| Operation | Implementation |
|-----------|---------------|
| Navigate into folder | Update adapter data, push path to backstack |
| Navigate up | Pop backstack, reload |
| Open file | `Intent.ACTION_VIEW` with content URI via `FileProvider` |
| Rename | `AlertDialog` with `EditText`, `File.renameTo()` |
| Delete | Confirmation dialog, recursive delete via `File.delete()` |
| New folder | `AlertDialog` with `EditText`, `File.mkdir()` |
| Copy/Paste | In-memory clipboard (list of `File`), paste via recursive copy |
| Multi-select | Long-press enters selection mode |
| Search | `SearchView` in toolbar, filters by filename substring |

### Backstack

- `Stack<String>` tracking directory paths for back navigation
- Back press: pop stack → navigate to parent. If stack empty → finish activity.

## Theme & Styling

**All colors through theme attributes.** No hardcoded colors in layouts or Java.

| Element | Attribute |
|---------|-----------|
| Toolbar background | `?attr/termuxToolbarBackground` |
| File list background | `?attr/termuxActivityDrawerBackground` |
| Text color | `?android:attr/textColorPrimary` |
| Text color secondary | `?android:attr/textColorSecondary` |
| Breadcrumb link color | `?attr/colorAccent` |
| Divider color | `?android:attr/listDivider` |
| Icon tint (drawer) | `?attr/termuxActivityDrawerImageTint` |

Day/night support comes automatically via `values/themes.xml` and `values-night/themes.xml`.

## Icons

All VectorDrawable, 24dp viewport, Material Design glyphs:

| File | Use |
|------|-----|
| `ic_folder.xml` | Drawer entry + folder rows |
| `ic_file.xml` | File rows |
| `ic_folder_open.xml` | Current directory in breadcrumb |
| `ic_delete.xml` | Delete action |
| `ic_edit.xml` | Rename action |
| `ic_add_folder.xml` | New folder action |
| `ic_search.xml` | Search action |
| `ic_copy.xml` | Already exists in termux-shared |
| `ic_back_arrow.xml` | Up navigation (or use system default) |

## AndroidManifest Changes

```xml
<activity
    android:name=".activities.FileManagerActivity"
    android:exported="false"
    android:theme="@style/Theme.TermuxApp.DayNight.NoActionBar"
    android:configChanges="orientation|screenSize|keyboardHidden" />

<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

## Files to Create

```
app/src/main/java/com/termux/app/
├── activities/
│   └── FileManagerActivity.java
└── filemanager/
    ├── FileListAdapter.java
    ├── FileItem.java
    └── FileOperationHelper.java

app/src/main/res/
├── layout/
│   ├── activity_file_manager.xml
│   └── item_file_list.xml
├── drawable/
│   ├── ic_folder.xml
│   ├── ic_file.xml
│   ├── ic_folder_open.xml
│   ├── ic_delete.xml
│   ├── ic_edit.xml
│   ├── ic_add_folder.xml
│   └── ic_search.xml
├── menu/
│   └── file_manager_menu.xml
└── xml/
    └── file_paths.xml
```

## Files to Modify

| File | Change |
|------|--------|
| `app/.../res/layout/activity_termux.xml` | Add `file_manager_button` ImageButton next to `settings_button` |
| `app/.../TermuxActivity.java` | Wire `file_manager_button` onClick → launch `FileManagerActivity` |
| `app/.../AndroidManifest.xml` | Register `FileManagerActivity` + `FileProvider` |
| `app/.../res/values/strings.xml` | Add `action_open_file_manager` string |

## Root Directory

- `TERMUX_FILES_DIR_PATH` = `/data/data/com.termux/files/` (from `TermuxConstants`)
- File manager starts here, can navigate into `home/`, `usr/`, etc.

## Error Handling

- Permission denied: toast message, skip directory
- File not found on open: toast, refresh listing
- Rename conflict (target exists): ask to overwrite
- Delete failure: toast with error
- Copy failure (disk full, permission): toast and abort remaining files

## Testing

- Launch from drawer icon → FileManagerActivity opens
- Navigate into subdirectories, back via toolbar arrow and system back
- Create folder, rename file, delete file
- Multi-select: long-press, select multiple, delete all
- Open text file → external app
- Day/night theme toggle → all colors follow theme
