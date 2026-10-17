package com.hozefa.threepattipro.nativeapp;

import org.junit.Test;
import static org.junit.Assert.*;

public class MotionPolicyTest {
    @Test public void defaultAndUnknownPreferenceUseFull() {
        assertEquals(MotionPolicy.Mode.FULL,MotionPolicy.resolve("full",1,1,1));
        assertEquals(MotionPolicy.Mode.FULL,MotionPolicy.resolve("future-mode",1,1,1));
    }
    @Test public void explicitReducedAndOff() {
        assertEquals(MotionPolicy.Mode.REDUCED,MotionPolicy.resolve("reduced",1,1,1));
        assertEquals(MotionPolicy.Mode.OFF,MotionPolicy.resolve("off",1,1,1));
    }
    @Test public void everyDisabledSystemAnimationScaleOverridesFull() {
        assertEquals(MotionPolicy.Mode.OFF,MotionPolicy.resolve("full",0,1,1));
        assertEquals(MotionPolicy.Mode.OFF,MotionPolicy.resolve("full",1,0,1));
        assertEquals(MotionPolicy.Mode.OFF,MotionPolicy.resolve("full",1,1,0));
        assertEquals(MotionPolicy.Mode.OFF,MotionPolicy.resolve("reduced",1,1,0));
    }
    @Test public void invalidSystemScalesFailToStaticUi() {
        assertEquals(MotionPolicy.Mode.OFF,MotionPolicy.resolve("full",Float.NaN,1,1));
        assertEquals(MotionPolicy.Mode.OFF,MotionPolicy.resolve("full",1,Float.POSITIVE_INFINITY,1));
        assertEquals(MotionPolicy.Mode.OFF,MotionPolicy.resolve("full",1,1,-1));
    }
    @Test public void reducedDurationIsShortAndOffIsImmediate() {
        assertEquals(220,MotionPolicy.duration(MotionPolicy.Mode.FULL,220));
        assertEquals(120,MotionPolicy.duration(MotionPolicy.Mode.REDUCED,220));
        assertEquals(90,MotionPolicy.duration(MotionPolicy.Mode.REDUCED,90));
        assertEquals(0,MotionPolicy.duration(MotionPolicy.Mode.OFF,500));
    }
    @Test public void integerTransitionsAreClampedAndOverflowSafe() {
        assertEquals(Integer.MIN_VALUE,MotionPolicy.interpolate(Integer.MIN_VALUE,Integer.MAX_VALUE,0));
        assertEquals(Integer.MAX_VALUE,MotionPolicy.interpolate(Integer.MIN_VALUE,Integer.MAX_VALUE,1));
        assertEquals(0,MotionPolicy.interpolate(Integer.MIN_VALUE,Integer.MAX_VALUE,.5f));
        assertEquals(12,MotionPolicy.interpolate(12,-12,-1));
        assertEquals(-12,MotionPolicy.interpolate(12,-12,2));
        assertEquals(0,MotionPolicy.interpolate(-155,155,.5f));
    }
    @Test public void pointsKeepActualSignsAndZero() {
        assertEquals("+0",MotionPolicy.points(0,true));
        assertEquals("-155",MotionPolicy.points(-155,true));
        assertEquals("310",MotionPolicy.points(310,false));
    }
    @Test public void floatingDeltaUsesFreeSpaceAndNeverOverlapsTheScore() {
        assertEquals(12,MotionPolicy.deltaTop(30,50,80,14,4));
        assertEquals(34,MotionPolicy.deltaTop(8,30,70,18,4));
    }
    @Test public void largeFontWithoutFreeSpaceOmitsTheDecorativeDelta() {
        assertEquals(-1,MotionPolicy.deltaTop(12,50,65,30,4));
    }
}
