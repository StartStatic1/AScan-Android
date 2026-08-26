# AScan (Android)

App Kotlin + WebView do **AScan**, com **código de acesso**.

## Código de desbloqueio (padrão)

```
AScan@2026
```

Troque o hash em `app/src/main/java/com/ascan/app/UnlockStore.kt`  
(gere com: `echo -n 'SEU_CODIGO' | sha256sum`).

## Compilar no GitHub Actions

1. Push neste repositório
2. Aba **Actions** → workflow **Build AScan APK**
3. Baixe o artifact **AScan-debug-apk**

Ou use **Actions → Run workflow** (botão manual).

## Android Studio

File → Open → esta pasta → Run.

## Combos online

Repo: https://github.com/StartStatic1/AScan-Combos  

Nomes na tela (sem `.txt`):
- `combo1.txt` → **Basic**
- `combo2.txt` → **Plus**
- `combo3.txt` → **Pro**

Ou use `lista.txt` com `Nome|URL`.

t.me/ApkBugado
