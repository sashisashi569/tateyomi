# AGENTS.md

縦読み（Tateyomi）は Android 向けの縦書き EPUB リーダー。単一 Activity + Compose、
本文は Canvas 描画。製品仕様は `README.md`、バージョン規則は `VERSIONING.md`。

## コマンド

devshell 必須。`local.properties` は不要、Gradle wrapper（`./gradlew`）はない。

```bash
nix develop -c gradle assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
nix develop -c gradle testDebugUnitTest
nix develop -c gradle testDebugUnitTest --tests '*BackupTest*'   # 単一テスト
nix develop -c gradle lintDebug
nix develop -c gradle assembleRelease     # 署名なし（R8 有効）
nix develop -c adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- Android SDK / adb は devshell の `$ANDROID_HOME` を使う。`/nix/store/...` の
  SDK パスをハードコードしない。build-tools は 35.0.0。
- devshell に emulator / system image は含まれない。検証は起動済みの adb 端末で行う
  （実機はワイヤレスデバッグ。複数端末時は `adb -s <serial>`）。
- JVM テストは Android フレームワーク非依存。`ReaderLayout` / `EpubParser` は
  Robolectric なしでテストできる。
- EPUB コーパスはリポジトリに含めない。
  `TATEYOMI_EPUB_DIR=~/epub-collection nix develop -c gradle testDebugUnitTest --tests '*EpubCollectionTest*'`
- instrumentation のシードは `connectedDebugAndroidTest` 実行後にアプリを
  アンインストールすることがある。詳細は README の「計測用シード」を参照。

## 構成と境界

- `MainActivity` → `ui/AppRoot.kt`（テーマ・画面遷移・Snackbar）。
  画面状態は `ui/MainViewModel.kt` の `Screen`。
- `data/` は EPUB パース、JSON 永続化、設定・統計・バックアップ。
  `AppRepository` が `filesDir/tateyomi/` の唯一の書き込み窓口。
- `reader/` は Android API に依存しない組版・メトリクス・見開き計算とフォント解決。
  Canvas 描画は `ui/reader/ReaderPage.kt`。
- 表示設定UIは `ui/components/ReaderSettingsContent.kt` をリーダーのシートと
  設定画面で共有する。片方だけ変えない。
- リーダーの紙面は `readerPalette(settings.theme)`、ヘッダー・下部バー・シートは
  `materialReaderPalette()`（アプリテーマ）。紙面カラーをUI側へ流用しない。
  `ui/AppRoot.kt` は `Surface` で `LocalContentColor` を供給する。
- UI の書体は `AppTypography` の SansSerif。数値表示だけ Monospace。
  本文だけがユーザー選択フォント。
- 本文は `字数 × 行数 × 行間` から自動縮小する。`marginDp` は固定pxではなく
  16dp = 1em 基準の比例余白、`lineSpacing` は行ピッチ倍率。固定px前提で計算しない。
- 縦書き記号は `ReaderTypefaces.verticalFormChars()` がフォントの `vert` 対応を
  判定する。対応字体ではフォント字形を優先し、手動回転は非対応時だけ。
- EPUB: `<svg><image>` は画像ページ、本文中の `<img class="gaiji">` は
  `TextBlock.inlineImages` として文字サイズで描画する。表紙スキップは
  リーダーのページオフセット（`skipCover`）で、コンテンツから削除しない。
- `BookContent` のキャッシュ版は `BOOK_CONTENT_VERSION`。パーサー／保存形式を
  変えたら必ず上げる（古い `content.json` は自動再パースされない）。
- バックアップは `BACKUP_VERSION`。プリセットは同名マージ、統計は単調マージ、
  読書進捗は書籍ID→正規化したタイトル・著者で照合し、新しい `updatedAt` を採用。
  単体プリセットの書き出しも同じバックアップJSON形式。

## バージョン / リリース

- `versionName` の唯一の定義は `gradle.properties` の `tateyomi.versionName`。
  `versionCode` は `app/build.gradle.kts` で自動計算。
- 1.0.0 前は `./scripts/bump-version.sh pre`（`0.0.x`）。公式リリース後は
  `release` / `feature` / `fix` / `major`。開発版に Git タグは付けない。
- リリースAPKはローカルの debug keystore でテスト署名し `dist/` に置く。
  `dist/*.apk` は Git 管理外だが、`dist/README.txt` は追跡対象なので
  バージョンとファイル名を更新する。

```bash
BT=$ANDROID_HOME/build-tools/35.0.0
$BT/zipalign -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk /tmp/aligned.apk
$BT/apksigner sign --ks ~/.config/.android/debug.keystore \
  --ks-pass pass:android --key-pass pass:android \
  --out dist/tateyomi-<version>-debugsigned.apk /tmp/aligned.apk
```

- アプリ本体は AGPL-3.0（`LICENSE`）。互換性のないコードを追加しない。

## スキルとUI文言

- 見た目・レイアウト・色・書体・形状・タップ領域は `material-3`
  （`.opencode/skills/material-3/skills/material-3/SKILL.md`、git submodule）。
  初回は `git submodule update --init --recursive`。
- 日本語UI文言と `contentDescription` は `anti-slop`
  （`.opencode/skills/anti-slop/SKILL.md`、このリポジトリ向けの vendored 版）。
  対象文字列を列挙→規範と照合→Before/After を提示→**承認後に編集**。
  既存文言を正として引用しない。`strings.xml` への一括移行は指示があるまでしない。
- 衝突時は見た目側の `material-3` を優先し、文言側からレイアウト変更を要求しない。
  補助チェック: `.opencode/skills/anti-slop/scripts/check-copy.sh [--strict]`
