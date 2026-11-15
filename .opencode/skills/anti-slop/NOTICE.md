# NOTICE — anti-slop（UI文言）

このディレクトリのスキルは **vendored**（取り込み＋改変）です。
npm 等の依存としては参照せず、ファイルをこのリポジトリで直接管理します。

## ベース

- リポジトリ: https://github.com/dmmulroy/anti-slop
- 参照時点: `main` @ `13909c9e07590f5a85d93c62712259fe14a883f7`（v0.1.2 の11コミット後）
- 上流 README の方針: 「This project is meant to be vendored … the vendored files are
  yours to maintain and make your own.」

## 本リポジトリでの変更

- 上流の Oxlint / TypeScript 向けルール群ではなく、tateyomi の日本語 UI 文言
  （ラベル・ボタン・ヘルパー・エラー・スナックバー・ダイアログ・空状態）を検査する
  規範として再構成しています。
- 用語集・スロット規則・禁止構文・改善例は本プロジェクト用に記述しています。
- 上流の更新は自動では取り込みません。取り込む場合は差分を確認し、手動で反映します。

## ライセンス（MIT）

Copyright (c) 2026 Dillon Mulroy

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
