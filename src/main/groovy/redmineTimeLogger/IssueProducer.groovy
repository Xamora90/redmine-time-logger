package redmineTimeLogger

import com.taskadapter.redmineapi.bean.Issue
import redmineTimeLogger.redmineConnector.IssueConnector

class IssueProducer {

  private IssueConnector issueConnector
  private Integer newIssueStatus

  IssueProducer(final IssueConnector issueConnector, final Integer newIssueStatus) {
    this.issueConnector = issueConnector
    this.newIssueStatus = newIssueStatus
  }

  Map<Integer, Integer> findParentIdsForNewIssues(final List<Integer> issueIds, final List<Issue> issues) {
    final Map<Integer, Issue> issuesById = issues.collectEntries { final it -> [it.id, it] }
    final Map<Integer, Integer> issueIdMap = issueIds
        .collect { issuesById.get(it) }
        .findAll { it.statusId == newIssueStatus }
        .collectEntries { final issue -> [issue.id, issue.id] }
    if (issueIdMap.isEmpty()) {
      return [:]
    }
    findParentIds(issueIdMap, issuesById, [:])
  }

  private Map<Integer, Integer> findParentIds(final Map<Integer, Integer> parentIdsByIssueId,
                                              final Map<Integer, Issue> issues,
                                              final Map<Integer, Integer> foundParentIdsByIssueId) {
    if (parentIdsByIssueId.isEmpty()) {
      return foundParentIdsByIssueId
    }
    final Map<Integer, Issue> issuesById = new HashMap<>(issues)

    issuesById.putAll(issueConnector.getIssuesByIds(parentIdsByIssueId.values().findAll { !issuesById.containsKey(it) }))

    final Map<Integer, Integer> mapToProcess = [:]
    parentIdsByIssueId
        .findAll { final issueId, final parentId -> issuesById.get(parentId)?.statusId == newIssueStatus }
        .each { final issueId, final parentId ->
          {
            final Integer parentParentId = getParent(issuesById, issuesById.get(parentId).parentId)
            if (!parentParentId) {
              foundParentIdsByIssueId.put(issueId, parentId)
            } else if (issuesById.containsKey(parentParentId)) {
              foundParentIdsByIssueId.put(issueId, parentParentId)
            } else {
              mapToProcess.put(issueId, parentParentId)
            }
          }
        }
    findParentIds(mapToProcess, issuesById, foundParentIdsByIssueId)
  }

  private Integer getParent(final Map<Integer, Issue> issues, final Integer parentId) {
    final Issue parentIssue = issues.get(parentId)
    if (!parentIssue || parentIssue.statusId != newIssueStatus || !parentIssue.parentId) {
      return parentId
    } else {
      getParent(issues, parentIssue.parentId)
    }
  }
}
