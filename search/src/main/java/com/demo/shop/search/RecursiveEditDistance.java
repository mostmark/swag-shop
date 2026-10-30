package com.demo.shop.search;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Edit distance written straight from the textbook definition.
 *
 * <p>
 * It is correct, but every call branches into three recursive calls and the same sub-problems are solved
 * over and over again: the work grows exponentially with the word length. Comparing a 7-letter search term
 * with a 10-letter word takes about 430,000 calls, and one search makes hundreds of comparisons.
 */
@ApplicationScoped
public class RecursiveEditDistance implements EditDistance {

    @Override
    public int distance(String a, String b) {
        return distance(a, a.length(), b, b.length());
    }

    private int distance(String a, int i, String b, int j) {
        if (i == 0) {
            return j;
        }
        if (j == 0) {
            return i;
        }
        int substitutionCost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        int deletion = distance(a, i - 1, b, j) + 1;
        int insertion = distance(a, i, b, j - 1) + 1;
        int substitution = distance(a, i - 1, b, j - 1) + substitutionCost;
        return Math.min(Math.min(deletion, insertion), substitution);
    }
}
