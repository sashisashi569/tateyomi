# 縦読み — Tateyomi

縦書きの組式で読むための Android 向けリフロー EPUB リーダーです。
WebView や既存の EPUB ビューアを使わず、Compose Canvas 上に日本語縦組の
レンダラーを実装しています。

| 本棚 | 縦書き（明朝・生成り） |
|---|---|
| ![本棚](docs/screenshots/library.png) | ![縦書き](docs/screenshots/reader-vertical.png) |

| 横書き（24字・行間1.7） | 縦書き（夜・明朝） |
|---|---|
| ![横書き](docs/screenshots/reader-horizontal.png) | ![夜](docs/screenshots/reader-dark.png) |

| 目次（節アンカー対応） | リーダーの表示設定 |
|---|---|
| ![目次](docs/screenshots/toc.png) | ![リーダーの表示設定](docs/screenshots/settings.png) |

| 設定 | 設定画面の表示設定 |
|---|---|
| ![設定](docs/screenshots/settings-home.png) | ![表示設定](docs/screenshots/display-settings.png) |

| 表示設定（紙面・プリセット） | フォントの選択 |
|---|---|
| ![紙面とプリセット](docs/screenshots/display-settings-presets.png) | ![フォントの選択](docs/screenshots/font-picker.png) |

| フォントマネージャー | アプリ情報 |
|---|---|
| ![フォントマネージャー](docs/screenshots/font-manager.png) | ![アプリ情報](docs/screenshots/app-info.png) |

![読書統計](docs/screenshots/stats.png)

## 主な機能

- **リフロー EPUB 2 / EPUB 3 の取り込み**（SAF。複数選択可）
  - OPF / spine / EPUB3 nav / EPUB2 NCX / 表紙 / 画像 / ルビ / 見出し / 強調
  - 固定レイアウトの画像だけの spine にも対応（1ページ画像として表示）
- **縦書き・横書きの組式レンダリング**
  - 字数（1行／1列の文字数）、行数（1ページの行数／列数）、行間、字下げ、余白、フォント
  - 文字サイズは字数・行数・行間から自動計算し、端末の表示領域に収まらない場合は自動縮小
  - 行間は文字サイズに対する行ピッチの倍率として維持
  - 縦書きのルビ、句読点の縦組字形（OpenType `vert`）、欧文の90度回転
  - 省略記号 `……`・ダッシュ・長音・波ダッシュは、フォントの `vert` 対応に依存せず90度回転するフォールバックあり
  - ページ単位の送り（タップ／スワイプ）、ページスライダー
  - ヘッダー／フッターは常時表示
- **組式プリセット**
  - 文庫 / 単行本 / 大活字 / 横書き
  - 現在の設定を名前付きで保存・削除
  - 組方向の切り替え時は、縦横それぞれに適した字数・行数へ自動調整
- **ライブラリ**
  - 表紙付きグリッド、続きから読むカード、長押し削除
  - 表紙がない EPUB はタイトルから縦書き風の表紙を自動生成
- **読書進捗の保存**
  - 章インデックス + 章内位置（組式を変えても同じ場所へ復帰）
  - アプリ再起動後も復帰
- **読書統計**
  - 今日 / 累計の読書時間、連続読書日数、読了冊数、読んだ文字数
  - 直近7日間グラフ、本ごとの読書時間と進捗
- **紙面テーマ**：生成り / 白 / 夜
- **設定**（本棚右上の歯車、またはリーダー下部の「表示」から）
  - **表示設定**：組方向、字数・行数・行間・字下げ・余白、フォント、紙面テーマ、プリセット
  - リーダーの「表示」シートと設定画面の「表示設定」は同じ編集画面を共有
  - **フォントマネージャー**：フォント一覧、TTF / OTF / TTC の追加・削除
  - **アプリ情報**：バージョン、対応形式、プライバシー、オープンソース
- **フォント**
  - 端末の明朝（serif） / ゴシック（sans-serif） / 等幅（monospace）
  - TTF / OTF / TTC を取り込んで本文に適用（アプリ専用領域にコピー）
  - 追加フォントは削除可能。使用中のフォントを削除すると明朝へ戻ります
  - フォントの選択は「設定 > 表示設定」、追加・削除は「設定 > フォントマネージャー」

## 操作

