package com.cacheguard.model;

import java.util.Arrays;
import java.util.Random;

/**
 * Deterministic generator: the same arguments always produce the same request stream,
 * which keeps comparisons reproducible.
 */
public final class WorkloadGenerator {

    public static final int MAX_KEY_SPACE = 1_000_000;
    private static final int MAX_ZIPF_KEYS = 100_000;

    private WorkloadGenerator() {
    }

    /**
     * @param length   number of requests (1 .. {@code RequestParser.MAX_REQUESTS})
     * @param keySpace number of distinct blocks (2 .. {@link #MAX_KEY_SPACE}); keys are 1..keySpace
     */
    public static int[] generate(WorkloadPattern pattern, int length, int keySpace, long seed) {
        if (length < 1) {
            throw new IllegalArgumentException("Length must be at least 1");
        }
        if (keySpace < 2 || keySpace > MAX_KEY_SPACE) {
            throw new IllegalArgumentException("Distinct blocks must be between 2 and " + MAX_KEY_SPACE);
        }
        Random rnd = new Random(seed);
        int[] out = new int[length];
        switch (pattern) {
            case UNIFORM -> {
                for (int i = 0; i < length; i++) {
                    out[i] = rnd.nextInt(keySpace) + 1;
                }
            }
            case ZIPF -> fillZipf(out, keySpace, rnd);
            case LOOP -> {
                for (int i = 0; i < length; i++) {
                    out[i] = (i % keySpace) + 1;
                }
            }
            case SCAN_HOT -> fillScanHot(out, keySpace, rnd);
            case PHASES -> fillPhases(out, keySpace, rnd);
        }
        return out;
    }

    private static void fillZipf(int[] out, int keySpace, Random rnd) {
        int n = Math.min(keySpace, MAX_ZIPF_KEYS);
        double[] cdf = new double[n];
        double sum = 0;
        for (int rank = 1; rank <= n; rank++) {
            sum += 1.0 / rank;
            cdf[rank - 1] = sum;
        }
        for (int i = 0; i < out.length; i++) {
            double u = rnd.nextDouble() * sum;
            int idx = Arrays.binarySearch(cdf, u);
            if (idx < 0) {
                idx = -idx - 1;
            }
            out[i] = Math.min(idx, n - 1) + 1;
        }
    }

    private static void fillScanHot(int[] out, int keySpace, Random rnd) {
        int hot = Math.max(1, keySpace / 10);
        int cold = Math.max(1, keySpace - hot);
        int scan = 0;
        for (int i = 0; i < out.length; i++) {
            if (rnd.nextDouble() < 0.7) {
                out[i] = rnd.nextInt(hot) + 1;
            } else {
                out[i] = hot + (scan % cold) + 1;
                scan++;
            }
        }
    }

    private static void fillPhases(int[] out, int keySpace, Random rnd) {
        int window = Math.max(2, keySpace / 5);
        int phaseLength = Math.max(50, out.length / 8);
        for (int i = 0; i < out.length; i++) {
            long offset = (long) (i / phaseLength) * Math.max(1, window / 2);
            out[i] = (int) ((offset + rnd.nextInt(window)) % keySpace) + 1;
        }
    }
}
