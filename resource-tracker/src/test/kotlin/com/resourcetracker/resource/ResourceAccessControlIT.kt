package com.resourcetracker.resource

import com.resourcetracker.testsupport.JwtTestTokens
import com.resourcetracker.testsupport.MySqlIntegrationTest
import com.resourcetracker.testsupport.TestDataBuilders
import com.resourcetracker.resource.ResourceCategory
import com.resourcetracker.user.Role
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ResourceAccessControlIT : MySqlIntegrationTest() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `resource visibility and stock changes follow role and department scope`() {
        val it = departments.saveAndFlush(TestDataBuilders.department("Integration IT"))
        val finance = departments.saveAndFlush(TestDataBuilders.department("Integration Finance"))
        val ownResource = resources.saveAndFlush(TestDataBuilders.internalResource("IT-TEST-1", "IT laptop", it, 10))
        val outOfStock = resources.saveAndFlush(TestDataBuilders.internalResource("IT-TEST-2", "Spare monitor", it, 0))
        val foreignResource = resources.saveAndFlush(TestDataBuilders.internalResource("FIN-TEST-1", "Finance printer", finance, 6))
        val sharedExternal = resources.saveAndFlush(TestDataBuilders.externalResource("EXT-TEST-1", "Design software license"))

        val adminToken = tokenFor(Role.ADMIN, it, "it-admin")
        val memberToken = tokenFor(Role.MEMBER, it, "it-member")
        val teamLeadToken = tokenFor(Role.TEAM_LEAD, it, "it-lead")
        val managerToken = tokenFor(Role.DEPARTMENT_MANAGER, it, "it-manager")
        val financeManagerToken = tokenFor(Role.DEPARTMENT_MANAGER, finance, "finance-manager")
        val directorToken = tokenFor(Role.MANAGING_DIRECTOR, finance, "managing-director")

        mockMvc.perform(get("/api/v1/resources/${ownResource.id}")
            .header("Authorization", JwtTestTokens.bearer(managerToken)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(ownResource.id))
            .andExpect(jsonPath("$.availableQuantity").value(10))

        mockMvc.perform(get("/api/v1/resources/${foreignResource.id}")
            .header("Authorization", JwtTestTokens.bearer(managerToken)))
            .andExpect(status().isNotFound)

        mockMvc.perform(get("/api/v1/resources/${ownResource.id}")
            .header("Authorization", JwtTestTokens.bearer(teamLeadToken)))
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/resources/${foreignResource.id}")
            .header("Authorization", JwtTestTokens.bearer(teamLeadToken)))
            .andExpect(status().isNotFound)

        mockMvc.perform(get("/api/v1/resources/${foreignResource.id}")
            .header("Authorization", JwtTestTokens.bearer(directorToken)))
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/resources/${sharedExternal.id}")
            .header("Authorization", JwtTestTokens.bearer(managerToken)))
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/resources")
            .header("Authorization", JwtTestTokens.bearer(managerToken))
            .param("availableOnly", "true")
            .param("page", "0")
            .param("size", "10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(ownResource.id)))

        val createBody = """
            {
              "name": "Other department stock",
              "type": "INTERNAL",
              "category": "${ResourceCategory.EQUIPMENT}",
              "unitOfMeasure": "unit",
              "quantity": 2,
              "departmentId": ${finance.id}
            }
        """.trimIndent()
        mockMvc.perform(post("/api/v1/resources")
            .header("Authorization", JwtTestTokens.bearer(managerToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody))
            .andExpect(status().isNotFound)

        mockMvc.perform(post("/api/v1/resources")
            .header("Authorization", JwtTestTokens.bearer(teamLeadToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody))
            .andExpect(status().isForbidden)

        mockMvc.perform(post("/api/v1/resources/${foreignResource.id}/corrections")
            .header("Authorization", JwtTestTokens.bearer(managerToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"newQuantity":5,"reason":"foreign stock must be denied"}"""))
            .andExpect(status().isNotFound)

        mockMvc.perform(post("/api/v1/resources/${ownResource.id}/corrections")
            .header("Authorization", JwtTestTokens.bearer(managerToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"newQuantity":8,"reason":"cycle count"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resourceId").value(ownResource.id))
            .andExpect(jsonPath("$.quantityChange").value(-2))
            .andExpect(jsonPath("$.reason").value("cycle count"))
        assertEquals(2, stockMovements.findAllByResourceIdOrderByCreatedAtAsc(ownResource.id).size)

        mockMvc.perform(get("/api/v1/resources/${ownResource.id}")
            .header("Authorization", JwtTestTokens.bearer(memberToken)))
            .andExpect(status().isForbidden)

        mockMvc.perform(get("/api/v1/resources/${ownResource.id}")
            .header("Authorization", JwtTestTokens.bearer(adminToken)))
            .andExpect(status().isForbidden)

        mockMvc.perform(post("/api/v1/resources/${ownResource.id}/corrections")
            .header("Authorization", JwtTestTokens.bearer(financeManagerToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"newQuantity":5,"reason":"other department"}"""))
            .andExpect(status().isNotFound)
    }
}