| 場所 | 操作 |
|---|---|
| 本文の左端 / 右端 | ページ送り（縦書き：左が次、右が前／横書き：右が次、左が前） |
| 左右スワイプ | ページ送り（縦書きは右スワイプで次） |
| ヘッダー／フッター | 常時表示（表示切替なし） |
| 下部バー | 目次、表示設定、ページスライダー |
| 表示設定 | リーダーのシートは閉じたときに適用／設定画面は変更を即時保存 |
| 本棚右上の歯車 | 設定（表示設定 / フォントマネージャー / アプリ情報） |
| 本棚の本 | タップで開く／長押しで削除 |

## 開発環境

Nix flake の devshell に JDK 17、Gradle、Android SDK 35、build-tools 35 を
含めています。`local.properties` は不要です。

```bash
nix develop

gradle assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
gradle testDebugUnitTest    # JVM ユニットテスト
gradle lintDebug
gradle assembleRelease      # 署名なし release APK（R8 有効）
```

`direnv` を使う場合は `.envrc` の `use flake` を許可してください。

### 実機・エミュレータへのインストール

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

ビルド済みの R8 リリース APK はローカルの `dist/tateyomi-1.0.0-debugsigned.apk`
に生成しています（ローカルの debug keystore で署名したテスト用。ストア配布用
ではありません）。APK などのビルド成果物は Git 管理外です。
この APK に EPUB は同梱されていません。本棚の「EPUBを追加」から取り込んでください。

## アーキテクチャ

```
app/src/main/java/app/tateyomi/
├── data/
│   ├── EpubParser.kt       EPUB 2/3 のテキスト・ルビ・画像・目次抽出
│   ├── AppRepository.kt    取り込み、JSON 永続化、進捗・設定・統計
│   ├── CoverGenerator.kt   表紙の正規化とプレースホルダー生成
│   ├── Fonts.kt            フォント定義（端末フォント・追加フォント・有効ID）
│   ├── FontMetadata.kt     OpenType/TrueType の name テーブルから書体名を取得
│   ├── Model.kt            書籍・章・ブロック・目次・進捗
│   ├── Settings.kt         組式設定とプリセット
│   └── Stats.kt            読書統計
├── reader/
│   ├── ReaderLayout.kt     字数×行数のグリッド組版、禁則処理、ページ割付
│   ├── ReaderFonts.kt      フォントIDから端末ファミリ／フォントファイルを解決
│   ├── ReaderTypefaces.kt  端末フォント・追加フォントの Typeface キャッシュ
│   └── ...
├── ui/
│   ├── MainViewModel.kt    画面状態、読書セッション、進捗保存、フォント管理
│   ├── AppRoot.kt          画面遷移、Snackbar、ライフサイクル
│   ├── library/            本棚
│   ├── reader/             Pager、Canvas 描画、目次・設定シート
│   ├── settings/           設定、表示設定、フォントマネージャー、アプリ情報
│   ├── stats/              読書統計
│   └── theme/              Material 3 テーマ、紙面パレット
└── util/                  時間・文字数・連続読書日数の整形
```

- **組版エンジン**は Android API に依存しない純 Kotlin です。1文字=1セルを
  基本に、ASCII はまとめて回転描画し、ルビは基底文字に追従させます。
  禁則（行頭の句読点・閉じ括弧、行末の開き括弧、欧文単語の空白折り返し）を
  簡易実装しています。
- **行間**は `fontSize × lineSpacing` を行ピッチとして厳密に適用します。
  文字サイズは「字数（インライン方向）」「行数 × 行間（ブロック方向）」の
  両方が端末の表示領域に収まるよう自動縮小され、テキストブロックは
  ページ内で中央寄せになります。行数は指定どおりに保たれます。
- **進捗**は `chapterIndex` と `chapterFraction` で保存するため、字数・行数・
  組方向を変えても同じ章内位置へ戻れます。
- **目次**は EPUB の fragment（`#id`）をブロックへ対応付け、章頭ではなく
  実際の見出し位置へジャンプします。Gutenberg の目次に混ざるルビ読みを
  見出し本文で補正します。
- **設定**はシートを閉じたときに適用します。スライダー操作中にページ割付が
  再計算されてシートが揺れるのを避けるためです。
- **表示設定の編集UI**は `ui/components/ReaderSettingsContent.kt` に集約し、
  リーダーの「表示」シートと設定画面の「表示設定」で共有しています。設定画面
  では変更を即時保存し、リーダーのシートでは閉じたときにまとめて適用します。

### 保存先

アプリ専用領域 `filesDir/tateyomi/` 以下です。

