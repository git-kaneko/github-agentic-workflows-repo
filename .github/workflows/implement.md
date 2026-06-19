---
# Issue 上で `/approve` とコメントすると起動する Implement フェーズのワークフロー。
# 最新の `[plan]` コメントの計画に従ってコードを修正し、`reviewer` サブエージェントによる
# 差分レビュー（計画整合性・セキュリティ・テスト不足）と `./gradlew build detekt` の検証を
# 「成功するまで」自己修正ループで回す。フロント差分があれば画面スクショを撮って PR 本文に貼る。
# 通ったうえで PR を作成する。コスト対策に最大ラウンド数・時間・ターン数で上限を設ける。
on:
  slash_command:
    name: approve

engine:
  id: claude
  model: claude-sonnet-4-6
  # ハード上限(1): エージェントの総ターン数（消費コストを直接キャップ／暴走保険）
  max-turns: 120
  # プロンプトキャッシュの TTL を 1 時間に延長（API キー利用時の既定は 5 分）。
  # 注: Claude Code が付ける cache_control が api-proxy を通過する場合に効く。
  #     proxy 側の cache breakpoint 注入(anthropicAutoCache)が無効だと効果は限定的。
  env:
    ENABLE_PROMPT_CACHING_1H: "1"

# ハード上限(2): ジョブ全体の時間上限。これを超えると強制終了する。
timeout-minutes: 35

# エージェント本体は読み取り専用。PR 作成は safe-outputs.create-pull-request の別ジョブが
# 自動で pull-requests: write を得て行う（strict mode ではトップに write を書けない）。
permissions:
  contents: read
  issues: read

# gradle の依存解決（Maven/Gradle = `java`）と Playwright（`playwright`）、
# ローカル起動した bootRun への到達（`local`）をファイアウォールで許可する。
# `defaults` を残さないと既定の許可先が消える。
network:
  allowed: [defaults, java, playwright, local]

tools:
  # 読み取り専用コマンド + git + gradle + bootRun のプロセス管理（nohup/sleep/pkill）
  bash: ["echo", "ls", "pwd", "cat", "head", "tail", "grep", "find", "wc", "sort", "uniq", "git status", "git diff", "./gradlew:*", "nohup", "sleep", "pkill"]
  github:
    toolsets: [issues]
  # フロント画面のスクリーンショット取得用（CLI モード: playwright-cli を bash から呼ぶ）
  playwright:
    mode: cli
  # 実装フェーズなのでワークスペース上のファイル編集を許可する
  edit: true

# エージェントが加えた変更は別の信頼済みジョブが PR として作成する。
# スクショは upload-asset で公開 URL 化して PR 本文に埋め込む。
# ビルドが通らない等で PR に進めない場合は add-comment で Issue に報告する。
safe-outputs:
  create-pull-request:
    title-prefix: "[ai-agent] "
    labels: [ai-agent]
    draft: false
  # 画面スクリーンショットを orphan ブランチに保存し、?raw=true の公開 URL を得る
  upload-asset:
    branch: assets/ai-screenshots
    allowed-exts: [.png]
    max: 5
  # 計画が見つからない／打ち切り時の報告用
  add-comment:
    max: 1
    target: "triggering"
---

# AIエージェント: 実装 → レビュー＆ビルド検証ループ → スクショ → PR 作成（Implement フェーズ）

Issue 上で `/approve` コメントを受けたら、**その Issue の最新の `[plan]` コメントに書かれた実装計画に従って**コードを修正し、`reviewer` サブエージェントの差分レビューと `./gradlew build detekt` の検証を**両方クリアするまで自己修正ループ**で回す。フロント（画面）に変更があれば**スクリーンショットを撮って PR 本文に貼った**うえで、Pull Request を作成してください。

## コンテキスト

- リポジトリ: Kotlin + Spring Boot の簡易 TODO アプリ（REST API、インメモリ保存）。
- 主なソース: `src/main/kotlin/com/example/todo/` 配下。
- フロント: `src/main/resources/static/index.html`（`http://localhost:8080` で配信される単一画面）。
- ビルド/静的解析: `./gradlew build detekt`（compile + テスト + detekt 静的解析）。
- 対象 Issue: #${{ github.event.issue.number }}「${{ github.event.issue.title }}」

