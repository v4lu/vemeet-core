package com.vemeet.backend.controller

import com.vemeet.backend.dto.CategoryRequest
import com.vemeet.backend.dto.CategoryResponse
import com.vemeet.backend.dto.CreateRecipeRequest
import com.vemeet.backend.dto.RecipeResponse
import com.vemeet.backend.exception.NotAllowedException
import com.vemeet.backend.service.RecipeService
import com.vemeet.backend.utils.CognitoIdExtractor
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/v1/recipes")
@Tag(name = "Recipe", description = "Recipe management APIs")
class RecipeController(private val recipeService: RecipeService) {

    @PostMapping
    @Operation(summary = "Create a new recipe", description = "Creates a new recipe for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Recipe created successfully", content = [Content(schema = Schema(implementation = RecipeResponse::class))])
    fun createRecipe(
        @RequestBody request: CreateRecipeRequest,
        authentication: Authentication,

        ): ResponseEntity<RecipeResponse> {
        val cognitoId = CognitoIdExtractor.extractCognitoId(authentication)  ?: throw NotAllowedException("Not valid token")
        val recipe = recipeService.createRecipe(request, cognitoId)
        return ResponseEntity.ok(recipe)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a recipe by ID", description = "Retrieves a recipe by its ID")
    @ApiResponse(responseCode = "200", description = "Recipe found", content = [Content(schema = Schema(implementation = RecipeResponse::class))])
    @ApiResponse(responseCode = "404", description = "Recipe not found")
    fun getRecipe(@PathVariable id: Long): ResponseEntity<RecipeResponse> {
        val recipe = recipeService.getRecipe(id)
        return ResponseEntity.ok(recipe)
    }

    @GetMapping
    @Operation(summary = "Get all recipes", description = "Retrieves all recipes with pagination")
    @ApiResponse(responseCode = "200", description = "Recipes found", content = [Content(schema = Schema(implementation = Page::class))])
    fun getAllRecipes(pageable: Pageable): ResponseEntity<Page<RecipeResponse>> {
        val recipes = recipeService.getAllRecipes(pageable)
        return ResponseEntity.ok(recipes)
    }

    @GetMapping("/categories")
    @Operation(summary = "Get all categories")
    @ApiResponse(responseCode = "200", description = "Recipes found", content = [Content(schema = Schema(implementation = CategoryResponse::class))])
    fun getAllCategories(): ResponseEntity<List<CategoryResponse>> {
        val recipes = recipeService.getCategories()
        return ResponseEntity.ok(recipes)
    }

    @PostMapping("/categories")
    @Operation(summary = "Create new category")
    @ApiResponse(responseCode = "200", description = "successfully created new category", content = [Content(schema = Schema(implementation = CategoryResponse::class))])
    fun createCategory(@RequestBody request: CategoryRequest) : ResponseEntity<CategoryResponse> {
        val category = recipeService.createCategory(request)
        return ResponseEntity.ok(category)
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a recipe", description = "Deletes an existing recipe")
    @ApiResponse(responseCode = "204", description = "Recipe deleted successfully")
    @ApiResponse(responseCode = "404", description = "Recipe not found")
    @ApiResponse(responseCode = "403", description = "Not allowed to delete this recipe")
    fun deleteRecipe(@PathVariable id: Long, authentication: Authentication): ResponseEntity<Unit> {
        val cognitoId = CognitoIdExtractor.extractCognitoId(authentication)  ?: throw NotAllowedException("Not valid token")
        recipeService.deleteRecipe(id, cognitoId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/search")
    @Operation(summary = "Search recipes", description = "Search recipes with various filters and pagination")
    @ApiResponse(responseCode = "200", description = "Successful search", content = [Content(schema = Schema(implementation = Page::class))])
    fun searchRecipes(
        @Parameter(description = "Search by title") @RequestParam title: String?,
        @Parameter(description = "Filter by category ID") @RequestParam categoryId: Long?,
        @Parameter(description = "Filter by tag ID") @RequestParam tagId: Long?,
        @Parameter(description = "Filter by difficulty") @RequestParam difficulty: String?,
        @Parameter(description = "Minimum number of servings") @RequestParam minServings: Int?,
        @Parameter(description = "Maximum number of servings") @RequestParam maxServings: Int?,
        @Parameter(description = "Created after date")
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) createdAfter: Instant?,
        @Parameter(description = "Created before date")
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) createdBefore: Instant?,
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") sort: String,
        @Parameter(description = "Sort direction") @RequestParam(defaultValue = "DESC") direction: Sort.Direction
    ): ResponseEntity<Page<RecipeResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by(direction, sort))
        val recipes = recipeService.findAllRecipes(
            title, categoryId, tagId, difficulty, minServings, maxServings,
            createdAfter, createdBefore, pageable
        )
        return ResponseEntity.ok(recipes)
    }
}