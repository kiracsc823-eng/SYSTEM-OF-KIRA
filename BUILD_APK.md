# Hunter System - APK kaise banaye

## Option A: Android Studio (sabse aasan)
1. Android Studio (Koala ya naya) install karo.
2. File > Open > is `HunterSystem` folder ko select karo, Gradle sync hone do.
3. Build > Build Bundle(s)/APK(s) > Build APK(s)
4. APK milega: `app/build/outputs/apk/debug/app-debug.apk`

## Option B: Command line (JDK 17 + Android SDK chahiye)
    ./gradlew assembleDebug      # Windows: gradlew.bat assembleDebug
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Option C: Bina install ke (GitHub Actions)
Folder ko ek GitHub repo me push karo. Actions tab > "Build APK" chalega,
aur APK "Artifacts" me download ho jayega.

Phone me install: APK copy karo, "Install unknown apps" allow karo, kholo.
