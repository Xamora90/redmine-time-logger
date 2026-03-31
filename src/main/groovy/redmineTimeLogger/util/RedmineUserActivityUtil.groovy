package redmineTimeLogger.util

import com.taskadapter.redmineapi.bean.Issue
import com.taskadapter.redmineapi.bean.JournalDetail
import redmineTimeLogger.domain.RedmineUserActivity

import java.time.LocalDate

final class RedmineUserActivityUtil {

  static List<RedmineUserActivity> collectUserActivities(final Issue issue, final LocalDate startDate, final Integer currentUserId, final Set<Integer> excludedIssueStatuses) {
    final Map<LocalDate, Integer> changesetsByDate = getChangesetCountByDate(issue, startDate, currentUserId)
    final Map<LocalDate, Integer> journalCountByDate = getJournalCountByDate(issue, startDate, currentUserId, excludedIssueStatuses)
    changesetsByDate.each { final date, final count ->
      {
        final Integer journalCount = journalCountByDate.get(date)
        if (!journalCount || journalCount < count) {
          journalCountByDate.put(date, count)
        }
      }
    }

    journalCountByDate.collect { final date, final count ->
      new RedmineUserActivity(issue.id, date, getActivityDurationHours(count))
    }
  }

  static List<RedmineUserActivity> moveActivitiesToParentForNewIssues(final Collection<RedmineUserActivity> userActivities, final Map<Integer, Integer> parentIdsByIssueId) {
    if (parentIdsByIssueId.isEmpty()) {
      return userActivities
    }

    final List<RedmineUserActivity> resolvedUserActivities = []
    userActivities.each { final userActivity ->
      if (parentIdsByIssueId.containsKey(userActivity.issueId)) {
        userActivity.issueId = parentIdsByIssueId.get(userActivity.issueId)
      } else {
        resolvedUserActivities.add(userActivity)
      }
    }

    userActivities.findAll { final a -> parentIdsByIssueId.containsValue(a.issueId) }
      .groupBy { it.date }.each { final date, final act ->
        act.groupBy { it.issueId }.each { final parent, final val ->
          resolvedUserActivities.add(new RedmineUserActivity(parent, date, val*.hours.sum() as Float))
        }
    }
    resolvedUserActivities
  }

  private static Float getActivityDurationHours(int count) {
    Float hours = count / 4
    return hours >= 0.5 ? hours : 0.5 as Float
  }

  private static Map<LocalDate, Integer> getChangesetCountByDate(final Issue issue, final LocalDate startDate, final Integer currentUserId) {
    issue.changesets
        .findAll { final c -> c.user?.id == currentUserId }
        .countBy { final c -> DateUtil.convertToLocalDate(c.committedOn) }
        .findAll { final date, final count -> !startDate.isAfter(date) }
  }

  private static Map<LocalDate, Integer> getJournalCountByDate(final Issue issue, final LocalDate startDate, final Integer currentUserId, final Set<Integer> excludedIssueStatuses) {
    issue.journals
        .findAll { final j ->
          j.user.id == currentUserId
              && !j.details.isEmpty()
              && !anyDetailIsExcludedStatusOrRelation(j.details, excludedIssueStatuses)
              && !journalIsOnlyAboutAssigningToMe(j.details, currentUserId)
        }
        .countBy { final j -> DateUtil.convertToLocalDate(j.createdOn) }
        .findAll { final date, final count -> !startDate.isAfter(date) }
  }

  private static boolean anyDetailIsExcludedStatusOrRelation(final List<JournalDetail> details, final Set<Integer> excludedIssueStatuses) {
    details.any { final d ->
      (d.name == 'status_id' && excludedIssueStatuses.contains(d.newValue as Integer))
          || d.name in ['relates', 'child_id']
    }
  }

  private static boolean journalIsOnlyAboutAssigningToMe(final List<JournalDetail> details, final Integer currentUserId) {
    if (details.size() != 1 || details[0].name != 'assigned_to_id') {
      return false
    }
    final JournalDetail d = details[0]
    !d.oldValue && d.newValue == currentUserId as String
  }
}
