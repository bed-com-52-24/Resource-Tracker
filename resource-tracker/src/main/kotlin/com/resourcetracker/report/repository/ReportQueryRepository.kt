package com.resourcetracker.report.repository

import com.resourcetracker.department.Department
import com.resourcetracker.report.service.ProcurementRow
import com.resourcetracker.request.entity.ProcurementStatus
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class ReportQueryRepository(
    @PersistenceContext private val em: EntityManager
) {

    fun procurementRows(
        departmentId: Int?
    ): List<ProcurementRow> {

        val jpql = """
            SELECT p.requestId,
                   r.departmentId,
                   p.status,
                   p.estimatedCost,
                   p.authorizedAt,
                   p.receivedAt
            FROM Procurement p, ResourceRequest r
            WHERE p.requestId = r.requestId
              AND p.status IN :statuses
        """.trimIndent() +
            if (departmentId != null) {
                " AND r.departmentId = :departmentId"
            } else {
                ""
            }

        val query = em.createQuery(jpql)
            .setParameter("statuses", REPORTABLE_STATUSES)

        if (departmentId != null) {
            query.setParameter("departmentId", departmentId)
        }

        return query.resultList.map { result ->

            val row = result as Array<*>

            ProcurementRow(
                requestId = (row[0] as Number).toInt(),
                departmentId = (row[1] as Number).toInt(),
                status = row[2] as ProcurementStatus,
                cost = row[3] as? BigDecimal ?: BigDecimal.ZERO,
                authorizedAt = row[4] as? LocalDateTime,
                receivedAt = row[5] as? LocalDateTime
            )
        }
    }

    fun departmentNames(): Map<Int, String> {

        return em.createQuery(
            "SELECT d.id, d.name FROM Department d"
        ).resultList.associate { result ->

            val row = result as Array<*>

            (row[0] as Number).toInt() to
                (row[1] as String)
        }
    }

    fun departmentExists(id: Int): Boolean {
        return em.find(Department::class.java, id) != null
    }

    companion object {

        private val REPORTABLE_STATUSES = listOf(
            ProcurementStatus.AUTHORISED,
            ProcurementStatus.ORDERED,
            ProcurementStatus.RECEIVED,
            ProcurementStatus.ACCEPTED
        )
    }
}