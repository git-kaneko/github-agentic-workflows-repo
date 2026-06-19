package com.example.todo

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** 統計情報レスポンス。 */
data class TodoStats(
    val pendingCount: Int,
)

/** TODO 1件を表すデータ。 */
data class Todo(
    val id: Long,
    val title: String,
    val done: Boolean = false,
    val pinned: Boolean = false,
)

/** 作成・更新リクエストのボディ。 */
data class TodoRequest(
    val title: String,
    val done: Boolean = false,
    val pinned: Boolean = false,
)

/** 一覧レスポンス。ピン止め済みと通常に分けて返す。 */
data class TodoListResponse(
    val pinned: List<Todo>,
    val todos: List<Todo>,
)

@RestController
@RequestMapping("/todos")
class TodoController {

    private val store = ConcurrentHashMap<Long, Todo>()
    private val seq = AtomicLong(0)

    /** 一覧取得。 */
    @GetMapping
    fun list(): TodoListResponse {
        val snapshot = store.values.toList().sortedBy { it.id }
        return TodoListResponse(
            pinned = snapshot.filter { it.pinned },
            todos = snapshot.filter { !it.pinned },
        )
    }

    /** 統計情報取得。 */
    @GetMapping("/stats")
    fun stats(): TodoStats =
        TodoStats(pendingCount = store.values.count { !it.done })

    /** 1件取得。 */
    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<Todo> =
        store[id]?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    /** 作成。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody req: TodoRequest): Todo {
        val id = seq.incrementAndGet()
        val todo = Todo(id = id, title = req.title, done = req.done, pinned = req.pinned)
        store[id] = todo
        return todo
    }

    /** 更新。 */
    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody req: TodoRequest): ResponseEntity<Todo> {
        if (!store.containsKey(id)) return ResponseEntity.notFound().build()
        val updated = Todo(id = id, title = req.title, done = req.done, pinned = req.pinned)
        store[id] = updated
        return ResponseEntity.ok(updated)
    }

    /** ピン状態のトグル。 */
    @PatchMapping("/{id}/pin")
    fun togglePin(@PathVariable id: Long): ResponseEntity<Todo> {
        val todo = store[id] ?: return ResponseEntity.notFound().build()
        val updated = todo.copy(pinned = !todo.pinned)
        store[id] = updated
        return ResponseEntity.ok(updated)
    }

    /** 削除。 */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> =
        if (store.remove(id) != null) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
}
