package com.example.todo

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/**
 * 実 MySQL に対する TODO API の統合テスト。JPA が実際に SQL を発行する。
 *
 * MySQL への接続が必要なため、環境変数 `RUN_DB_TESTS` が設定されているときだけ実行する。
 * 接続先は `application.properties`（既定）または `SPRING_DATASOURCE_*` 環境変数で指定する。
 * explain-sql ワークフローは MySQL を起動したうえで `RUN_DB_TESTS=1` で本テストを流し、
 * general_log に発行 SQL を残して EXPLAIN にかける。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = ".+")
class TodoApiTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) {

    private fun createTodo(title: String, pinned: Boolean = false): Long {
        val body = objectMapper.writeValueAsString(TodoRequest(title = title, pinned = pinned))
        val res = mockMvc.post("/todos") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect { status { isCreated() } }.andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("id").asLong()
    }

    private fun currentPending(): Int {
        val res = mockMvc.get("/todos/stats").andExpect { status { isOk() } }.andReturn()
        return objectMapper.readTree(res.response.contentAsString).get("pendingCount").asInt()
    }

    @Test
    fun `作成した TODO を取得できる`() {
        val id = createTodo("牛乳を買う")
        mockMvc.get("/todos/$id").andExpect {
            status { isOk() }
            jsonPath("$.title") { value("牛乳を買う") }
            jsonPath("$.done") { value(false) }
        }
    }

    @Test
    fun `一覧はピン止めと通常に分かれる`() {
        val normalId = createTodo("通常タスク")
        val pinnedId = createTodo("重要タスク", pinned = true)
        mockMvc.get("/todos").andExpect {
            status { isOk() }
            jsonPath("$.pinned[?(@.id == $pinnedId)]") { exists() }
            jsonPath("$.todos[?(@.id == $normalId)]") { exists() }
        }
    }

    @Test
    fun `stats は未完了の件数を返す`() {
        val before = currentPending()
        createTodo("未完了タスク")
        assertThat(currentPending()).isEqualTo(before + 1)
    }

    @Test
    fun `ピン状態をトグルできる`() {
        val id = createTodo("トグル対象")
        mockMvc.patch("/todos/$id/pin").andExpect {
            status { isOk() }
            jsonPath("$.pinned") { value(true) }
        }
    }

    @Test
    fun `更新と削除ができる`() {
        val id = createTodo("更新前")
        val body = objectMapper.writeValueAsString(TodoRequest(title = "更新後", done = true))
        mockMvc.put("/todos/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("更新後") }
            jsonPath("$.done") { value(true) }
        }
        mockMvc.delete("/todos/$id").andExpect { status { isNoContent() } }
        mockMvc.get("/todos/$id").andExpect { status { isNotFound() } }
    }

    @Test
    fun `存在しない ID は 404 を返す`() {
        mockMvc.get("/todos/999999").andExpect { status { isNotFound() } }
    }
}
