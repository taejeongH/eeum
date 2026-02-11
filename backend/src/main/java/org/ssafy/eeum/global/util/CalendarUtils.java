package org.ssafy.eeum.global.util;

import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.ChineseCalendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 날짜 및 시간 계산을 위한 유틸리티 클래스입니다.
 * 음력-양력 변환 등의 기능을 제공합니다.
 * 
 * @summary 날짜 유틸리티
 */
public class CalendarUtils {

    /**
     * 음력 날짜를 양력 날짜로 변환합니다.
     * 
     * @summary 음력-양력 변환
     * @param lunarDate 변환할 음력 날짜
     * @return 변환된 양력 날짜
     */
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