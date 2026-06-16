---
# Issue に "ai-agent" ラベルが付いたら起動するエージェントワークフロー
on:
  issues:
    types: [labeled]

# labeled イベントは全ラベルで発火するため、"ai-agent" のときだけ実行する
if: ${{ github.event.label.name == 'ai-agent' }}

engine: gemini

# エージェント本体は読み取り専用。書き込みは safe-outputs 経由でのみ行う。
permissions:
  contents: read
  issues: read

# 起動可能なユーザーを制限したい場合は有効化する（既定でも write 権限保有者に制限される）。
# roles: [admin, maintainer]

tools:
  # チェックアウト済みリポジトリを読み取り専用コマンドで調査する
  bash: ["echo", "ls", "pwd", "cat", "head", "tail", "grep", "find", "wc", "sort", "uniq"]
  # Issue を読むための GitHub MCP ツールセット
  github:
    toolsets: [issues]
  # ファイル編集は無効（この段階では計画のみ）
  edit: false

# エージェントの出力はサニタイズされ、別の信頼済みジョブからコメントとして投稿される
safe-outputs:
  add-comment:
    max: 1
    target: "triggering"
---

# AIエージェント: Issue 調査と実装計画

`ai-agent` ラベルが付いた Issue について、コードベースを調査し、**実装計画**をコメントとして投稿してください。

## コンテキスト

- リポジトリ: Kotlin + Spring Boot の簡易 TODO アプリ（REST API、インメモリ保存）。
- 主なソース: `src/main/kotlin/com/example/todo/` 配下。
- 対象 Issue: #${{ github.event.issue.number }}「${{ github.event.issue.title }}」

## やること

1. Issue 本文を読み、要望や不具合の内容を正確に把握する。
2. リポジトリのソース（`src/` 配下）を調査し、関連するファイル・クラス・関数を特定する。
3. 次を含む実装計画を Markdown で作成する:
   - **概要**: 何を解決するか
   - **変更対象**: 変更が必要なファイルと、その理由
   - **実装ステップ**: 番号付きで具体的に
   - **影響範囲・リスク**: 想定される副作用や注意点
   - **テスト方針**: どう検証するか
4. 作成した計画を Issue にコメントとして投稿する。

## 制約

- この段階では **コードを変更しない**（調査と計画の提示のみ）。
- 推測と事実を区別し、不確実な点は「〜と推測される」等で明記する。
- 出力は日本語で記述する。

<!--
## 将来の拡張: コード修正して PR を作成する場合
上記フロントマターを次のように変更する:
  - permissions に `contents: write` と `pull-requests: write` を追加
  - tools の `edit: false` を削除（編集を許可）
  - safe-outputs に create-pull-request を追加:
      safe-outputs:
        create-pull-request:
          title-prefix: "[ai-agent] "
          labels: [ai-agent]
  - 本文の指示を「実装計画」から「コードを修正し PR を作成する」へ変更
-->
