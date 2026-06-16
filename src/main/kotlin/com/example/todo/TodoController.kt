package com.example.todo

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** TODO 1件を表すデータ。 */
data class Todo(
    val id: Long,
    val title: String,
    val done: Boolean = false,
)

/** 作成・更新リクエストのボディ。 */
data class TodoRequest(
    val title: String,
    val done: Boolean = false,
)

@RestController
@RequestMapping("/todos")
class TodoController {

    private val store = ConcurrentHashMap<Long, Todo>()
    private val seq = AtomicLong(0)

    /** 一覧取得。 */
    @GetMapping
    fun list(): List<Todo> = store.values.sortedBy { it.id }

    /** 1件取得。 */
    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<Todo> =
        store[id]?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    /** 作成。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody req: TodoRequest): Todo {
        val id = seq.incrementAndGet()
        val todo = Todo(id = id, title = req.title, done = req.done)
        store[id] = todo
        return todo
    }

    /** 更新。 */
    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody req: TodoRequest): ResponseEntity<Todo> {
        if (!store.containsKey(id)) return ResponseEntity.notFound().build()
        val updated = Todo(id = id, title = req.title, done = req.done)
        store[id] = updated
        return ResponseEntity.ok(updated)
    }

    /** 削除。 */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> =
        if (store.remove(id) != null) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
}
