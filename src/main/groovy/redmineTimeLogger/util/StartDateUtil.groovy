package redmineTimeLogger.util

import redmineTimeLogger.Constants
import redmineTimeLogger.domain.Day

import java.time.DayOfWeek
import java.time.LocalDate

final class StartDateUtil {

  static LocalDate calculateStartDate(final String startDateParam) {
    if (!startDateParam) {
      LocalDate date = LocalDate.now()

      if (date.getDayOfWeek() == DayOfWeek.MONDAY) {
        date = date.minusDays(3)
      } else {
        date = date.minusDays(1)
      }
      return date
    }
    LocalDate.parse(startDateParam, Constants.DATE_FORMATTER)
  }

  static LocalDate reCalculateStartDate(final List<Day> days, final LocalDate startDate, final boolean onlyLastWorkday) {
    if (onlyLastWorkday) {
      return startDate
    }

    final LocalDate today = LocalDate.now()

    LocalDate firstMissingDate = startDate
    while (firstMissingDate in days*.date || !DateUtil.isWorkDay(firstMissingDate)) {
      firstMissingDate = firstMissingDate.plusDays(1)
    }

    final LocalDate firstDayWithNotFullTime = days?.findAll { final d ->
      d.sum < 7.5 && DateUtil.isWorkDay(d.date)
    }?.min { it.date }?.date

    final LocalDate newStartDate
    if (firstDayWithNotFullTime) {
      newStartDate = [firstMissingDate, firstDayWithNotFullTime].min()
    } else if (firstMissingDate <= today) {
      newStartDate = firstMissingDate
    } else {
      newStartDate = null
    }

    newStartDate
  }
}