```
library.json                  本棚インデックス
settings.json                 組式設定・プリセット・フォント一覧
stats.json                    読書統計
fonts/<uuid>.<ttf|otf|ttc>    追加したフォント
books/<uuid>/source.epub      取り込んだ EPUB 原本
books/<uuid>/content.json     抽出済み本文（章・ブロック・ルビ・アンカー）
books/<uuid>/cover.jpg        正規化した表紙
books/<uuid>/images/<hash>    本文画像
```

EPUB は取り込み時にアプリ領域へコピーします。元ファイルを移動・削除しても
読めます。本を削除すると上記ディレクトリと統計からも削除します。
追加フォントもアプリ領域へコピーするため、元ファイルを削除しても使用できます。
フォントファイルが見つからない場合は一覧から除外し、使用中なら明朝へ戻します。

## テスト

### JVM ユニットテスト

```bash
gradle testDebugUnitTest
```

- `ReaderLayoutTest`：字数・行数・禁則・ルビ分割・ページ/進捗の往復
- `ReaderMetricsTest`：行数・行間を保った自動縮小と行ピッチ
- `VerticalGlyphsTest`：省略記号・ダッシュ類の縦組み回転判定
- `FontsTest`：端末フォントIDと旧設定の互換、追加フォントの解決・フォールバック
- `FontMetadataTest`：フォントでないファイルの書体名解析
- `EpubParserTest`：合成 EPUB（ルビ、画像、名前空間付き OPF、固定レイアウト画像 spine）
- `FormattingTest`：時間・文字数・連続読書日数
- `EpubCollectionTest`：手元の EPUB コーパスを一括パース（環境変数があるときだけ実行）

```bash
TATEYOMI_EPUB_DIR=~/epub-collection gradle testDebugUnitTest --tests '*EpubCollectionTest*'
```

Project Gutenberg の日本語 EPUB 2/3、台湾の繁体字 EPUB、固定レイアウト画像
EPUB など、作業時点のコーパス 29 冊でパースを確認済みです（現在の手元
コーパスは 10 冊で、こちらも 10/10 パース成功。コーパスはリポジトリに含みません）。

### 計測用シード（任意）

エミュレータで UI を確認するための instrumentation テストがあります。
`app/src/androidTest/assets` のパブリックドメイン EPUB を本棚へ取り込みます。

```bash
gradle connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.tateyomi.SeedLibraryTest
```

`connectedDebugAndroidTest` は終了後にアプリをアンインストールする場合が
あります。その場合は上記 APK を手動インストールし、`adb shell am instrument`
で `SeedLibraryTest` を実行するとデータが残ります。詳細は
`app/src/androidTest/assets/NOTICE.txt` を参照してください。

## EPUB 対応と制限

- 本文は **テキスト優先** で抽出します。出版社 CSS は使わず、見出し・段落・
  ルビ・強調・画像だけを保持します。
- 画像は1枚につき1ページの全画面表示です（本文への回り込みなし）。
- SVG 画像はスキップします。DRM 付き EPUB は読めません。
- 表や複雑な段組は直列化されます。
- 禁則処理は基本的な集合のみです。`<br>` や段落境界をまたぐ行頭句読点は
  完全には防げません。
- 行数や行間を大きくすると、端末の表示領域に収めるために文字サイズが
  自動的に小さくなります。
- 縦書きの欧文は回転描画のため、字詰めに余白が出ることがあります。
- Canvas 描画のため、現状は TalkBack による本文読み上げに対応していません。
- フォントは端末の serif / sans-serif / monospace に加え、TTF / OTF / TTC を
  追加できます。端末に日本語明朝体がない場合は代替フォントになります。専用の
  縦書きフォントは同梱していません。追加フォントはアプリが読み込める形式である
  必要があり、壊れたファイルや 64MB を超えるファイルは追加できません。多くの
  句読点は OpenType の `vert` を使い、`……` などの
  省略記号・ダッシュ・長音・波ダッシュは Latin フォールバックでも正しく
  縦になるよう、フォント機能を切って90度回転するフォールバックを実装しています。
- 本文のないページ（表紙のみ等）は取り込み時に除外します。画像だけのページは
  固定レイアウト本のために残します。
- 読書文字数は「ページ送り1回あたりの平均文字数」からの概算です。

## クレジット / ライセンス

- アプリ本体のライセンスは現時点で未指定です。必要に応じて `LICENSE` を
  追加してください。
- `app/src/androidTest/assets` の EPUB は Project Gutenberg 由来の
  パブリックドメイン作品で、UI 検証用です。リリース APK には含まれません。
  出典と注意は `NOTICE.txt` に記載しています。
- スクリーンショットの本文は上記パブリックドメイン作品です。
