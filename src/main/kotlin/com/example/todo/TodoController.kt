package com.example.todo

import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 統計情報レスポンス。 */
data class TodoStats(
    val pendingCount: Int,
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
class TodoController(
    private val repository: TodoRepository,
) {

    /** 一覧取得。 */
    @GetMapping
    fun list(): TodoListResponse {
        val snapshot = repository.findAll(Sort.by("id"))
        return TodoListResponse(
            pinned = snapshot.filter { it.pinned },
            todos = snapshot.filter { !it.pinned },
        )
    }

    /** 統計情報取得。 */
    @GetMapping("/stats")
    fun stats(): TodoStats =
        TodoStats(pendingCount = repository.countByDoneFalse().toInt())

    /** 1件取得。 */
    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<Todo> =
        repository.findByIdOrNull(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    /** 作成。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody req: TodoRequest): Todo {
        val todo = Todo(title = req.title, done = req.done, pinned = req.pinned)
        return repository.save(todo)
    }

    /** 更新。 */
    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody req: TodoRequest): ResponseEntity<Todo> {
        val todo = repository.findByIdOrNull(id) ?: return ResponseEntity.notFound().build()
        todo.title = req.title
        todo.done = req.done
        todo.pinned = req.pinned
        return ResponseEntity.ok(repository.save(todo))
    }

    /** ピン状態のトグル。 */
    @PatchMapping("/{id}/pin")
    fun togglePin(@PathVariable id: Long): ResponseEntity<Todo> {
        val todo = repository.findByIdOrNull(id) ?: return ResponseEntity.notFound().build()
        todo.pinned = !todo.pinned
        return ResponseEntity.ok(repository.save(todo))
    }

    /** 削除。 */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> =
        if (repository.existsById(id)) {
            repository.deleteById(id)
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
}
