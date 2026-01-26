package org.ssafy.eeum.global.util;

import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.ChineseCalendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class CalendarUtils {
    public static LocalDate convertLunarToSolar(LocalDate lunarDate) {
        ChineseCalendar lunar = new ChineseCalendar();
        lunar.clear();

        lunar.set(Calendar.YEAR, lunarDate.getYear() + 2637);
        lunar.set(Calendar.MONTH, lunarDate.getMonthValue() - 1);
        lunar.set(Calendar.DAY_OF_MONTH, lunarDate.getDayOfMonth());
        lunar.set(ChineseCalendar.IS_LEAP_MONTH, 0);

        return Instant.ofEpochMilli(lunar.getTimeInMillis())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }
}