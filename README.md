# todo-app

Kotlin + Spring Boot による簡易 TODO アプリ（REST API、MySQL + Spring Data JPA で永続化）。

## 必要環境

- JDK 17
- Docker（MySQL を起動するため）

Gradle は同梱の Wrapper (`./gradlew`) を使うためインストール不要。

## 起動

MySQL を起動してからアプリを起動する。MySQL の定義は `compose.yaml` にある。

```bash
docker compose up --wait   # MySQL を起動（接続可能になるまで待つ）
./gradlew bootRun
```

`http://localhost:8080` で起動する。接続先は `application.properties` の既定（`localhost:3306/todo`、ユーザー/パスワード `todo`）。停止は `docker compose down`（データ保持）/ `docker compose down -v`（データ破棄）。

## テスト

MySQL に対する統合テストを含むため、MySQL を起動し `RUN_DB_TESTS=1` を付けて実行する（未設定時は DB 依存テストをスキップ）。

```bash
docker compose up --wait
RUN_DB_TESTS=1 ./gradlew test
```

## API

| メソッド | パス          | 説明       |
| -------- | ------------- | ---------- |
| GET      | `/todos`      | 一覧取得   |
| GET      | `/todos/{id}` | 1件取得    |
| POST     | `/todos`      | 作成       |
| PUT      | `/todos/{id}` | 更新       |
| DELETE   | `/todos/{id}` | 削除       |

リクエスト/レスポンスのボディは JSON。

```jsonc
// Todo
{ "id": 1, "title": "牛乳を買う", "done": false }

// 作成・更新リクエスト
{ "title": "牛乳を買う", "done": false }
```

## 使用例

```bash
# 作成
curl -s -X POST localhost:8080/todos \
  -H 'Content-Type: application/json' \
  -d '{"title":"牛乳を買う"}'

# 一覧
curl -s localhost:8080/todos

# 完了に更新
curl -s -X PUT localhost:8080/todos/1 \
  -H 'Content-Type: application/json' \
  -d '{"title":"牛乳を買う","done":true}'

# 削除
curl -s -X DELETE localhost:8080/todos/1 -i
```

## 注意

データは MySQL に永続化される。スキーマは起動時に Hibernate の `ddl-auto=update` で自動生成・更新される。
