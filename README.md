# todo-app

Kotlin + Spring Boot による簡易 TODO アプリ（REST API、インメモリ保存）。

## 必要環境

- JDK 17

Gradle は同梱の Wrapper (`./gradlew`) を使うためインストール不要。

## 起動

```bash
./gradlew bootRun
```

`http://localhost:8080` で起動する。

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

データはインメモリ（`ConcurrentHashMap`）保存のため、再起動で消える。
