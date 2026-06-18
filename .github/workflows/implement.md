---
# Issue 上で `/approve` とコメントすると起動する Implement フェーズのワークフロー。
# 最新の `[plan]` コメントの計画に従ってコードを修正し、PR を作成する。
on:
  slash_command:
    name: approve

engine:
  id: claude
  model: claude-sonnet-4-6

# エージェント本体は読み取り専用。PR 作成は safe-outputs.create-pull-request の別ジョブが
# 自動で pull-requests: write を得て行う（strict mode ではトップに write を書けない）。
permissions:
  contents: read
  issues: read

tools:
  bash: ["echo", "ls", "pwd", "cat", "head", "tail", "grep", "find", "wc", "sort", "uniq"]
  github:
    toolsets: [issues]
  # 実装フェーズなのでワークスペース上のファイル編集を許可する
  edit: true

# エージェントが加えた変更は別の信頼済みジョブが PR として作成する
safe-outputs:
  create-pull-request:
    title-prefix: "[ai-agent] "
    labels: [ai-agent]
    draft: false
  # 計画が見つからない等で実装に進めない場合の通知用
  add-comment:
    max: 1
    target: "triggering"
---

# AIエージェント: 実装と PR 作成（Implement フェーズ）

Issue 上で `/approve` コメントを受けたら、**その Issue の最新の `[plan]` コメントに書かれた実装計画に従って**コードを修正し、Pull Request を作成してください。

## コンテキスト

- リポジトリ: Kotlin + Spring Boot の簡易 TODO アプリ（REST API、インメモリ保存）。
- 主なソース: `src/main/kotlin/com/example/todo/` 配下。
- 対象 Issue: #${{ github.event.issue.number }}「${{ github.event.issue.title }}」

## やること

1. Issue 本文と、**`[plan]` で始まる最新のコメント**（承認された実装計画）を読む。複数あれば最も新しいものを採用する。
2. その計画の「実装ステップ」に従って `src/` 配下のコードを修正する。
3. Pull Request を作成する。**main へ直接コミットせず、必ず新規ブランチを切る**。ブランチ名は `ai-agent/issue-${{ github.event.issue.number }}-<英数字の短い概要>` の形式にする。本文には次を含める:
   - 対応する計画の要約
   - **`Fixes #${{ github.event.issue.number }}`**（Issue と連動させ、マージ時に自動クローズする）
   - テスト方針・確認手順

## 制約

- **必ず最新の `[plan]` 計画に従う**。計画に書かれていない変更は最小限にとどめる。
- 計画コメントが見つからない場合は、コードを変更せず、その旨を PR 本文ではなくエラーとして扱い、Issue にコメントで「計画が見つからないため先に `/plan` を実行してほしい」と促す。
- 推測と事実を区別する。出力は日本語で記述する。
