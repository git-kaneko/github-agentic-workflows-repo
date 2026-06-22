---
# 【雛型】PR で ORM(JPA) のテーブル操作が変わったとき、実際に発行される生 SQL を
# MySQL 上で EXPLAIN し、その結果＋レビュー観点を PR にコメントするワークフロー。
#
# 仕組みの要点:
#   1. PR 差分に Entity / Repository / マイグレーションの変更が無ければ何もしない。
#   2. リポジトリの compose.yaml で MySQL を起動する（ローカル開発と同じ定義を使い回す）。
#   3. general_log を ON にしてリポジトリのテストを流し、発行された生 SQL を捕捉する。
#      （MySQL に PostgreSQL の auto_explain 相当が無いため、この「ログ→抽出」方式を採る）
#   4. 捕捉した SELECT を一つずつ EXPLAIN し、フルスキャン等の観点を付けて整形する。
#   5. safe-outputs.add-comment で結果を PR にコメントする。
#
# ※ 前提: Todo アプリが JPA 化（@Entity + JpaRepository）され、実 MySQL に対する
#   統合テスト（RUN_DB_TESTS で有効化）が存在すること。テストが SQL を発行しない場合は
#   手順3で「対象なし」として終了する。
on:
  pull_request:
    types: [opened, synchronize, reopened]

engine:
  id: claude
  model: claude-sonnet-4-6
  # ハード上限: 総ターン数（暴走保険／コストキャップ）
  max-turns: 80
  env:
    # DB 依存の統合テスト（@EnabledIfEnvironmentVariable）を有効化する。
    # これにより `./gradlew test` が実 MySQL に接続し、JPA が SQL を発行する。
    RUN_DB_TESTS: "1"

# ハード上限: ジョブ全体の時間。これを超えると強制終了。
timeout-minutes: 30

# エージェント本体は読み取り専用。PR へのコメントは safe-outputs の別ジョブが行う。
permissions:
  contents: read
  pull-requests: read

# Gradle の依存解決（java）と、docker hub からの MySQL イメージ取得を許可する。
# `defaults` を残さないと既定の許可先が消える。MySQL イメージが pull できない場合は
# ここに docker レジストリの到達先を追加すること。
network:
  allowed: [defaults, java]

tools:
  # 読み取り専用コマンド + git（差分/ベース取得）+ gradle + docker compose + mysql クライアント
  bash:
    - echo
    - ls
    - pwd
    - cat
    - head
    - tail
    - grep
    - find
    - wc
    - sort
    - uniq
    - git status
    - git diff
    - git fetch
    - "./gradlew:*"
    - "docker:*"
    - "mysql:*"
    - sleep
  # PR の情報を読むための GitHub MCP ツールセット
  github:
    toolsets: [pull_requests]
  # このワークフローはコードを変更しない（調査と EXPLAIN のみ）
  edit: false

# エージェントの出力はサニタイズされ、別の信頼済みジョブから PR コメントとして投稿される。
safe-outputs:
  add-comment:
    max: 1
    target: "triggering"   # = トリガーした PR
---

# AIエージェント: ORM の生 SQL を MySQL で EXPLAIN して PR にレビュー出力

この PR の差分に **ORM(JPA) によるテーブル操作の変更**が含まれる場合、実際に発行される生 SQL を MySQL 上で `EXPLAIN` し、その結果と簡単なレビュー観点を **PR にコメント**してください。変更が含まれない場合は**何もせず終了**します。

## コンテキスト

- リポジトリ: Kotlin + Spring Boot の TODO アプリ。**JPA(Hibernate) + MySQL** でテーブルを操作する。
- 主なソース: `src/main/kotlin/com/example/todo/` 配下（`@Entity`、`JpaRepository` を継承したリポジトリ等）。エンティティ `Todo` は `todos` テーブルに対応する。
- MySQL の起動定義は **リポジトリ直下の `compose.yaml`**（ローカル開発と共通）。接続先 `localhost:3306/todo`（ユーザー/パスワード `todo`、root パスワード `root`）は `application.properties` の既定と一致する。
- 対象 PR: #${{ github.event.pull_request.number }}「${{ github.event.pull_request.title }}」
- MySQL に PostgreSQL の `auto_explain` 相当は無い。よって **general query log で発行 SQL を捕捉 → 各 SQL を EXPLAIN** する方式を採る。

## やること

