package com.shop.supportservice.privacy;

import org.springframework.stereotype.Component;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PiiMasker {

    // Order ids are matched first and kept as they are: a UUID has long digit runs that would look like a phone.
    private static final Pattern PII = Pattern.compile(
            "(?<uuid>\\b\\p{XDigit}{8}-?\\p{XDigit}{4}-?\\p{XDigit}{4}-?\\p{XDigit}{4}-?\\p{XDigit}{12}\\b)"
                    + "|(?<email>[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+)"
                    + "|(?<card>(?<![\\w+])\\d(?:[ -]?\\d){12,18}(?!\\w))"
                    + "|(?<phone>(?<!\\w)\\+?\\d(?:[ ()-]{0,2}\\d){8,13}(?!\\w))");

    public String mask(String text) {
        return PII.matcher(text).replaceAll(this::replacement);
    }

    private String replacement(MatchResult m) {
        if (m.group("uuid") != null) {
            return Matcher.quoteReplacement(m.group());
        }
        if (m.group("email") != null) {
            return "[EMAIL]";
        }
        if (m.group("card") != null) {
            return "[CARD]";
        }
        return "[PHONE]";
    }
}