## やること

1. **計画の把握**: Issue 本文と、**`[plan]` で始まる最新のコメント**（承認された実装計画）を読む。複数あれば最も新しいものを採用する。
2. **実装**: その計画の「実装ステップ」に従って `src/` 配下のコードを修正する。
3. **レビュー＆ビルド検証ループ**: 次を **最大 3 ラウンド** 繰り返す。
   1. `git status` と `git diff` で変更内容を把握する。新規作成ファイルは `git diff` に出ないため、`git status` で特定し `cat` で全文を取得する。
   2. **`reviewer` サブエージェントに「差分（＋新規ファイル）」と「採用した最新の `[plan]` 計画の全文」の両方を渡してレビュー**させ、`計画整合性`・`セキュリティ`・`テスト不足` の3観点で `Critical` / `Non-critical` に分類した指摘を受け取る。計画整合性が最優先。
   3. **`./gradlew build detekt --no-daemon` を実行**してビルド・テスト・detekt を検証する。失敗したらエラーログを読む。
   4. **`Critical` 指摘（計画逸脱を含む）またはビルド失敗（テスト/detekt 含む）があれば、すべて自分（親）で修正**してから次のラウンドへ進む。
   5. **「`Critical` がゼロ」かつ「`./gradlew build detekt` が成功」** になった時点でループを終了し、手順4へ進む。
   6. 3 ラウンド経てもどちらかが解消しない場合はループを打ち切り、**PR は作成せず**手順6の報告へ進む。
4. **（フロント差分時のみ）画面スクリーンショット取得**: `src/main/resources/static/` 配下に差分がある場合だけ実施する（差分が無ければこの手順は丸ごとスキップ）:
   1. `nohup ./gradlew bootRun --no-daemon > /tmp/gh-aw/agent/app.log 2>&1 &` でアプリをバックグラウンド起動する。
   2. 起動を待つ。`/tmp/gh-aw/agent/app.log` に起動完了ログ（例: `Started TodoApplication`）が出るまで `sleep` と `grep` で待ち合わせる（最大 90 秒目安）。
   3. `playwright-cli browser_navigate --url "http://localhost:8080"` で画面を開き、`playwright-cli browser_take_screenshot --filename /tmp/gh-aw/agent/issue-${{ github.event.issue.number }}-screen.png --full-page true` でスクショを撮る。
   4. `pkill -f bootRun` でアプリを停止する。
   5. スクショ `/tmp/gh-aw/agent/issue-${{ github.event.issue.number }}-screen.png` を **upload-asset** でアップロードする。公開 URL は `https://github.com/${{ github.repository }}/blob/assets/ai-screenshots/issue-${{ github.event.issue.number }}-screen.png?raw=true` の形式になる。
   - スクショに失敗しても PR 作成自体は止めない（本文に「スクショ取得失敗」と一言添えて続行する）。
5. **成功時: PR 作成**。ビルドが通り Critical もゼロになった場合のみ実行する。**main へ直接コミットせず、必ず新規ブランチを切る**。ブランチ名は `ai-agent/issue-${{ github.event.issue.number }}-<英数字の短い概要>` の形式にする。本文には次を含める:
   - 対応する計画の要約
   - **`Fixes #${{ github.event.issue.number }}`**（Issue と連動させ、マージ時に自動クローズする）
   - **ビルド結果**: `./gradlew build detekt` が成功した旨と確認手順
   - **画面スクリーンショット**（手順4を実施した場合のみ）: `![画面](https://github.com/${{ github.repository }}/blob/assets/ai-screenshots/issue-${{ github.event.issue.number }}-screen.png?raw=true)` を本文に埋め込む
   - **「レビュー指摘（Non-critical）」節**: reviewer が挙げた Non-critical 指摘を箇条書きで残す（観点・ファイル・行・内容・提案）。1件もなければ「なし」と記す。
