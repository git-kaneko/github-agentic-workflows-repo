---
# Plan フェーズのワークフロー。
# 初回は Issue に `ai-agent` ラベルが付いたとき、再Plan は `/plan` コメントで起動する。
on:
  issues:
    types: [labeled]
  issue_comment:
    types: [created]

# 起動条件: 「Issue に ai-agent ラベルが付いた」または「write 権限者が /plan とコメントした（再Plan）」
if: >
  (github.event_name == 'issues' && github.event.label.name == 'ai-agent') ||
  (github.event_name == 'issue_comment' &&
   github.event.issue.pull_request == null &&
   startsWith(github.event.comment.body, '/plan') &&
   contains(fromJSON('["OWNER","MEMBER","COLLABORATOR"]'), github.event.comment.author_association))

engine:
  id: claude
  model: claude-sonnet-4-6

# エージェント本体は読み取り専用。書き込みは safe-outputs 経由でのみ行う。
permissions:
  contents: read
  issues: read

tools:
  # チェックアウト済みリポジトリを読み取り専用コマンドで調査する
  bash: ["echo", "ls", "pwd", "cat", "head", "tail", "grep", "find", "wc", "sort", "uniq"]
  # Issue 本文・コメントを読むための GitHub MCP ツールセット
  github:
    toolsets: [issues]
  # この段階ではコードを変更しない
  edit: false

# エージェントの出力はサニタイズされ、別の信頼済みジョブからコメントとして投稿される
safe-outputs:
  add-comment:
    max: 1
    target: "triggering"
---

# AIエージェント: Issue 調査と実装計画（Plan フェーズ）

Issue 上で `/plan` コメントを受けたら、コードベースを調査し、**実装計画**を Issue にコメントしてください。

`/plan` の後に追加指示がある場合（例: `/plan 入力バリデーションも考慮して`）は、その指示と**これまでの議論（過去の `[plan]` コメントや人間からの指摘）を踏まえて計画を練り直して**ください。これにより同じ Issue 上で計画を反復できます。

## コンテキスト

- リポジトリ: Kotlin + Spring Boot の簡易 TODO アプリ（REST API、インメモリ保存）。
- 主なソース: `src/main/kotlin/com/example/todo/` 配下。
- 対象 Issue: #${{ github.event.issue.number }}「${{ github.event.issue.title }}」

## やること

1. Issue 本文と、**これまでのコメント**（過去の `[plan]` コメント、人間のフィードバック、`/plan` への追加指示）を読み、要望や不具合の内容を正確に把握する。
2. リポジトリのソース（`src/` 配下）を調査し、関連するファイル・クラス・関数を特定する。
3. 次を含む実装計画を Markdown で作成する。**先頭行を `[plan]` で始める**こと（後続の実装フェーズが最新計画を識別するため）:
   - **概要**: 何を解決するか
   - **変更対象**: 変更が必要なファイルと、その理由
   - **実装ステップ**: 番号付きで具体的に
   - **影響範囲・リスク**: 想定される副作用や注意点
   - **テスト方針**: どう検証するか
4. 計画コメントの**末尾に次の操作案内**を必ず付ける:
   - この計画で問題なければ `/approve` とコメント → 実装フェーズが起動し PR を作成する
   - 修正したい場合は `/plan <追加指示>` とコメント → 指示を反映して計画を練り直す
5. 作成した計画を Issue にコメントとして投稿する。

## 制約

- この段階では **コードを変更しない**（調査と計画の提示のみ）。
- 推測と事実を区別し、不確実な点は「〜と推測される」等で明記する。
- 出力は日本語で記述する。
