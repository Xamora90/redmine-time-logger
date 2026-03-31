package redmineTimeLogger.domain

import groovy.transform.TupleConstructor

import java.time.LocalDate

@TupleConstructor
class RedmineUserActivity {
  Integer issueId
  LocalDate date
  Float hours
  Integer activityId
}