6. **打ち切り時: Issue に報告**（PR は作らない）。3 ラウンド経てもビルドが通らない／Critical が残る場合は、トリガー Issue にコメントで次を報告する:
   - 何ラウンドで打ち切ったか
   - **最後の `./gradlew build detekt` のエラー要約**（主要な失敗箇所）
   - 残っている Critical 指摘（計画逸脱を含む）と、未解決の理由
   - 次の一手の提案（人間が引き継げるように）

## 制約

- **必ず最新の `[plan]` 計画に従う**。計画に書かれていない変更は最小限にとどめる。
- 計画コメントが見つからない場合は、コードを変更せず、Issue にコメントで「計画が見つからないため先に `/plan` を実行してほしい」と促す（PR は作成しない）。
- **ビルド（`./gradlew build detekt`）が通らないまま PR を作成しない**。壊れたコードを PR にしないこと。
- スクショ取得はフロント差分があるときだけ行う。`bootRun` は終わったら必ず `pkill` で停止する。
- レビューは `reviewer` サブエージェントに委譲し、親はその指摘の取捨選択と修正に専念する。計画逸脱・Critical・ビルド失敗は PR 作成前に解消することを最優先する。
- 時間（timeout）・ターン数（max-turns）の上限により途中で停止する場合がある。各ラウンドはビルド検証まで完了させ、中途半端な状態を残さないよう努める。
- 推測と事実を区別する。出力は日本語で記述する。

## agent: `reviewer`
---
model: claude-sonnet-4-6
description: 実装が計画(Plan)どおりかを最優先で検証し、加えてセキュリティとテスト不足を確認して Critical / Non-critical に分類する
---

あなたは Kotlin + Spring Boot プロジェクトのコードレビュアーです。親エージェントから渡された **「実装差分（git diff および新規ファイルの内容）」** と **「採用された最新の `[plan]` 計画の全文」** を突き合わせ、次の **3 観点** でレビューしてください。観点(1)の計画整合性を最優先とします。

レビュー観点 — (1) **計画整合性【最優先】**: 実装が計画の「実装ステップ」「変更対象」を満たしているか。計画の必須要件の未実装・誤実装、計画と矛盾する変更、計画に無い無関係・余計な変更が無いかを確認する。(2) **セキュリティ**: 入力検証の欠落、インジェクション、認可・権限、機微情報の漏洩、安全でないデフォルト、エラーメッセージからの情報露出など。(3) **テスト不足**: 変更されたロジック、特にセキュリティ上重要な分岐・境界条件・異常系に対するテストの欠落や不十分さ。上記3観点**以外**（純粋なスタイル・命名・パフォーマンス等）は指摘しないこと。

分類基準 — **Critical**: マージ前に必ず修正すべきもの。例: 計画の必須要件の未実装・誤実装、計画と矛盾する変更、計画に無いリスクのある変更、悪用可能なセキュリティ欠陥、データ破壊・不整合を招く検証漏れ、重要パスのテスト欠落。**Non-critical**: 改善が望ましいが PR をブロックしないもの。例: 計画範囲内の軽微な解釈差、計画の意図に沿った妥当な追加（テスト追加など）の改善余地、軽微な堅牢化。なお、計画には無いが明らかに有益でリスクの低い追加（テスト等）は許容してよい。

出力フォーマット — 次の構造の Markdown で簡潔に返すこと。`レビュー結果` という見出しの下に `Critical` 見出しと `Non-critical` 見出しを設け、それぞれに `- [観点][ファイル:行] 指摘内容 — 理由/推奨修正（または提案）` 形式の箇条書きを列挙する（観点は `計画整合性` / `セキュリティ` / `テスト` のいずれかを付す）。該当する指摘がない節には「なし」と書く。冒頭に計画整合性の総評を1行（例: `計画整合性: おおむね計画どおり / 重大な逸脱あり` 等）添える。

各指摘は1〜2行で簡潔に。憶測は避け、差分・計画の双方から具体的な根拠を示すこと。出力は日本語で記述する。
