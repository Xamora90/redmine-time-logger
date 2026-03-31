package redmineTimeLogger.redmineConnector

import com.taskadapter.redmineapi.RedmineManager
import com.taskadapter.redmineapi.TimeEntryManager
import com.taskadapter.redmineapi.bean.TimeEntry
import com.taskadapter.redmineapi.internal.ResultsWrapper
import com.taskadapter.redmineapi.internal.Transport
import redmineTimeLogger.Constants
import redmineTimeLogger.domain.RedmineUserActivity
import redmineTimeLogger.util.DateUtil

import java.time.LocalDate
import java.time.ZoneId

class TimeEntryConnector {

  private TimeEntryManager timeEntryManager
  private Transport transport
  Integer currentUserId

  TimeEntryConnector(final RedmineManager redmineManager, final Integer currentUserId) {
    timeEntryManager = redmineManager.timeEntryManager
    transport = redmineManager.transport
    this.currentUserId = currentUserId
  }

  Map<String, Integer> getActivityNamesWithId() {
    timeEntryManager.getTimeEntryActivities().collectEntries { [it.name, it.id] }
  }

  Map<LocalDate, List<TimeEntry>> processTrackedTimeEntries(final LocalDate startDate) {
    final Map params = [
        'user_id': currentUserId as String,
        'from'   : startDate.format(Constants.DATE_FORMATTER),
        'limit'  : '100'
    ]
    final List<TimeEntry> timeEntries = []
    int offset = 0
    final ResultsWrapper<TimeEntry> results
    do {
      final Map newParams = new HashMap(params)
      newParams.put('offset', offset as String)
      results = timeEntryManager.getTimeEntries(newParams)
      timeEntries.addAll(results.results)
      offset += timeEntries.size()
    } while (results.totalFoundOnServer > offset)
    timeEntries?.groupBy {
      DateUtil.convertToLocalDate(it.spentOn)
    }
  }

  void createTimeEntries(final List<RedmineUserActivity> userActivities) {
    userActivities.each {
      final TimeEntry timeEntry = createTimeEntry(it)
      println("Creating time entry for issue ${it.issueId} on ${it.date} with ${it.hours} hours")
      timeEntryManager.createTimeEntry(timeEntry)
    }
  }

  private TimeEntry createTimeEntry(final RedmineUserActivity userActivity) {
    final TimeEntry timeEntry = new TimeEntry(transport)
    timeEntry.setIssueId(userActivity.issueId)
    timeEntry.setUserId(currentUserId)
    timeEntry.setActivityId(userActivity.activityId)
    timeEntry.setHours(userActivity.hours)
    timeEntry.setSpentOn(Date.from(userActivity.date.atStartOfDay(ZoneId.systemDefault()).toInstant()))
    timeEntry
  }
}
