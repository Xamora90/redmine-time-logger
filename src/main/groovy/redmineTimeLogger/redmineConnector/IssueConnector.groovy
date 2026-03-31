package redmineTimeLogger.redmineConnector

import com.taskadapter.redmineapi.Include
import com.taskadapter.redmineapi.IssueManager
import com.taskadapter.redmineapi.RedmineManager
import com.taskadapter.redmineapi.bean.Issue
import com.taskadapter.redmineapi.internal.ResultsWrapper

class IssueConnector {

  private IssueManager issueManager
  Map<Integer, String> projects

  IssueConnector(final RedmineManager redmineManager, final List<String> projectNames) {
    issueManager = redmineManager.getIssueManager()
    projects = redmineManager.projectManager.projects
        .findAll { projectNames.contains(it.name) }
        .collectEntries { [(it.id): it.name] }
  }

  Map<String, Integer> getIssueStatusNamesWithId() {
    issueManager.statuses.collectEntries { [it.name, it.id] }
  }

  Issue getDetailedIssueById(final Integer issueId) {
    issueManager.getIssueById(issueId, Include.journals, Include.changesets)
  }

  Map<Integer, Issue> getIssuesByIds(final Collection<Integer> issueIds) {
    if (issueIds.isEmpty()) {
      return [:]
    }

    final Map params = [
        'issue_id' : issueIds.join(','),
        'status_id': '*',
        "limit"    : issueIds.size() as String
    ]
    issueManager.getIssues(params).results?.collectEntries { final issue -> [issue.id, issue] } ?: [:]
  }

  List<Integer> getIssueIds(final String updatedOn) {
    final Map defaultParams = [
        'status_id' : '*',
        'updated_on': updatedOn,
        'limit'     : '100'
    ]
    final List<Integer> resultIssueIds = []
    projects.each { final projectId, final projectName ->
      int offset = 0
      ResultsWrapper<Issue> results
      printf("\r")
      printf("\rLooking for issues in project '${projectName}'...")
      do {
        final Map actParams = new HashMap(defaultParams)
        actParams.put('offset', offset as String)
        actParams.put('project_id', projectId as String)
        results = issueManager.getIssues(actParams)
        resultIssueIds.addAll(results.results*.id.asList())
        offset += resultIssueIds.size()
      } while (results.totalFoundOnServer > offset)
    }
    printf("\rFound ${resultIssueIds.size()} issues, processing...")
    resultIssueIds
  }
}
