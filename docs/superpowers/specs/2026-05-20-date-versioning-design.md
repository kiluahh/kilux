# Date-Based Versioning for Kilux

## Summary

Replace semver-based versioning (`0.120.3`) with date-based versioning (`2026.05.20.1`) operated by Claude Code via `version.sh`.

## Version Format

| Field | Format | Example |
|-------|--------|---------|
| versionName | `YYYY.MM.DD.N` | `2026.05.20.1` |
| versionCode | `YYYYMMDDN` (integer) | `202605201` |

Same-day builds increment the sequence number N (1, 2, 3...).

## Workflow

```
./version.sh [-m "summary"]
  ├─ Scan changelogs/ for today's existing versions → determine N
  ├─ Update app/build.gradle: versionCode + versionName
  ├─ Create changelogs/YYYY.MM.DD.N.md template
  └─ Print new version info
```

## Files

| File | Action |
|------|--------|
| `version.sh` | New — version script at repo root |
| `changelogs/` | New — one .md per version |
| `app/build.gradle` | Modify — replace semver validation with date format |
| `.github/workflows/debug_build.yml` | Modify — update version regex and validation |
| `.github/workflows/attach_debug_apks_to_release.yml` | Modify — update semver validation |
| `CLAUDE.md` | Update — document new version mechanism |
