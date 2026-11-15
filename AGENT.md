# AGENT.md

このリポジトリで作業するエージェント向けの指針です。
UI の見た目は `material-3`、UI 文言は `anti-slop` を規範とします。

## 使用スキル

| スキル | パス | 管理 | 用途 |
|---|---|---|---|
| material-3 | `.opencode/skills/material-3` | git submodule | 見た目・レイアウト・色・書体・形状・コンポーネント選択・タップ領域 |
| anti-slop（UI文言） | `.opencode/skills/anti-slop` | 通常ファイル（vendored なカスタム版） | 可視文字列と contentDescription の文言 |

- `material-3` は https://github.com/hamen/material-3-skill のサブモジュールです。
  スキル本体は `skills/material-3/SKILL.md` と `skills/material-3/references/` にあります。
- `anti-slop` は https://github.com/dmmulroy/anti-slop をベースに、このアプリの
  日本語 UI 文言向けへ書き直した **vendored** スキルです（上流は自動追従しない）。
  `SKILL.md`、`references/`、`scripts/check-copy.sh` が実体で、出典とライセンスは
  `.opencode/skills/anti-slop/NOTICE.md` に記録しています。

### 優先順位

1. 見た目・レイアウト・色・書体・形状・コンポーネント選択・タップ領域 → `material-3`
2. 可視文字列と `contentDescription` の文言 → `anti-slop`
3. 衝突したら `material-3` が勝つ。文言側からレイアウト変更を要求しない。

### 運用ルール

- `anti-slop`: 対象文字列を列挙 → 規範と照合 → Before/After 表を提示 →
  **承認後にのみ編集**。用語集 `references/glossary.md` の「規範表記」が正であり、
  既存文言を正として引用しない。未決・未検証の項目は勝手に決めず、選択肢を出す。
- `material-3`: Compose Material3 と `MaterialTheme` を使う。色はトークン、形状は `Shapes`、
  タップ領域は約 48dp。監査はスキルの audit 手順に従う。
- スキルを更新したら、本ファイルの記述とのずれを直す。

### サブモジュール

```bash
git submodule update --init --recursive   # 初回
git -C .opencode/skills/material-3 pull   # 更新
```

## UI デザイン

### 方向性

- 縦書き EPUB リーダー。読書を邪魔しない、静かな紙面調の UI。
- 実装は Jetpack Compose + Material 3。テーマは
  `app/src/main/java/app/tateyomi/ui/theme/Theme.kt`。
- 画面は単一アクティビティ + Compose（`ui/AppRoot.kt`）。リーダー本文は Canvas 描画。

### 色

- ライト／ダーク／AMOLED の3スキーム（`TateyomiTheme`）。`MaterialTheme.colorScheme` の
  ロール経由で参照し、画面側に `Color(0x…)` を増やさない。
- ブランド色（`Theme.kt`）: `Paper` / `Ink` / `Moss` / `MossLight` / `MossLighter` /
  `Vermilion` / `Hairline` / `Night*`。
- リーダーは紙面パレット（`readerPalette(theme)`）とアプリクローム
  （`materialReaderPalette()`）を分離する。紙面の色はアプリのカラーテーマと独立。
- 書影プレースホルダなど「コンテンツ画像」の色はテーマ非依存（ダークでも紙色）を意図している。

### タイポグラフィ

- UI はゴシック体（`FontFamily.SansSerif`）で統一する。明朝は使わない。
  `Theme.kt` の `AppTypography` も SansSerif。
- 例外は数値の読み出し（スライダー値・目次番号など）の `FontFamily.Monospace`（桁揃えのため）。
- リーダー本文の書体だけがユーザー選択（明朝／ゴシック／追加フォント）。

### 形状・エレベーション・余白

- 角丸は `Shapes`（4 / 8 / 12 / 20 / 28dp）が基準。既存画面には直書きの角丸が残る（移行中）。
- 高さはトーナル（`tonalElevation`）優先。影は書影や本文の上など、背景が煩い箇所だけ。
- edge-to-edge。`WindowInsets.safeDrawing` でインセットを処理する。
- 現状は compact 幅前提。大画面・折りたたみへの適応は未対応（既知の課題）。

### コンポーネント

- Material3 標準: `AlertDialog` / `ModalBottomSheet` / `SegmentedButton` / `FilterChip` /
  `Slider` / `Switch` / `Snackbar` / `FilledTonalButton` など。
- アイコンは Material Icons（Outlined）。装飾アイコンは `contentDescription = null`、
  操作アイコンは動作を書く（例: `設定を開く`）。
- 画面: 本棚（`ui/library`）→ リーダー（`ui/reader`）→ 統計（`ui/stats`）→ 設定（`ui/settings`）。
  リーダーの表示設定は `ui/components/ReaderSettingsContent.kt` を設定画面とシートで共有する。

### 文言

- `anti-slop` に従う。ドメイン語（字数・行数・行間・字下げ・余白・ルビ・見開き・組式）は
  正確さのため維持する。
- 画面タイトルは体言止め、ボタンは名詞形または辞書形、ヘルパーはです・ます。
- エラーは「何が失敗したか」の1文。回復手順を足さない。
- 文言だけを `strings.xml` へ移すリファクタリングは、別タスクとして指示された場合のみ行う。

## ビルドと検証

```bash
nix develop -c gradle assembleDebug        # APK
nix develop -c gradle testDebugUnitTest    # 単体テスト
nix develop -c adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- 文言・レイアウトを変えたら、`anti-slop` の `scripts/check-copy.sh` とビルドを通す。
- 実機確認は adb（ワイヤレスデバッグ）で行う。
