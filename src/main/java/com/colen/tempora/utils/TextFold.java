package com.colen.tempora.utils;

import java.sql.Connection;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.sqlite.Function;

// How f: of /tempora lookup compares text, the same on the client for names (ClientNameIndex) and on the server for
// text stored as players read it (tempora_fold in SQL, TextFilter.textCondition)
public final class TextFold {

    private static final String SQL_FUNCTION = "tempora_fold";

    private static final char COMBINING_DOT = 0x307;
    private static final char FINAL_SIGMA = 0x3C2;
    private static final char SIGMA = 0x3C3;
    private static final char DOTLESS_I = 0x131;
    private static final Pattern FORMATTING_CODE = Pattern.compile("§[0-9a-fk-orA-FK-OR]");

    private TextFold() {}

    // Lower case and in one form so what's typed matches however the name was written. NFKC so full width letters
    // from an IME match normal ones. Lower casing a Turkish dotted I leaves a combining dot that typing i doesn't,
    // Greek has a separate final sigma and capital I lower cases to i not the Turkish dotless one, so all three go.
    // Accents stay, taking them off would also take the marks off Japanese kana and change the word
    public static String fold(String text) {
        // Colour codes in a name aren't something anyone types
        return Normalizer.normalize(
            FORMATTING_CODE.matcher(text)
                .replaceAll(""),
            Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replace(String.valueOf(COMBINING_DOT), "")
            .replace(FINAL_SIGMA, SIGMA)
            .replace(DOTLESS_I, 'i');
    }

    // Comma separated, folded. Each is matched on its own so f:stone,dirt is either. One that's only _ would search
    // for a space and match nearly everything, so it's left out like an empty one
    public static List<String> terms(String text) {
        List<String> terms = new ArrayList<>();
        for (String term : fold(text).split(",")) {
            if (!spaced(term).trim()
                .isEmpty()) terms.add(term);
        }
        return terms;
    }

    // Shown names can't be typed with spaces in a command, so an _ stands for one (iron_ore matches Iron Ore). A run of
    // them is one space, so a doubled _ by mistake still finds it
    public static String spaced(String term) {
        return term.replaceAll("_+", " ");
    }

    // Takes a name already through fold. Names with a real _ in them match as typed too
    public static boolean matchesShownName(List<String> terms, String foldedName) {
        for (String term : terms) {
            if (foldedName.contains(term) || foldedName.contains(spaced(term))) return true;
        }
        return false;
    }

    // SQL giving this expression folded, needs addSqlFunction on the connection
    static String sql(String expression) {
        return SQL_FUNCTION + "(" + expression + ")";
    }

    // Adds fold to SQL as tempora_fold(text). Every read connection gets it
    public static void addSqlFunction(Connection conn) throws SQLException {
        Function.create(conn, SQL_FUNCTION, new Function() {

            @Override
            protected void xFunc() throws SQLException {
                String value = value_text(0);
                if (value == null) result();
                else result(fold(value));
            }
        }, 1, Function.FLAG_DETERMINISTIC);
    }
}
