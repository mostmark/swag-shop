package com.demo.shop.search;

/** Levenshtein edit distance: the number of single-character edits that turn one word into another. */
public interface EditDistance {

    int distance(String a, String b);
}
