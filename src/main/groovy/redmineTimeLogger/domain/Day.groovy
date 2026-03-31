package redmineTimeLogger.domain

import groovy.transform.TupleConstructor

import java.time.LocalDate

@TupleConstructor
class Day {
  LocalDate date
  Set<Integer> issues
  Float sum
}
