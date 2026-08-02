# Note

AndroidX Inkを土台にした、Androidタブレット／スマートフォン向けのオープンソース手書きノートアプリです。

## 実装済み

- Material 3とDynamic Color
- AndroidX Inkによる低遅延・筆圧対応のベクター手書き
- スマートフォン向け1ペインUI、タブレット向けフォルダー＋ライブラリーの2ペインUI
- 複数ノートを開くタブ
- ページの縦スクロール／横スクロール切り替え
- PDFインポートと各ページへの追記
- 任意階層のフォルダー
- ノート／PDF先頭ページのサムネイル
- Undo／Redo、線全体／部分消しゴム、自動保存
- Lenovo系ペンのボタン／ダブルタップによる消しゴム切り替え
- Google Drive `appDataFolder`を使った端末間同期
- 投げ縄選択（交差／25%／50%／90%包含率）、メッシュ基準の選択枠
- Pressure Pen、Marker、Highlighter、パラメーター式カスタムブラシ
- 選択筆跡の移動・拡縮・削除・色／太さ／ブラシ変更
- 編集可能な独自形式 `.atnote`

## `.atnote`形式

`.atnote`はZIPコンテナーです。

```text
manifest.json             ノート、ページ、ブラシ、Inkエントリー参照
background/source.pdf     PDFから作成したノートのみ。原本PDF
ink/strokes/<id>.bin      AndroidX Ink Storageの公式圧縮入力列
images/<id>.png           ページへ配置した編集可能な画像
```

ストロークはAndroidX Inkの`StrokeInputBatch`をInk Storageのgzip圧縮Protocol Buffersとして直接保存し、manifestにはブラシ設定と参照先を保持します。そのため、Drive同期後もPDF画像ではなく、ペン色・太さ・消去・Undo対象として再編集できます。

## Google Drive同期

同期対象はGoogle Driveの非表示領域`appDataFolder`です。通常のマイドライブ画面にはノートファイルを表示せず、アプリだけがアクセスします。

- ノート単位でリビジョンとSHA-256を比較
- フォルダー階層は`library-index.json`として同期
- 同一リビジョンで内容が異なる場合、リモート版を`(conflict)`ノートとして保存
- リアルタイム共同編集やストローク単位のCRDTマージは行わない

### OAuth設定

Drive同期を実機で使用するには、ビルドするGoogle Cloudプロジェクト側で次を設定してください。

1. Google Drive APIを有効化する。
2. OAuth同意画面を設定する。
3. Android OAuthクライアントを作成する。
4. パッケージ名に`com.atuy.note`を指定する。
5. 使用する署名鍵のSHA-1を登録する。デバッグビルドでは通常、`~/.android/debug.keystore`のSHA-1を使う。
6. テスト公開中は使用するGoogleアカウントをテストユーザーへ追加する。

クライアントシークレットをAPKへ埋め込む必要はありません。アプリはGoogle Identity Servicesの`AuthorizationClient`から`drive.appdata`スコープの短期アクセストークンを取得します。

## Lenovoペン

次の入力を処理します。

- 標準Androidスタイラスのプライマリ／セカンダリボタン
- `TOOL_TYPE_ERASER`
- Lenovo Tab Pen Plusで観測されるキーコード`601`
- 一部ペンのダブルタップで観測されるキーコード`718`（650 ms以内の2回入力）

端末やファームウェアによってキーコードが異なる場合は、`MainViewModel.handleStylusKey`へ追加してください。

## ビルド

必要環境：JDK 17、Android SDK 35、Gradle 8.11.1。

```bash
gradle :app:assembleDebug
```

Android Studioではリポジトリを開いて通常どおりSync／Runできます。GitHub Actionsも同じ条件で`assembleDebug`とユニットテストを実行します。

APK出力：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 現在の制約

- AndroidX Inkの公式メッシュ部分消しは永続化制約があるため、部分消しは入力列を分割して再生成します。

- Drive同期は明示的な同期ボタンで開始します。
- 同じノートを複数端末で同時編集した場合は自動マージせず、競合コピーを残します。
- PDFのテキスト編集ではなく、原本PDFの上に編集可能なInkストロークを保持します。

## ライセンス

Apache License 2.0
