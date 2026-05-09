# Termux 正式版编译清单

- [ ] 生成密钥：`keytool -genkey -v -keystore release-key.jks -alias myalias -keyalg RSA -keysize 2048 -validity 10000`
- [ ] app/build.gradle → signingConfigs 新增 `release`（密码读环境变量）
- [ ] app/build.gradle → buildTypes.release 加上 `signingConfig signingConfigs.release`
- [ ] app/build.gradle → 修改 `versionCode` 和 `versionName`

**编译：**
```
export RELEASE_KEY_ALIAS=myalias
export RELEASE_STORE_PASSWORD=xxx
export RELEASE_KEY_PASSWORD=xxx
TERMUX_APP_VERSION_NAME="1.0.0" ./gradlew assembleRelease
```

**APK：** `app/build/outputs/apk/release/termux-app_apt-android-7-release_universal.apk`
