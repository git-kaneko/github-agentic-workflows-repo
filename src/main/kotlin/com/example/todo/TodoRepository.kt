package com.example.todo

import org.springframework.data.jpa.repository.JpaRepository

/** TODO の永続化リポジトリ。基本的な CRUD は JpaRepository が提供する。 */
interface TodoRepository : JpaRepository<Todo, Long> {

    /** 未完了（done = false）の件数を返す（派生クエリ）。 */
    fun countByDoneFalse(): Long
}
