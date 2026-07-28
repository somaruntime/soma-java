package io.github.somaruntime.soma.runtime;

import java.util.Comparator;

/** Canonical String order by Unicode code point, including deterministic lone-surrogate order. */
final class UnicodeCodePointOrder implements Comparator<String> {
    static final UnicodeCodePointOrder INSTANCE = new UnicodeCodePointOrder();

    private UnicodeCodePointOrder() { }

    @Override
    public int compare(String left, String right) {
        if (left == right) return 0;
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            int leftCodePoint = left.codePointAt(leftIndex);
            int rightCodePoint = right.codePointAt(rightIndex);
            if (leftCodePoint != rightCodePoint) {
                return leftCodePoint < rightCodePoint ? -1 : 1;
            }
            leftIndex += Character.charCount(leftCodePoint);
            rightIndex += Character.charCount(rightCodePoint);
        }
        return left.length() - right.length();
    }
}
