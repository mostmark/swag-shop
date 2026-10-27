package com.demo.shop.search;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * The fix: dynamic programming. Each sub-problem is solved once and only two rows of the table are kept, so
 * comparing a 7-letter term with a 10-letter word takes 70 steps instead of 430,000 calls.
 */
@ApplicationScoped
public class IterativeEditDistance implements EditDistance {

    @Override
    public int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitutionCost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1),
                        previous[j - 1] + substitutionCost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
