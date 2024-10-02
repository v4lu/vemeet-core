package com.vemeet.backend.controller


import com.vemeet.backend.dto.*
import com.vemeet.backend.exception.NotAllowedException
import com.vemeet.backend.service.SwipeService
import com.vemeet.backend.service.UserService
import com.vemeet.backend.utils.CognitoIdExtractor
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/swipes")
@Tag(name = "Swipe", description = "Swipe endpoints")
class SwipeController(
    private val swipeService: SwipeService,
    private val userService: UserService
) {

    @PostMapping
    @Operation(
        summary = "Create a new swipe",
        responses = [
            ApiResponse(
                responseCode = "200", description = "Successfully created swipe",
                content = [Content(schema = Schema(implementation = SwipeResponse::class))]
            ),
            ApiResponse(
                responseCode = "404", description = "User not found",
                content = [Content(schema = Schema(implementation = ExceptionResponse::class))]
            ),
            ApiResponse(
                responseCode = "401", description = "Invalid Credentials",
                content = [Content(schema = Schema(implementation = ExceptionResponse::class))]
            )
        ]
    )
    fun createSwipe(
        authentication: Authentication,
        @RequestBody swipeRequest: SwipeRequest
    ): ResponseEntity<SwipeResponse> {
        val cognitoId = CognitoIdExtractor.extractCognitoId(authentication) ?: throw NotAllowedException("Not valid token")
        val user = userService.getSessionUser(cognitoId)
        val swipeResponse = swipeService.createSwipe(user, swipeRequest)
        return ResponseEntity.ok(swipeResponse)
    }

    @GetMapping("/potential-matches")
    @Operation(
        summary = "Get potential matches for the user",
        responses = [
            ApiResponse(
                responseCode = "200", description = "Successfully retrieved potential matches",
                content = [Content(schema = Schema(implementation = PaginatedPotentialMatches::class))]
            ),
            ApiResponse(
                responseCode = "401", description = "Invalid Credentials",
                content = [Content(schema = Schema(implementation = ExceptionResponse::class))]
            )
        ]
    )
    fun getPotentialMatches(
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "4") size: Int
    ): ResponseEntity<PaginatedPotentialMatches> {
        val cognitoId = CognitoIdExtractor.extractCognitoId(authentication) ?: throw NotAllowedException("Not valid token")
        val user = userService.getSessionUser(cognitoId)
        val paginatedMatches = swipeService.getPotentialMatches(user, page, size)
        return ResponseEntity.ok(paginatedMatches)
    }


    @GetMapping("/matches")
    @Operation(
        summary = "Get all matches for the user",
        responses = [
            ApiResponse(
                responseCode = "200", description = "Successfully retrieved user matches",
                content = [Content(schema = Schema(implementation = List::class))]
            ),
            ApiResponse(
                responseCode = "401", description = "Invalid Credentials",
                content = [Content(schema = Schema(implementation = ExceptionResponse::class))]
            )
        ]
    )
    fun getMatches(authentication: Authentication): ResponseEntity<List<UserResponse>> {
        val cognitoId = CognitoIdExtractor.extractCognitoId(authentication) ?: throw NotAllowedException("Not valid token")
        val user = userService.getSessionUser(cognitoId)
        val matches = swipeService.getMatches(user)
        return ResponseEntity.ok(matches)
    }
}