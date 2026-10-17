package com.hozefa.threepattipro.nativeapp;
import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;
public class DateRangeTest {
    private long at(String value){return Instant.parse(value).toEpochMilli();}
    @Test public void yesterdayAcrossSpringDstIs23Hours(){DateRange r=DateRange.of("yesterday",Instant.parse("2026-03-09T15:00:00Z"),ZoneId.of("America/New_York"),0,0);assertEquals(23*3600000L,r.end-r.start);assertTrue(r.contains(r.start));assertFalse(r.contains(r.end));}
    @Test public void customFallDstInclusiveEndIs25Hours(){ZoneId zone=ZoneId.of("America/New_York");long day=LocalDate.of(2026,11,1).atStartOfDay(zone).toInstant().toEpochMilli();DateRange r=DateRange.of("custom",Instant.now(),zone,day,day);assertEquals(25*3600000L,r.end-r.start);}
    @Test public void monthExcludesFutureMonths(){DateRange r=DateRange.of("month",Instant.parse("2026-10-17T12:00:00Z"),ZoneOffset.UTC,0,0);assertTrue(r.contains(at("2026-10-31T23:59:59Z")));assertFalse(r.contains(at("2026-11-01T00:00:00Z")));assertFalse(r.contains(at("2026-09-30T23:59:59Z")));}
    @Test public void yearExcludesNextYear(){DateRange r=DateRange.of("year",Instant.parse("2026-10-17T12:00:00Z"),ZoneOffset.UTC,0,0);assertFalse(r.contains(at("2027-01-01T00:00:00Z")));}
    @Test public void weekStartsSunday(){DateRange r=DateRange.of("week",Instant.parse("2026-10-17T12:00:00Z"),ZoneOffset.UTC,0,0);assertEquals(at("2026-10-11T00:00:00Z"),r.start);}
    @Test public void endOnlyCustomMatchesWebUnbounded(){DateRange r=DateRange.of("custom",Instant.now(),ZoneOffset.UTC,0,at("2026-10-17T00:00:00Z"));assertTrue(r.contains(at("2026-11-01T00:00:00Z")));}
}
