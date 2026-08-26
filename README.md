# AScan (Android)

App Android em **Kotlin** com **WebView** carregando o AScan (HTML).

## Abrir no Android Studio

1. Instale [Android Studio](https://developer.android.com/studio)
2. **File → Open** → pasta `AScanAndroid`
3. Aguarde o Gradle sync
4. Rode em emulador ou celular (USB debug)

## Gerar APK

```bash
./gradlew assembleDebug
```

APK em: `app/build/outputs/apk/debug/app-debug.apk`

## Observações

- Internet liberada (combos GitHub + scan IPTV)
- HTTP cleartext permitido (muitos servidores IPTV usam http://)
- HTML em `app/src/main/assets/index.html`

t.me/ApkBugado
