package com.resourcetracker.auth

import com.resourcetracker.user.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import tools.jackson.databind.json.JsonMapper

class AuthControllerTest {
    @Test
    fun `login response contains only the five token fields`() {
        val auth = Mockito.mock(AuthService::class.java)
        val users = Mockito.mock(UserRepository::class.java)
        val request = LoginRequest("member", "password")
        val tokens = TokenPair(
            accessToken = "access",
            refreshToken = "refresh",
            accessTokenExpiresIn = 1800,
            refreshTokenExpiresIn = 604800,
        )
        Mockito.`when`(auth.login(request)).thenReturn(tokens)

        val response = AuthController(auth, users).login(request)
        val json = JsonMapper.builder().build().writeValueAsString(response)
        val body = JsonMapper.builder().build().readValue(json, Map::class.java) as Map<*, *>

        assertEquals(
            setOf("accessToken", "refreshToken", "tokenType", "accessTokenExpiresIn", "refreshTokenExpiresIn"),
            body.keys,
        )
        assertFalse(body.containsKey("user"))
        assertEquals(1800, body["accessTokenExpiresIn"])
    }
}
