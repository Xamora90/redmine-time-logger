package redmineTimeLogger


import redmineTimeLogger.redmineConnector.TimeEntryConnector

class TimeRecorder {

  private TimeEntryConnector timeEntryConnector

  TimeRecorder(final TimeEntryConnector timeEntryConnector) {
    this.timeEntryConnector = timeEntryConnector
  }


}
