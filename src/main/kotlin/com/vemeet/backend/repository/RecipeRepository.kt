package com.vemeet.backend.repository

import com.vemeet.backend.model.Difficulty
import com.vemeet.backend.model.Recipe
import io.lettuce.core.dynamic.annotation.Param
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface RecipeRepository : JpaRepository<Recipe, Long> {
     override fun findAll(pageable: Pageable): Page<Recipe>


    @Query("""
        SELECT r FROM Recipe r
        LEFT JOIN r.category c
        LEFT JOIN r.tags t
        WHERE (:title IS NULL OR LOWER(r.title) LIKE LOWER(CONCAT('%', :title, '%')))
        AND (:categoryId IS NULL OR c.id = :categoryId)
        AND (:tagId IS NULL OR :tagId IN (SELECT t.id FROM r.tags t))
        AND (:difficulty IS NULL OR r.difficulty = :difficulty)
        AND (:minServings IS NULL OR r.servings >= :minServings)
        AND (:maxServings IS NULL OR r.servings <= :maxServings)
        AND (:createdAfter IS NULL OR r.createdAt >= :createdAfter)
        AND (:createdBefore IS NULL OR r.createdAt <= :createdBefore)
    """)
    fun findAllWithFilters(
        @Param("title") title: String?,
        @Param("categoryId") categoryId: Long?,
        @Param("tagId") tagId: Long?,
        @Param("difficulty") difficulty: Difficulty?,
        @Param("minServings") minServings: Int?,
        @Param("maxServings") maxServings: Int?,
        @Param("createdAfter") createdAfter: Instant?,
        @Param("createdBefore") createdBefore: Instant?,
        pageable: Pageable
    ): Page<Recipe>
}