package com.kellvyn.assistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class NativeModelPolicyTest {
    @Test public void requiresExactModelAndHashMarker() {
        assertTrue(NativeModelPolicy.isVerified(2_008_432_640L, NativeModelPolicy.MODEL_SHA256));
        assertFalse(NativeModelPolicy.isVerified(2_008_432_639L, NativeModelPolicy.MODEL_SHA256));
        assertFalse(NativeModelPolicy.isVerified(2_008_432_640L, "incorrect"));
    }

    @Test public void resumesOnlyAtRequestedOffsetForTheSameCompleteModel() {
        assertTrue(NativeModelPolicy.canAppend(100L, 206, "bytes 100-2008432639/2008432640"));
        assertFalse(NativeModelPolicy.canAppend(100L, 206, "bytes 0-2008432639/2008432640"));
        assertFalse(NativeModelPolicy.canAppend(100L, 200, "bytes 100-2008432639/2008432640"));
        assertFalse(NativeModelPolicy.canAppend(100L, 206, "bytes 100-2008432638/2008432639"));
        assertFalse(NativeModelPolicy.canAppend(100L, 206, null));
    }

    @Test public void cannotAppendToCompletedOrEmptyDownloads() {
        assertFalse(NativeModelPolicy.canAppend(0L, 206, "bytes 0-2008432639/2008432640"));
        assertFalse(NativeModelPolicy.canAppend(2_008_432_640L, 206, "bytes 2008432640-2008432639/2008432640"));
    }
}