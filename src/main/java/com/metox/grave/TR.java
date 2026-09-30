package com.metox.grave;

/** Turkce ilgi eki: chizzar -> chizzar'in, Ali -> Ali'nin. */
public final class TR {

    private TR() {}

    public static String genitive(String word) {
        if (word == null || word.isEmpty()) return word == null ? "" : word;
        return word + "'" + suffix(word);
    }

    private static String suffix(String word) {
        char sv = 'i';
        for (int i = word.length() - 1; i >= 0; i--) {
            Character g = group(word.charAt(i));
            if (g != null) {
                sv = g;
                break;
            }
        }
        boolean endsVowel = group(word.charAt(word.length() - 1)) != null;
        return (endsVowel ? "n" : "") + sv + "n";
    }

    private static Character group(char c) {
        switch (c) {
            case 'a': case 'A': case 'ı': case 'I': return 'ı';
            case 'e': case 'E': case 'i': case 'İ': return 'i';
            case 'o': case 'O': case 'u': case 'U': return 'u';
            case 'ö': case 'Ö': case 'ü': case 'Ü': return 'ü';
            default: return null;
        }
    }
}
