package com.hozefa.threepattipro.nativeapp;
import java.time.*;
/** HH calendar filters, using local day boundaries rather than fixed 24-hour intervals. */
public final class DateRange {
    public final long start,end;
    private DateRange(long start,long end){this.start=start;this.end=end;}
    public boolean contains(long timestamp){return timestamp>=start&&timestamp<end;}
    private static long day(LocalDate date,ZoneId zone){return date.atStartOfDay(zone).toInstant().toEpochMilli();}
    public static DateRange of(String filter,Instant now,ZoneId zone,long customStart,long customEnd){
        LocalDate today=now.atZone(zone).toLocalDate();long min=Long.MIN_VALUE,max=Long.MAX_VALUE;
        switch(filter){
            case "today":min=day(today,zone);break;
            case "yesterday":min=day(today.minusDays(1),zone);max=day(today,zone);break;
            case "week":min=day(today.minusDays(today.getDayOfWeek().getValue()%7),zone);break;
            case "month":min=day(today.withDayOfMonth(1),zone);max=day(today.withDayOfMonth(1).plusMonths(1),zone);break;
            case "year":min=day(today.withDayOfYear(1),zone);max=day(today.withDayOfYear(1).plusYears(1),zone);break;
            case "custom":if(customStart>0){min=customStart;if(customEnd>0)max=day(Instant.ofEpochMilli(customEnd).atZone(zone).toLocalDate().plusDays(1),zone);}break;
        }
        return new DateRange(min,max);
    }
}
