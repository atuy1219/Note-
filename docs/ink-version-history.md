# AndroidX Ink 更新履歴

2026-10-01時点のNoteは `1.1.0-alpha09`。出典: https://developer.android.com/jetpack/androidx/releases/ink

| バージョン | 公式公開日 | Noteへの導入日 | 主な変更 |
|---|---|---|---|
| 1.0.0 | 2025-12-17 | 2026-08-01 | 安定版。graphics-core 1.0.4へ更新。 |
| 1.1.0-alpha05 | 2026-07-15 | 2026-08-02 | 実験的な部分消しゴム。メッシュの編集・線の分割。切断面のアンチエイリアスと消去後メッシュの保存APIは未対応。 |
| 1.1.0-alpha06 | 2026-07-29 | 2026-08-03（#10） | BrushFamilyの必要バージョン判定API、AffineTransformの成分を公開。 |
| 1.1.0-alpha07 | 2026-08-12 | 2026-08-15（#24） | 高密度パーティクルブラシの性能回帰を修正。Brush.Version定数の調整。 |
| 1.1.0-alpha08 | 2026-09-09 | 2026-09-12（#32） | iOS MetalRenderer、平行移動API、ブラシ関連APIの名称・型変更。部分消しゴムAPIにWorkerThread指定。 |
| 1.1.0-alpha09 | 2026-09-23 | 2026-09-26（#36） | 色シフトをOklabへ変更。SATURATION/LUMINOSITYがCHROMA/LIGHTNESSへ。lifecycle-runtime 2.10.0依存で新しいAGPのLint互換性を改善。 |

## Noteへの影響

- Noteは現在、線全体消しゴムを使用する。依存の更新だけでは部分消しゴムは有効にならない。
- 現在のカスタムブラシは先端形状・SlidingWindowModelを使用する。alpha08/09で改名された色シフト・DampingNodeのAPIは使用していない。
- alpha07の性能修正はパーティクルブラシ向け。現在のPressure Pen/Marker/Highlighterが一律高速化するとは断定しない。
- iOS対応はライブラリの変更であり、このAndroidアプリへのiOS機能追加ではない。