1. **差分検知**: ベースブランチを取得して差分を見る。
   ```bash
   git fetch origin
   git diff --name-only ${{ github.event.pull_request.base.sha }}...HEAD
   ```
   `@Entity` を含むクラス、`Repository`（`JpaRepository`/`@Query` 等）、SQL/マイグレーション（`*.sql`、Flyway/Liquibase）に**変更が無ければ、ここで終了**する（PR コメントもしない）。

2. **MySQL 起動**: リポジトリの `compose.yaml` で MySQL を起動する。healthcheck 付きなので `--wait` で接続可能になるまで待てる。
   ```bash
   docker compose up --wait
   ```

3. **スキーマ構築と SQL 捕捉**: テストを通じて実 SQL を発行させ、ログから捕捉する。
   1. `mysql -h127.0.0.1 -uroot -proot -e "SET GLOBAL general_log='ON'; SET GLOBAL log_output='TABLE';"` でクエリログを有効化する（`SET GLOBAL` は root 権限が必要）。
   2. `./gradlew test --no-daemon` でリポジトリのテストを実行する（`RUN_DB_TESTS` は設定済み）。テストは `application.properties` の既定で上記 MySQL に接続し、`ddl-auto` でスキーマを生成しつつ JPA に SQL を発行する。
   3. テストで投入されたデータに対し `mysql -h127.0.0.1 -uroot -proot -e "ANALYZE TABLE todos;" todo` で統計を更新する（EXPLAIN の精度向上のため）。
   4. 発行された生 SQL を抽出する。重複と、`todos` に関係しない付随クエリ（Hikari の `SELECT 1` など）は除く。これは同じ EXPLAIN を何度も貼らないためのノイズ除去であり、各 EXPLAIN の**中身は手順6で全文を出す**:
      ```bash
      mysql -h127.0.0.1 -uroot -proot -N -e \
        "SELECT DISTINCT argument FROM mysql.general_log \
         WHERE command_type='Query' AND argument LIKE 'SELECT%' \
           AND argument LIKE '%todos%';" todo
      ```
   - SQL が 1 件も捕捉できなければ「EXPLAIN 対象の SELECT 無し」として手順6で簡潔に報告し終了する。

4. **EXPLAIN 実行**: 捕捉した各 SELECT に対して EXPLAIN を実行し、**出力 JSON の全文を取得する**（抜粋しない）。
   ```bash
   mysql -h127.0.0.1 -uroot -proot -e "EXPLAIN FORMAT=JSON <SQL>;" todo
   # MySQL 8.0 なら実測込みの EXPLAIN ANALYZE も可:
   # mysql -h127.0.0.1 -uroot -proot -e "EXPLAIN ANALYZE <SQL>;" todo
   ```

5. **レビュー観点の付与**: 各 EXPLAIN 結果を読み、次の観点で注意点を拾う。
   - `type: ALL`（フルテーブルスキャン）になっていないか
   - `key: NULL` / `possible_keys: NULL`（インデックス未使用）になっていないか
   - `rows`（走査見込み行数）が想定よりも過大でないか
   - `Using filesort` / `Using temporary` が出ていないか

6. **PR コメント**: 結果を Markdown で **add-comment** に出力する。差分検知（手順1）で対象有りと判定した場合のみ投稿する。本文には次を含める:
   - 対象とした変更（どの Entity/Repository/SQL が変わったか）
   - 各 SQL について、**生 SQL** と **`EXPLAIN FORMAT=JSON` の結果全文**。JSON は ```json コードブロックに**そのまま貼る**。要点だけの抜粋・要約・省略・`<details>` 折りたたみはしない（読み手が完全な実行計画を見られるようにする）
   - 上記観点での **気になる点**（無ければ「特になし」）。これは全文 JSON への補足であって、JSON 本体を置き換えるものではない
   - 末尾に「これは自動生成のヒントであり、最終判断は人間が行う」旨の注記

7. **後始末**: `docker compose down -v` で MySQL を必ず破棄する（ボリュームごと使い捨て）。

## 制約

- **差分に ORM のテーブル操作変更が無ければ何も出力しない**（ノイズを出さない）。
- このワークフローは **コードを変更しない**（EXPLAIN と報告のみ）。
- MySQL は `compose.yaml` で起動し、終了時に必ず `docker compose down -v` で破棄する。
- EXPLAIN の失敗（構文・接続）でジョブ全体を落とさず、失敗した SQL はその旨を添えて続行する。
- 推測と事実を区別する。出力は日本語で記述する。
