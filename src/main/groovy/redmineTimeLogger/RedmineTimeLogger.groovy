package redmineTimeLogger


import com.taskadapter.redmineapi.RedmineManager
import com.taskadapter.redmineapi.RedmineManagerFactory
import com.taskadapter.redmineapi.bean.Issue
import com.taskadapter.redmineapi.bean.TimeEntry
import redmineTimeLogger.domain.Day
import redmineTimeLogger.domain.RedmineUserActivity
import redmineTimeLogger.redmineConnector.IssueConnector
import redmineTimeLogger.redmineConnector.TimeEntryConnector
import redmineTimeLogger.util.DateUtil
import redmineTimeLogger.util.RedmineUserActivityUtil
import redmineTimeLogger.util.StartDateUtil

import java.time.DayOfWeek
import java.time.LocalDate

class RedmineTimeLogger {

  private boolean onlyLastWorkday
  private LocalDate startDate

  void run(final Map<String, Object> config) {
    final RedmineManager redmineManager = RedmineManagerFactory.createWithApiKey(config['uri'] as String, config['apiKey'] as String)
    final Integer currentUserId = redmineManager.userManager.currentUser.id
    final TimeEntryConnector timeEntryConnector = new TimeEntryConnector(redmineManager, currentUserId)
    final IssueConnector issueConnector = new IssueConnector(redmineManager, config['projects'] as List)
    final String startDateParam = config['startDate']
    onlyLastWorkday = !startDateParam

    startDate = StartDateUtil.calculateStartDate(startDateParam)

    final Map<LocalDate, List<TimeEntry>> timeEntriesByDate = timeEntryConnector.processTrackedTimeEntries(startDate)
    final List<Day> days = timeEntriesByDate.collect { final k, final v -> new Day(k, v*.issueId.toSet(), (v*.hours.sum() as Float).round(2)) }
    startDate = StartDateUtil.reCalculateStartDate(days, startDate, onlyLastWorkday)
    if (!startDate) {
      return
    }

    final Map<String, Integer> issueStatuses = issueConnector.getIssueStatusNamesWithId()
    final Set<Integer> excludedIssueStatuses = issueStatuses
        .findAll { (config['excludedIssueStatusesInJournal'] as List).contains(it.key) }.values()
//    final Integer newIssueStatus = issueStatuses.get(config['newIssueStatusName'] as String)

//    final IssueProducer issueProducer = new IssueProducer(issueConnector, newIssueStatus)
    final List<RedmineUserActivity> userActivities = []
    printf("Processing issues from %s\n", startDate)
    final List<Integer> issueIds = issueConnector.getIssueIds(DateUtil.getUpdateOnParam(startDate))
    issueIds.eachWithIndex { final Integer issueId, final Integer index ->
      final Issue issue = issueConnector.getDetailedIssueById(issueId)
      userActivities.addAll(RedmineUserActivityUtil.collectUserActivities(issue, startDate, currentUserId, excludedIssueStatuses))
      if ((index + 1) % 10 == 0 || index + 1 == issueIds.size()) {
        printf("\r${index + 1}/${issueIds.size()} issues processed")
      }
    }
    printf("\r")
//    Map<Integer, Integer> parentIdsByIssueIds = issueProducer.findParentIdsForNewIssues(userActivities.collect { it.issueId }, issues)
//    List<RedmineUserActivity> resolvedUserActivities = RedmineUserActivityUtil.moveActivitiesToParentForNewIssues(userActivities, parentIdsByIssueIds)
    final List<RedmineUserActivity> resolvedUserActivities = userActivities

    final Map<String, Integer> activityIdsByName = timeEntryConnector.activityNamesWithId
    final Integer generalActivityId = activityIdsByName.get(config['generalActivity'])
    resolvedUserActivities.each { it.activityId = generalActivityId }

    resolvedUserActivities.addAll(collectAdditionalUserActivities(config, activityIdsByName))

    final List<LocalDate> fullTimeDays = days.findAll { it.sum >= 7.5 }.collect { it.date }
    final def entriesToRecord = resolvedUserActivities.findAll {
      !fullTimeDays.contains(it.date)
          && !days.find { final d -> d.date == it.date }?.issues?.contains(it.issueId)
    }
    timeEntryConnector.createTimeEntries(entriesToRecord)
  }

  private List<RedmineUserActivity> collectAdditionalUserActivities(final Map<String, Object> config, final Map<String, Integer> activityIdsByName) {
    (config['additionalActivities'] as List).collectMany { final additonalActivity ->
      final String day = additonalActivity['day'] as String
      createAdditionalUserActivity(
          additonalActivity['issue'] as Integer,
          additonalActivity['hours'] as Float,
          activityIdsByName[additonalActivity['activity']] as Integer,
          day)
    }
  }

  private List<RedmineUserActivity> createAdditionalUserActivity(final Integer issueId, final Float hours, final Integer activityId, final String day) {
    LocalDate date = startDate
    final LocalDate endDate = LocalDate.now()
    final List<RedmineUserActivity> meetingTimeEntries = []
    final DayOfWeek dayOfWeek = day ? DayOfWeek.valueOf(day.toUpperCase()) : null
    while (date <= endDate) {
      if (DateUtil.isWorkDay(date) && (!dayOfWeek || date.getDayOfWeek() == dayOfWeek)) {
        RedmineUserActivity userActivity = new RedmineUserActivity(issueId, date, hours, activityId)
        meetingTimeEntries.add(userActivity)
      }
      date = date.plusDays(1)
    }
    meetingTimeEntries
  }

}
