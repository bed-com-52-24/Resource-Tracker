package com.resourcetracker.request.service

import com.resourcetracker.notification.NotificationService
import com.resourcetracker.notification.NotificationType
import com.resourcetracker.request.entity.StockIssue
import com.resourcetracker.request.repository.ResourceRequestRepository
import com.resourcetracker.request.repository.StockIssueRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReturnReminderService(
    private val issues: StockIssueRepository,
    private val requests: ResourceRequestRepository,
    private val notifications: NotificationService,
    private val clock: Clock,
) {
    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Blantyre")
    @Transactional
    fun sendDueDateReminders() {
        val today = LocalDate.now(clock.withZone(ZoneId.of("Africa/Blantyre")))
        send(issues.findForSevenDayReminder(today.plusDays(7)), NotificationType.RETURN_DUE_7_DAYS) { it.reminder7DaysSent = true }
        send(issues.findForTwoDayReminder(today.plusDays(2)), NotificationType.RETURN_DUE_2_DAYS) { it.reminder2DaysSent = true }
        send(issues.findForDueDayReminder(today), NotificationType.RETURN_DUE_TODAY) { it.reminderDueDaySent = true }
    }

    private fun send(entries: List<StockIssue>, type: NotificationType, markSent: (StockIssue) -> Unit) {
        entries.forEach { issue ->
            val request = requests.findById(issue.requestId).orElseThrow()
            notifications.notify(
                listOfNotNull(request.requesterId, request.assignedManagerId).distinct(),
                type,
                mapOf(
                    "requestId" to issue.requestId,
                    "requestNumber" to request.requestNumber,
                    "issueId" to issue.id,
                    "expectedReturnDate" to issue.expectedReturnDate.toString(),
                ),
            )
            markSent(issue)
            issues.save(issue)
        }
    }
}
