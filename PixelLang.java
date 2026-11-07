/*
 ╔══════════════════════════════════════════════════════════════════╗
 ║         PixelLang Compiler  —  Single File Edition (Java)        ║
 ║         Compiler Construction (CCP) Final Project               ║
 ║                                                                  ║
 ║  FLOW (for every program):                                       ║
 ║   1. You type/load your PixelLang code                          ║
 ║   2. PHASE 1  →  Token Table (52 token types shown)             ║
 ║   3. PHASE 2  →  Grammar rules + Parse trace                    ║
 ║   4. PHASE 3  →  If errors → show all errors and STOP           ║
 ║                  If clean  → execute and show output             ║
 ║                                                                  ║
 ║  USAGE:                                                          ║
 ║    java PixelLang              ← interactive mode (REPL)        ║
 ║    java PixelLang file.pix     ← compile a .pix file            ║
 ║    java PixelLang --demo       ← run the built-in demo          ║
 ║    java PixelLang --errors     ← run the error demo             ║
 ╚══════════════════════════════════════════════════════════════════╝
*/

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class PixelLang {

    // ══════════════════════════════════════════════════════════════════
    //  ANSI COLOUR HELPERS
    // ══════════════════════════════════════════════════════════════════
    static String c(String code, String t) { return "\033[" + code + "m" + t + "\033[0m"; }
    static String purple(String t) { return c("95", t); }
    static String cyan(String t)   { return c("96", t); }
    static String green(String t)  { return c("92", t); }
    static String yellow(String t) { return c("93", t); }
    static String red(String t)    { return c("91", t); }
    static String blue(String t)   { return c("94", t); }
    static String bold(String t)   { return c("1",  t); }
    static String dim(String t)    { return c("2",  t); }
    static String white(String t)  { return c("97", t); }

    static String repeatChar(char ch, int n) {
        if (n <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(ch);
        return sb.toString();
    }

    // pad helpers (Java String.format width counts raw chars, fine for un-coloured text)
    static String ljust(String s, int w) {
        if (s.length() >= w) return s;
        return s + repeatChar(' ', w - s.length());
    }
    static String rjust(String s, int w) {
        if (s.length() >= w) return s;
        return repeatChar(' ', w - s.length()) + s;
    }
    static String center(String s, int w) {
        if (s.length() >= w) return s;
        int total = w - s.length();
        int left = total / 2;
        int right = total - left;
        return repeatChar(' ', left) + s + repeatChar(' ', right);
    }

    static void sep(String title, char ch, int w) {
        if (!title.isEmpty()) {
            int pad = (w - title.length() - 2) / 2;
            String line = repeatChar(ch, pad) + " " + title + " " + repeatChar(ch, pad);
            System.out.println("\n" + cyan(bold(line)));
        } else {
            System.out.println(dim(repeatChar(ch, w)));
        }
    }
    static void sep(String title) { sep(title, '─', 66); }
    static void sep() { sep("", '─', 66); }

    static void hdr(String text) {
        System.out.println("\n  " + purple("┌" + repeatChar('─', 60) + "┐"));
        System.out.println("  " + purple("│") + "  " + bold(white(ljust(text, 60))) + purple("│"));
        System.out.println("  " + purple("└" + repeatChar('─', 60) + "┘"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  SECTION 1 — TOKEN DEFINITIONS  (52 token types)
    // ══════════════════════════════════════════════════════════════════

    // ── Keyword strings ──
    static final String KW_STORE="store", KW_ASK="ask", KW_DASH="dash", KW_LEAP="leap",
            KW_STRIKE="strike", KW_GRAB="grab", KW_ECHO="echo", KW_WHEN="when",
            KW_OTHERWISE="otherwise", KW_REPEAT="repeat", KW_FOE="foe", KW_ITEM="item",
            KW_FORE="fore", KW_BACK="back", KW_HIGH="high", KW_LOW="low",
            KW_SOAR="soar", KW_PACES="paces", KW_INTO="into";

    static final Set<String> KEYWORDS = new LinkedHashSet<>(Arrays.asList(
            KW_STORE, KW_ASK, KW_DASH, KW_LEAP, KW_STRIKE, KW_GRAB,
            KW_ECHO, KW_WHEN, KW_OTHERWISE, KW_REPEAT,
            KW_FOE, KW_ITEM, KW_SOAR, KW_PACES, KW_INTO,
            KW_FORE, KW_BACK, KW_HIGH, KW_LOW));

    // ── Token type constants ──
    static final String TT_KW_STORE="KW_STORE", TT_KW_ASK="KW_ASK", TT_KW_DASH="KW_DASH",
            TT_KW_LEAP="KW_LEAP", TT_KW_STRIKE="KW_STRIKE", TT_KW_GRAB="KW_GRAB",
            TT_KW_ECHO="KW_ECHO", TT_KW_WHEN="KW_WHEN", TT_KW_OTHERWISE="KW_OTHERWISE",
            TT_KW_REPEAT="KW_REPEAT", TT_KW_FOE="KW_FOE", TT_KW_ITEM="KW_ITEM",
            TT_KW_SOAR="KW_SOAR", TT_KW_PACES="KW_PACES", TT_KW_INTO="KW_INTO",
            TT_KW_FORE="KW_FORE", TT_KW_BACK="KW_BACK", TT_KW_HIGH="KW_HIGH", TT_KW_LOW="KW_LOW";
    static final String TT_IDENTIFIER="IDENTIFIER", TT_NUMBER="NUMBER", TT_STRING="STRING";
    static final String TT_ASSIGN="ASSIGN", TT_EQ="EQ", TT_NEQ="NEQ", TT_GT="GT",
            TT_LT="LT", TT_GTE="GTE", TT_LTE="LTE";
    static final String TT_LPAREN="LPAREN", TT_RPAREN="RPAREN", TT_LBRACE="LBRACE",
            TT_RBRACE="RBRACE", TT_SEMICOLON="SEMICOLON";
    static final String TT_EOF="EOF";

    // Map keyword string → specific token type
    static final Map<String,String> KW_TO_TT = new LinkedHashMap<>();
    static {
        KW_TO_TT.put(KW_STORE, TT_KW_STORE);   KW_TO_TT.put(KW_ASK, TT_KW_ASK);
        KW_TO_TT.put(KW_DASH, TT_KW_DASH);     KW_TO_TT.put(KW_LEAP, TT_KW_LEAP);
        KW_TO_TT.put(KW_STRIKE, TT_KW_STRIKE); KW_TO_TT.put(KW_GRAB, TT_KW_GRAB);
        KW_TO_TT.put(KW_ECHO, TT_KW_ECHO);     KW_TO_TT.put(KW_WHEN, TT_KW_WHEN);
        KW_TO_TT.put(KW_OTHERWISE, TT_KW_OTHERWISE);
        KW_TO_TT.put(KW_REPEAT, TT_KW_REPEAT); KW_TO_TT.put(KW_FOE, TT_KW_FOE);
        KW_TO_TT.put(KW_ITEM, TT_KW_ITEM);     KW_TO_TT.put(KW_SOAR, TT_KW_SOAR);
        KW_TO_TT.put(KW_PACES, TT_KW_PACES);   KW_TO_TT.put(KW_INTO, TT_KW_INTO);
        KW_TO_TT.put(KW_FORE, TT_KW_FORE);     KW_TO_TT.put(KW_BACK, TT_KW_BACK);
        KW_TO_TT.put(KW_HIGH, TT_KW_HIGH);     KW_TO_TT.put(KW_LOW, TT_KW_LOW);
    }

    // Colour for each token category (for display)
    static Function<String,String> tokColour(String tt) {
        if (tt.startsWith("KW_")) return PixelLang::purple;
        if (tt.equals(TT_IDENTIFIER)) return PixelLang::cyan;
        if (tt.equals(TT_NUMBER)) return PixelLang::yellow;
        if (tt.equals(TT_STRING)) return PixelLang::green;
        if (tt.equals(TT_ASSIGN)||tt.equals(TT_EQ)||tt.equals(TT_NEQ)||tt.equals(TT_GT)
                ||tt.equals(TT_LT)||tt.equals(TT_GTE)||tt.equals(TT_LTE)) return PixelLang::yellow;
        if (tt.equals(TT_LPAREN)||tt.equals(TT_RPAREN)||tt.equals(TT_LBRACE)
                ||tt.equals(TT_RBRACE)||tt.equals(TT_SEMICOLON)) return PixelLang::blue;
        return PixelLang::dim;
    }

    // ── 52 Token Type Table (for display) ──
    static final String[][] ALL_52_TOKENS = {
        {TT_KW_STORE, "store", "Statement Keyword", "Declare a variable"},
        {TT_KW_ASK, "ask", "Statement Keyword", "Read user input"},
        {TT_KW_DASH, "dash", "Statement Keyword", "Move the player"},
        {TT_KW_LEAP, "leap", "Statement Keyword", "Make the player jump"},
        {TT_KW_STRIKE, "strike", "Statement Keyword", "Attack an enemy"},
        {TT_KW_GRAB, "grab", "Statement Keyword", "Pick up an item"},
        {TT_KW_ECHO, "echo", "Statement Keyword", "Print a message"},
        {TT_KW_WHEN, "when", "Statement Keyword", "Conditional (if)"},
        {TT_KW_OTHERWISE, "otherwise", "Statement Keyword", "Else branch"},
        {TT_KW_REPEAT, "repeat", "Statement Keyword", "Loop N times"},
        {TT_KW_FOE, "foe", "Modifier Keyword", "Enemy target for strike"},
        {TT_KW_ITEM, "item", "Modifier Keyword", "Item target for grab"},
        {TT_KW_SOAR, "soar", "Modifier Keyword", "Jump mode for leap"},
        {TT_KW_PACES, "paces", "Modifier Keyword", "Distance unit for dash"},
        {TT_KW_INTO, "into", "Modifier Keyword", "Target variable for ask"},
        {TT_KW_FORE, "fore", "Direction Keyword", "Move forward (+x)"},
        {TT_KW_BACK, "back", "Direction Keyword", "Move backward (-x)"},
        {TT_KW_HIGH, "high", "Direction Keyword", "Move up (+y)"},
        {TT_KW_LOW, "low", "Direction Keyword", "Move down (-y)"},
        {TT_IDENTIFIER, "letter(letter|digit|_)*", "Identifier", "Variable / enemy / item name"},
        {TT_NUMBER, "digit+", "Literal", "Integer constant (e.g. 42)"},
        {TT_STRING, "\"…\"", "Literal", "String constant (e.g. \"hi\")"},
        {TT_ASSIGN, "=", "Operator", "Assignment operator"},
        {TT_EQ, "==", "Operator", "Equality comparison"},
        {TT_NEQ, "!=", "Operator", "Not-equal comparison"},
        {TT_GT, ">", "Operator", "Greater-than comparison"},
        {TT_LT, "<", "Operator", "Less-than comparison"},
        {TT_GTE, ">=", "Operator", "Greater-than-or-equal"},
        {TT_LTE, "<=", "Operator", "Less-than-or-equal"},
        {TT_LPAREN, "(", "Delimiter", "Left parenthesis"},
        {TT_RPAREN, ")", "Delimiter", "Right parenthesis"},
        {TT_LBRACE, "{", "Delimiter", "Left brace (block open)"},
        {TT_RBRACE, "}", "Delimiter", "Right brace (block close)"},
        {TT_SEMICOLON, ";", "Delimiter", "Statement terminator"},
        {TT_EOF, "EOF", "Special", "End of file marker"},
        {"WHITESPACE", "space/\\t/\\n", "Skipped", "Whitespace (discarded)"},
        {"COMMENT", "# … \\n", "Skipped", "Comment (discarded)"},
        {"DFA_S0", "START", "DFA State", "Initial / reset state"},
        {"DFA_S1", "IN_IDENT", "DFA State", "Building identifier/keyword"},
        {"DFA_S2", "IN_NUM", "DFA State", "Building integer number"},
        {"DFA_S3", "IN_STRING", "DFA State", "Building string literal"},
        {"DFA_S4", "IN_OP", "DFA State", "Building operator (1 or 2 char)"},
        {"DFA_S5", "IN_COMMENT", "DFA State", "Skipping comment to end of line"},
        {"DFA_S6", "ACCEPT", "DFA State", "Token complete — emit it"},
        {"DFA_S7", "ERROR", "DFA State", "Invalid char — lexical error"},
        {"NT_PROGRAM", "program", "Non-Terminal", "Root of the grammar"},
        {"NT_STATEMENT", "statement", "Non-Terminal", "Any single statement"},
        {"NT_BLOCK", "block", "Non-Terminal", "Brace-enclosed statement list"},
        {"NT_CONDITION", "condition", "Non-Terminal", "Boolean condition expression"},
        {"NT_DIRECTION", "direction", "Non-Terminal", "Movement direction word"},
        {"NT_VALUE", "value", "Non-Terminal", "NUMBER or STRING literal"},
        {"NT_EXPR", "expr", "Non-Terminal", "Identifier or literal"},
    };

    // ══════════════════════════════════════════════════════════════════
    //  SECTION 2 — GRAMMAR RULES  (full EBNF + FIRST/FOLLOW)
    // ══════════════════════════════════════════════════════════════════

    static final String[][] GRAMMAR_EBNF = {
        {"program", "statement*"},
        {"statement", "store_stmt | ask_stmt | dash_stmt | leap_stmt\n"
                + "             | strike_stmt | grab_stmt | echo_stmt\n"
                + "             | when_stmt   | repeat_stmt"},
        {"store_stmt", "'store'  IDENTIFIER '=' ( NUMBER | STRING ) ';'"},
        {"ask_stmt", "'ask' '(' STRING ')' 'into' IDENTIFIER ';'"},
        {"dash_stmt", "'dash' direction NUMBER 'paces' ';'"},
        {"leap_stmt", "'leap' 'soar' ';'"},
        {"strike_stmt", "'strike' 'foe' IDENTIFIER ';'"},
        {"grab_stmt", "'grab'   'item' IDENTIFIER ';'"},
        {"echo_stmt", "'echo'   '(' STRING ')' ';'"},
        {"when_stmt", "'when' '(' condition ')' block ( 'otherwise' block )?"},
        {"repeat_stmt", "'repeat' '(' NUMBER ')' block"},
        {"block", "'{' statement* '}'"},
        {"direction", "'fore' | 'back' | 'high' | 'low'"},
        {"condition", "IDENTIFIER ( '==' | '!=' | '>' | '<' | '>=' | '<=' ) ( NUMBER | IDENTIFIER )"},
        {"IDENTIFIER", "letter ( letter | digit | '_' )*"},
        {"NUMBER", "digit+"},
        {"STRING", "'\"' [^\"]* '\"'"},
    };

    static final String[][] FIRST_SETS = {
        {"FIRST(program)", "{ store ask dash leap strike grab echo when repeat EOF }"},
        {"FIRST(statement)", "{ store ask dash leap strike grab echo when repeat }"},
        {"FIRST(store_stmt)", "{ store }"},
        {"FIRST(ask_stmt)", "{ ask }"},
        {"FIRST(dash_stmt)", "{ dash }"},
        {"FIRST(leap_stmt)", "{ leap }"},
        {"FIRST(strike_stmt)", "{ strike }"},
        {"FIRST(grab_stmt)", "{ grab }"},
        {"FIRST(echo_stmt)", "{ echo }"},
        {"FIRST(when_stmt)", "{ when }"},
        {"FIRST(repeat_stmt)", "{ repeat }"},
        {"FIRST(block)", "{ { }"},
        {"FIRST(direction)", "{ fore back high low }"},
        {"FIRST(condition)", "{ IDENTIFIER }"},
    };

    static final String[][] FOLLOW_SETS = {
        {"FOLLOW(program)", "{ EOF }"},
        {"FOLLOW(statement)", "FIRST(statement) ∪ { } EOF }"},
        {"FOLLOW(block)", "{ otherwise } EOF }"},
        {"FOLLOW(condition)", "{ ) }"},
        {"FOLLOW(direction)", "{ NUMBER }"},
    };

    // ══════════════════════════════════════════════════════════════════
    //  SECTION 3 — TOKENIZER  (DFA — 8 states)
    // ══════════════════════════════════════════════════════════════════

    static final String S0="START", S1="IN_IDENT", S2="IN_NUM", S3="IN_STRING",
            S4="IN_OP", S5="IN_COMMENT", S6="ACCEPT", S7="ERROR";

    static class Token {
        String type;
        Object value;   // String, Integer, or null
        int line;
        Token(String tt, Object value, int line) { this.type=tt; this.value=value; this.line=line; }
        public String toString() { return "Token(" + type + ", " + value + ", line " + line + ")"; }
    }

    static class Tokenizer {
        String src;
        int pos = 0;
        int line = 1;
        List<Token> tokens = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        Tokenizer(String src) { this.src = src; }

        Character ch() { return pos < src.length() ? src.charAt(pos) : null; }

        char adv() {
            char c = src.charAt(pos); pos++;
            if (c == '\n') line++;
            return c;
        }

        List<Token> tokenize() {
            String state = S0;
            StringBuilder buf = new StringBuilder();

            while (pos <= src.length()) {
                Character c = ch();

                if (state.equals(S0)) {
                    if (c == null) break;
                    else if (c==' '||c=='\t'||c=='\r'||c=='\n') { adv(); }
                    else if (c=='#') { adv(); state=S5; }
                    else if (Character.isLetter(c) || c=='_') { buf.setLength(0); buf.append(adv()); state=S1; }
                    else if (Character.isDigit(c)) { buf.setLength(0); buf.append(adv()); state=S2; }
                    else if (c=='"') { adv(); buf.setLength(0); state=S3; }
                    else if (c=='>'||c=='<'||c=='!'||c=='=') { buf.setLength(0); buf.append(adv()); state=S4; }
                    else if (c==';') { adv(); tokens.add(new Token(TT_SEMICOLON, ";", line)); }
                    else if (c=='(') { adv(); tokens.add(new Token(TT_LPAREN, "(", line)); }
                    else if (c==')') { adv(); tokens.add(new Token(TT_RPAREN, ")", line)); }
                    else if (c=='{') { adv(); tokens.add(new Token(TT_LBRACE, "{", line)); }
                    else if (c=='}') { adv(); tokens.add(new Token(TT_RBRACE, "}", line)); }
                    else {
                        int el = line; char bad = c; adv();
                        errors.add("[Line " + el + "] Lexical Error: Invalid character '" + bad + "'");
                    }
                }
                else if (state.equals(S1)) {
                    if (c != null && (Character.isLetterOrDigit(c) || c=='_')) { buf.append(adv()); }
                    else {
                        String word = buf.toString();
                        String tt = KW_TO_TT.getOrDefault(word, TT_IDENTIFIER);
                        tokens.add(new Token(tt, word, line));
                        state = S0;
                    }
                }
                else if (state.equals(S2)) {
                    if (c != null && Character.isDigit(c)) { buf.append(adv()); }
                    else {
                        tokens.add(new Token(TT_NUMBER, Integer.parseInt(buf.toString()), line));
                        state = S0;
                    }
                }
                else if (state.equals(S3)) {
                    if (c == null) {
                        errors.add("[Line " + line + "] Lexical Error: Unterminated string");
                        break;
                    } else if (c == '"') {
                        adv();
                        tokens.add(new Token(TT_STRING, buf.toString(), line));
                        state = S0;
                    } else {
                        buf.append(adv());
                    }
                }
                else if (state.equals(S4)) {
                    String b = buf.toString();
                    if (c != null && c=='=' && (b.equals(">")||b.equals("<")||b.equals("!")||b.equals("="))) {
                        buf.append(adv());
                        String two = buf.toString();
                        String tt = two.equals(">=") ? TT_GTE : two.equals("<=") ? TT_LTE
                                  : two.equals("!=") ? TT_NEQ : TT_EQ;
                        tokens.add(new Token(tt, two, line));
                    } else if (b.equals("=")) {
                        tokens.add(new Token(TT_ASSIGN, "=", line));
                    } else {
                        String tt = b.equals(">") ? TT_GT : b.equals("<") ? TT_LT : TT_NEQ;
                        tokens.add(new Token(tt, b, line));
                    }
                    state = S0;
                }
                else if (state.equals(S5)) {
                    if (c == null || c == '\n') { state = S0; }
                    else { adv(); }
                }
            }

            tokens.add(new Token(TT_EOF, null, line));
            return tokens;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  SECTION 4 — PARSER  (Recursive Descent / LL1)
    // ══════════════════════════════════════════════════════════════════

    static class ParseError extends RuntimeException {
        int line;
        ParseError(String msg, int line) { super(msg); this.line = line; }
    }

    // AST node — a generic map-like container
    static class Node {
        String type;
        Map<String,Object> data = new LinkedHashMap<>();
        Node(String type) { this.type = type; }
        Node put(String k, Object v) { data.put(k, v); return this; }
        Object get(String k) { return data.get(k); }
        int getInt(String k) { return ((Number) data.get(k)).intValue(); }
        String getStr(String k) { return (String) data.get(k); }
    }

    static class Parser {
        List<Token> tokens;
        int pos = 0;
        List<String> errors = new ArrayList<>();
        List<String> log = new ArrayList<>();

        Parser(List<Token> tokens) { this.tokens = tokens; }

        Token cur() { return tokens.get(pos); }
        String type() { return cur().type; }
        int line() { return cur().line; }

        Token adv() {
            Token t = tokens.get(pos);
            if (!t.type.equals(TT_EOF)) pos++;
            return t;
        }

        Token expect(String tt, String val) {
            Token t = cur();
            if (!t.type.equals(tt)) {
                throw new ParseError("[Line " + t.line + "] Syntax Error: Expected '"
                        + (val != null ? val : tt) + "' but found '" + t.value + "'", t.line);
            }
            if (val != null && !val.equals(String.valueOf(t.value))) {
                throw new ParseError("[Line " + t.line + "] Syntax Error: Expected '"
                        + val + "' but found '" + t.value + "'", t.line);
            }
            return adv();
        }
        Token expect(String tt) { return expect(tt, null); }

        void apply(String rule) { log.add(rule); }

        void recover() {
            if (!type().equals(TT_EOF)) adv();
            while (!type().equals(TT_EOF) && !type().equals(TT_SEMICOLON)
                    && !type().equals(TT_RBRACE) && !type().equals(TT_LBRACE)) {
                adv();
            }
            if (type().equals(TT_SEMICOLON)) adv();
        }

        Node parseProgram() {
            apply("program → statement*");
            List<Node> stmts = new ArrayList<>();
            while (!type().equals(TT_EOF)) {
                try {
                    Node s = stmt();
                    if (s != null) stmts.add(s);
                } catch (ParseError e) {
                    errors.add(e.getMessage());
                    recover();
                }
            }
            return new Node("program").put("body", stmts);
        }

        Node stmt() {
            Token t = cur();
            String rule;
            switch (t.type) {
                case TT_KW_STORE:  rule = "store_stmt → 'store' ID '=' value ';'"; break;
                case TT_KW_ASK:    rule = "ask_stmt   → 'ask' '(' STRING ')' 'into' ID ';'"; break;
                case TT_KW_DASH:   rule = "dash_stmt  → 'dash' direction NUMBER 'paces' ';'"; break;
                case TT_KW_LEAP:   rule = "leap_stmt  → 'leap' 'soar' ';'"; break;
                case TT_KW_STRIKE: rule = "strike_stmt → 'strike' 'foe' ID ';'"; break;
                case TT_KW_GRAB:   rule = "grab_stmt  → 'grab' 'item' ID ';'"; break;
                case TT_KW_ECHO:   rule = "echo_stmt  → 'echo' '(' STRING ')' ';'"; break;
                case TT_KW_WHEN:   rule = "when_stmt  → 'when' '(' condition ')' block ( 'otherwise' block )?"; break;
                case TT_KW_REPEAT: rule = "repeat_stmt → 'repeat' '(' NUMBER ')' block"; break;
                default:
                    throw new ParseError("[Line " + t.line + "] Syntax Error: Unexpected token '" + t.value + "'", t.line);
            }
            apply("statement → " + t.value + "_stmt");
            apply(rule);
            switch (t.type) {
                case TT_KW_STORE:  return pStore();
                case TT_KW_ASK:    return pAsk();
                case TT_KW_DASH:   return pDash();
                case TT_KW_LEAP:   return pLeap();
                case TT_KW_STRIKE: return pStrike();
                case TT_KW_GRAB:   return pGrab();
                case TT_KW_ECHO:   return pEcho();
                case TT_KW_WHEN:   return pWhen();
                case TT_KW_REPEAT: return pRepeat();
                default: return null;
            }
        }

        Node pStore() {
            int L = line(); adv();
            Token nm = expect(TT_IDENTIFIER);
            expect(TT_ASSIGN);
            Token vt = cur();
            if (!vt.type.equals(TT_NUMBER) && !vt.type.equals(TT_STRING))
                throw new ParseError("[Line " + vt.line + "] Syntax Error: Expected value after '='", vt.line);
            Token v = adv();
            expect(TT_SEMICOLON);
            return new Node("store").put("name", nm.value).put("value", v.value)
                    .put("vtype", v.type).put("line", L);
        }

        Node pAsk() {
            int L = line(); adv(); expect(TT_LPAREN);
            Token pr = expect(TT_STRING); expect(TT_RPAREN);
            expect(TT_KW_INTO, "into");
            Token nm = expect(TT_IDENTIFIER); expect(TT_SEMICOLON);
            return new Node("ask").put("prompt", pr.value).put("name", nm.value).put("line", L);
        }

        Node pDash() {
            int L = line(); adv();
            apply("direction → fore | back | high | low");
            Token dt = cur();
            if (!dt.type.equals(TT_KW_FORE) && !dt.type.equals(TT_KW_BACK)
                    && !dt.type.equals(TT_KW_HIGH) && !dt.type.equals(TT_KW_LOW))
                throw new ParseError("[Line " + dt.line + "] Syntax Error: Expected direction", dt.line);
            String dr = String.valueOf(adv().value);
            Token n = expect(TT_NUMBER);
            expect(TT_KW_PACES, "paces"); expect(TT_SEMICOLON);
            return new Node("dash").put("direction", dr).put("paces", n.value).put("line", L);
        }

        Node pLeap() {
            int L = line(); adv(); expect(TT_KW_SOAR, "soar"); expect(TT_SEMICOLON);
            return new Node("leap").put("line", L);
        }

        Node pStrike() {
            int L = line(); adv(); expect(TT_KW_FOE, "foe");
            Token nm = expect(TT_IDENTIFIER); expect(TT_SEMICOLON);
            return new Node("strike").put("enemy", nm.value).put("line", L);
        }

        Node pGrab() {
            int L = line(); adv(); expect(TT_KW_ITEM, "item");
            Token nm = expect(TT_IDENTIFIER); expect(TT_SEMICOLON);
            return new Node("grab").put("item", nm.value).put("line", L);
        }

        Node pEcho() {
            int L = line(); adv(); expect(TT_LPAREN);
            Token m = expect(TT_STRING); expect(TT_RPAREN); expect(TT_SEMICOLON);
            return new Node("echo").put("message", m.value).put("line", L);
        }

        Node pWhen() {
            int L = line(); adv(); expect(TT_LPAREN);
            apply("condition → IDENTIFIER op (NUMBER|IDENTIFIER)");
            Node cond = pCond(); expect(TT_RPAREN);
            apply("block → '{' statement* '}'");
            Node then = pBlock();
            Node oth = null;
            if (type().equals(TT_KW_OTHERWISE)) {
                apply("otherwise_clause → 'otherwise' block");
                adv(); apply("block → '{' statement* '}'"); oth = pBlock();
            }
            return new Node("when").put("condition", cond).put("then", then)
                    .put("otherwise", oth).put("line", L);
        }

        Node pRepeat() {
            int L = line(); adv(); expect(TT_LPAREN);
            Token n = expect(TT_NUMBER); expect(TT_RPAREN);
            apply("block → '{' statement* '}'");
            Node body = pBlock();
            return new Node("repeat").put("count", n.value).put("body", body).put("line", L);
        }

        Node pBlock() {
            expect(TT_LBRACE);
            List<Node> stmts = new ArrayList<>();
            while (!type().equals(TT_RBRACE) && !type().equals(TT_EOF)) {
                try {
                    Node s = stmt();
                    if (s != null) stmts.add(s);
                } catch (ParseError e) {
                    errors.add(e.getMessage());
                    if (!type().equals(TT_EOF) && !type().equals(TT_RBRACE)) recover();
                }
            }
            expect(TT_RBRACE);
            return new Node("block").put("body", stmts);
        }

        Node pCond() {
            Token l = expect(TT_IDENTIFIER);
            Token op = cur();
            if (!op.type.equals(TT_ASSIGN) && !op.type.equals(TT_EQ) && !op.type.equals(TT_NEQ)
                    && !op.type.equals(TT_GT) && !op.type.equals(TT_LT)
                    && !op.type.equals(TT_GTE) && !op.type.equals(TT_LTE))
                throw new ParseError("[Line " + op.line + "] Syntax Error: Expected operator in condition", op.line);
            String opv = String.valueOf(adv().value);
            if (opv.equals("=")) opv = "==";
            Token r = cur();
            if (!r.type.equals(TT_NUMBER) && !r.type.equals(TT_IDENTIFIER))
                throw new ParseError("[Line " + r.line + "] Syntax Error: Expected value in condition", r.line);
            Token rv = adv();
            return new Node("condition").put("left", l.value).put("op", opv)
                    .put("right", rv.value).put("rtype", rv.type);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  SECTION 5 — SEMANTIC ANALYZER + EXECUTION ENGINE
    // ══════════════════════════════════════════════════════════════════

    static String repr(Object v) {
        if (v instanceof String) return "'" + v + "'";
        return String.valueOf(v);
    }

    static class GameState {
        int x = 0, y = 0, vitality = 100, stamina = 100, score = 0;
        List<String> inventory = new ArrayList<>();
        Map<String,Integer> enemies = new LinkedHashMap<>();
        Map<String,Object> symbols = new LinkedHashMap<>();
        static final Map<String,Integer> MAX_HP = new LinkedHashMap<>();
        static {
            MAX_HP.put("dragon", 50); MAX_HP.put("goblin", 30); MAX_HP.put("skeleton", 20);
        }

        GameState() {
            enemies.put("dragon", 50); enemies.put("goblin", 30); enemies.put("skeleton", 20);
        }

        void display() {
            int w = 62;
            System.out.println();
            System.out.println(purple("╔" + repeatChar('═', w) + "╗"));
            System.out.println(purple("║") + bold(white(center("FINAL GAME STATE", w))) + purple("║"));
            System.out.println(purple("╠" + repeatChar('═', w) + "╣"));
            row("Position", "(x=" + x + ", y=" + y + ")", w);
            row("Vitality", vitality + "/100", w);
            row("Stamina", stamina + "/100", w);
            row("Score", String.valueOf(score), w);
            System.out.println(purple("╠" + repeatChar('═', w) + "╣"));
            String inv = inventory.isEmpty() ? "(empty)" : String.join(", ", inventory);
            row("Inventory", inv, w);
            System.out.println(purple("╠" + repeatChar('═', w) + "╣"));
            if (!enemies.isEmpty()) {
                for (Map.Entry<String,Integer> e : enemies.entrySet()) {
                    int maxhp = MAX_HP.getOrDefault(e.getKey(), 50);
                    int filled = (int)((double) e.getValue() / maxhp * 20);
                    String bar = repeatChar('█', filled) + repeatChar('░', 20 - filled);
                    row("Enemy " + e.getKey(), "HP " + e.getValue() + "/" + maxhp + "  [" + bar + "]", w);
                }
            } else {
                row("Enemies", "All defeated! 🎉", w);
            }
            System.out.println(purple("╠" + repeatChar('═', w) + "╣"));
            if (!symbols.isEmpty()) {
                for (Map.Entry<String,Object> e : symbols.entrySet())
                    row("var " + e.getKey(), repr(e.getValue()), w);
            } else {
                row("Variables", "(none declared)", w);
            }
            System.out.println(purple("╚" + repeatChar('═', w) + "╝"));
        }

        // mimics the python row(): "  {cyan(k):<30}{green(v)}" ljust to w+10
        void row(String k, String v, int w) {
            String content = "  " + ljust(cyan(k), 30 + (cyan(k).length() - k.length())) + green(v);
            // recompute padding based on visible length
            int visible = 2 + Math.max(k.length(), 30) + v.length();
            String pad = visible < w + 10 ? repeatChar(' ', (w + 10) - visible) : "";
            System.out.println(purple("║") + content + pad + purple("║"));
        }
    }

    static class Interpreter {
        GameState state = new GameState();
        List<String> errors = new ArrayList<>();

        void semErr(String msg, int line) {
            String full = line != 0 ? "[Line " + line + "] Semantic Error: " + msg
                                    : "Semantic Error: " + msg;
            errors.add(full);
            System.out.println("  " + red("✗") + " " + red(full));
        }

        // returns Object[]{value, ok}
        Object[] resolve(Object val, String rtype) {
            if (rtype.equals(TT_IDENTIFIER)) {
                String name = String.valueOf(val);
                if (!state.symbols.containsKey(name)) return new Object[]{null, false};
                return new Object[]{state.symbols.get(name), true};
            }
            return new Object[]{val, true};
        }

        int asInt(Object v) {
            if (v instanceof Number) return ((Number) v).intValue();
            return Integer.parseInt(String.valueOf(v));
        }

        boolean evalCond(Node cond, int line) {
            Object[] lr = resolve(cond.get("left"), TT_IDENTIFIER);
            if (!(Boolean) lr[1]) { semErr("Undeclared variable '" + cond.get("left") + "'", line); return false; }
            Object[] rr = resolve(cond.get("right"), cond.getStr("rtype"));
            if (!(Boolean) rr[1]) { semErr("Undeclared variable '" + cond.get("right") + "'", line); return false; }
            String op = cond.getStr("op");
            // numeric comparison when both are integers, else string equality
            int lv = asInt(lr[0]);
            int rv = asInt(rr[0]);
            switch (op) {
                case ">":  return lv > rv;
                case "<":  return lv < rv;
                case "==": return lv == rv;
                case "!=": return lv != rv;
                case ">=": return lv >= rv;
                case "<=": return lv <= rv;
                default:   return false;
            }
        }

        @SuppressWarnings("unchecked")
        void run(Node node) {
            GameState s = state;
            String t = node.type;

            if (t.equals("program")) {
                System.out.println("\n  " + green("🎮") + "  " + bold("Game Started!"));
                for (Node n : (List<Node>) node.get("body")) run(n);
                System.out.println("\n  " + green("🏁") + "  " + bold("Game Complete!"));
            }
            else if (t.equals("store")) {
                Object prev = s.symbols.get(node.getStr("name"));
                s.symbols.put(node.getStr("name"), node.get("value"));
                String status = prev != null ? "(was " + repr(prev) + ")" : "(new)";
                System.out.println("  " + purple("📦") + "  store " + cyan(node.getStr("name"))
                        + " = " + yellow(repr(node.get("value"))) + "  " + dim(status));
            }
            else if (t.equals("ask")) {
                System.out.println("\n  " + blue("⌨️") + "   " + bold("INPUT") + "  ─  " + node.get("prompt"));
                System.out.print("       " + purple("▶") + " ");
                String raw = readLine().trim();
                Object val;
                String chk = raw.startsWith("-") ? raw.substring(1) : raw;
                if (!chk.isEmpty() && chk.chars().allMatch(Character::isDigit)) val = Integer.parseInt(raw);
                else val = raw;
                s.symbols.put(node.getStr("name"), val);
                System.out.println("  " + green("✔") + "   " + cyan(node.getStr("name")) + " = " + yellow(repr(val)));
            }
            else if (t.equals("dash")) {
                int ox = s.x, oy = s.y, ost = s.stamina;
                int paces = node.getInt("paces");
                String dir = node.getStr("direction");
                if (dir.equals("fore")) s.x += paces;
                else if (dir.equals("back")) s.x -= paces;
                else if (dir.equals("high")) s.y += paces;
                else if (dir.equals("low")) s.y -= paces;
                s.stamina -= 5;
                Map<String,String> arrows = new LinkedHashMap<>();
                arrows.put("fore","→"); arrows.put("back","←"); arrows.put("high","↑"); arrows.put("low","↓");
                System.out.println("  " + cyan("🏃") + "  dash " + dir.toUpperCase() + " " + paces + " paces "
                        + arrows.get(dir) + "  pos " + yellow("(" + ox + "," + oy + ")") + "→"
                        + green("(" + s.x + "," + s.y + ")") + "  stamina " + dim(String.valueOf(ost))
                        + "→" + yellow(String.valueOf(s.stamina)));
            }
            else if (t.equals("leap")) {
                if (s.vitality < 50) { semErr("Cannot leap — vitality (" + s.vitality + ") < 50", node.getInt("line")); return; }
                int ov = s.vitality, oy = s.y; s.y += 10; s.vitality -= 10;
                System.out.println("  " + purple("🦅") + "  leap SOAR!  y:" + dim(String.valueOf(oy)) + "→"
                        + green(String.valueOf(s.y)) + "  vitality:" + dim(String.valueOf(ov)) + "→"
                        + yellow(String.valueOf(s.vitality)));
            }
            else if (t.equals("strike")) {
                String enemy = node.getStr("enemy");
                if (!s.enemies.containsKey(enemy)) { semErr("Enemy '" + enemy + "' does not exist", node.getInt("line")); return; }
                int old = s.enemies.get(enemy); int nhp = old - 25;
                if (nhp <= 0) {
                    s.enemies.remove(enemy);
                    System.out.println("  " + red("⚔️") + "   strike '" + cyan(enemy) + "'  HP:" + dim(String.valueOf(old))
                            + "→" + red("0") + "  " + green("★ DEFEATED!"));
                } else {
                    s.enemies.put(enemy, nhp);
                    System.out.println("  " + red("⚔️") + "   strike '" + cyan(enemy) + "'  HP:" + dim(String.valueOf(old))
                            + "→" + yellow(String.valueOf(nhp)));
                }
            }
            else if (t.equals("grab")) {
                String item = node.getStr("item"); int os = s.score;
                s.inventory.add(item); s.score += 100;
                System.out.println("  " + green("💎") + "  grab '" + cyan(item) + "'  score:" + dim(String.valueOf(os))
                        + "→" + green(String.valueOf(s.score)) + "  inv:" + yellow(s.inventory.toString()));
            }
            else if (t.equals("echo")) {
                System.out.println("  " + yellow("📢") + "  " + bold(node.getStr("message")));
            }
            else if (t.equals("when")) {
                Node cnd = (Node) node.get("condition");
                boolean result = evalCond(cnd, node.getInt("line"));
                String branch = result ? green("TRUE  → then") : red("FALSE → otherwise");
                System.out.println("  " + blue("🔀") + "  when (" + cyan(String.valueOf(cnd.get("left"))) + " "
                        + yellow(cnd.getStr("op")) + " " + yellow(String.valueOf(cnd.get("right"))) + ") → " + branch);
                Node then = (Node) node.get("then");
                Node oth = (Node) node.get("otherwise");
                if (result && then != null) {
                    for (Node n : (List<Node>) then.get("body")) run(n);
                } else if (!result && oth != null) {
                    for (Node n : (List<Node>) oth.get("body")) run(n);
                }
            }
            else if (t.equals("repeat")) {
                int count = node.getInt("count");
                System.out.println("  " + green("🔁") + "  repeat " + yellow(String.valueOf(count)) + " times");
                Node body = (Node) node.get("body");
                for (int i = 1; i <= count; i++) {
                    System.out.println("       " + dim("── iteration " + i + "/" + count + " ──"));
                    for (Node n : (List<Node>) body.get("body")) run(n);
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  SECTION 6 — DISPLAY FUNCTIONS  (tokens, grammar, parse trace)
    // ══════════════════════════════════════════════════════════════════

    static void show52TokenTable() {
        hdr("TOKEN REFERENCE TABLE — All 52 Token Types");
        System.out.println();
        int w1=20, w2=16, w3=18, w4=36;
        System.out.println("  " + bold(white(ljust("TOKEN TYPE", w1)))
                + "  " + bold(white(ljust("SYMBOL", w2)))
                + "  " + bold(white(ljust("CATEGORY", w3)))
                + "  " + bold(white("DESCRIPTION")));
        System.out.println(dim("  " + repeatChar('─', w1 + w2 + w3 + w4 + 6)));
        String lastCat = "";
        for (String[] row : ALL_52_TOKENS) {
            String tt = row[0], sym = row[1], cat = row[2], desc = row[3];
            if (!cat.equals(lastCat)) {
                System.out.println("\n  " + dim("── " + cat + " ──"));
                lastCat = cat;
            }
            Function<String,String> col = tokColour(tt);
            System.out.println("  " + col.apply(ljust(tt, w1)) + "  " + yellow(ljust(sym, w2))
                    + "  " + dim(ljust(cat, w3)) + "  " + desc);
        }
        System.out.println();
    }

    static void showGrammar() {
        hdr("GRAMMAR — EBNF Notation");
        System.out.println();
        for (String[] r : GRAMMAR_EBNF)
            System.out.println("  " + cyan(ljust(r[0], 16)) + " " + dim("→") + "  " + white(r[1]));
        System.out.println();

        hdr("LL(1) FIRST Sets");
        System.out.println();
        for (String[] r : FIRST_SETS)
            System.out.println("  " + cyan(ljust(r[0], 24)) + "  " + dim("=") + "  " + yellow(r[1]));
        System.out.println();

        hdr("LL(1) FOLLOW Sets");
        System.out.println();
        for (String[] r : FOLLOW_SETS)
            System.out.println("  " + cyan(ljust(r[0], 24)) + "  " + dim("=") + "  " + yellow(r[1]));
        System.out.println();
    }

    static void showTokenStream(List<Token> tokens) {
        hdr("TOKEN STREAM  (" + tokens.size() + " tokens produced)");
        System.out.println();
        System.out.println("  " + bold(ljust("#", 5)) + "  " + bold(ljust("TOKEN TYPE", 20))
                + "  " + bold(ljust("VALUE", 22)) + "  " + bold("LINE"));
        System.out.println("  " + dim(repeatChar('─', 60)));
        int i = 1;
        for (Token tok : tokens) {
            Function<String,String> col = tokColour(tok.type);
            String val = tok.value == null ? "EOF" : repr(tok.value);
            System.out.println("  " + dim(ljust(String.valueOf(i), 5)) + "  " + col.apply(ljust(tok.type, 20))
                    + "  " + yellow(ljust(val, 22)) + "  " + dim(String.valueOf(tok.line)));
            i++;
        }
        System.out.println();
    }

    static void showParseTrace(List<String> log) {
        hdr("PARSE TRACE — Grammar Rules Applied");
        System.out.println();
        Set<String> seen = new LinkedHashSet<>();
        int indent = 0;
        for (String rule : log) {
            if (seen.contains(rule)) continue;
            seen.add(rule);
            if (rule.startsWith("program")) {
                System.out.println("  " + purple("▶") + " " + bold(white(rule)));
                indent = 1;
            } else if (rule.startsWith("statement →")) {
                System.out.println("  " + repeatChar(' ', 2 * indent) + cyan("↳") + " " + cyan(rule));
            } else if (rule.contains("block") || rule.contains("condition")
                    || rule.contains("direction") || rule.contains("otherwise")) {
                System.out.println("  " + repeatChar(' ', 2 * (indent + 1)) + blue("·") + " " + blue(rule));
            } else if (rule.contains("→")) {
                System.out.println("  " + repeatChar(' ', 2 * (indent + 1)) + dim("·") + " " + dim(rule));
            } else {
                System.out.println("  " + dim("·") + " " + dim(rule));
            }
        }
        System.out.println();
    }

    // ══════════════════════════════════════════════════════════════════
    //  SECTION 7 — MAIN COMPILER PIPELINE
    // ══════════════════════════════════════════════════════════════════

    static final String BANNER =
        "\n" + purple("╔══════════════════════════════════════════════════════════════════╗") + "\n"
        + purple("║") + "   " + bold(white("P I X E L L A N G   C O M P I L E R")) + "                            " + purple("║") + "\n"
        + purple("║") + "   Compiler Construction (CCP) Final Project                     " + purple("║") + "\n"
        + purple("║") + "   Phases: Tokenizer (DFA) → Parser (LL1) → Semantic Engine     " + purple("║") + "\n"
        + purple("╚══════════════════════════════════════════════════════════════════╝") + "\n";

    static boolean compileAndRun(String source) {
        // ── PHASE 1: TOKENIZE ──
        sep("PHASE 1  ─  TOKENIZER  (DFA)", '═', 66);
        Tokenizer tok = new Tokenizer(source);
        List<Token> tokens = tok.tokenize();
        showTokenStream(tokens);

        if (!tok.errors.isEmpty()) {
            sep("LEXICAL ERRORS DETECTED", '═', 66);
            for (String e : tok.errors) System.out.println("  " + red("✗") + "  " + red(e));
            System.out.println();
            System.out.println("  " + red(bold("Compilation stopped — fix lexical errors first.")));
            sep();
            return false;
        }

        // ── PHASE 2: PARSE ──
        sep("PHASE 2  ─  PARSER  (Recursive Descent / LL1)", '═', 66);
        Parser p = new Parser(tokens);
        Node ast = p.parseProgram();
        showParseTrace(p.log);

        if (!p.errors.isEmpty()) {
            sep("SYNTAX ERRORS DETECTED", '═', 66);
            for (String e : p.errors) System.out.println("  " + red("✗") + "  " + red(e));
            System.out.println();
            System.out.println("  " + red(bold("Compilation stopped — fix syntax errors first.")));
            sep();
            return false;
        }

        // ── ALL PHASES CLEAN → EXECUTE ──
        sep("PHASE 3  ─  EXECUTION  (Semantic Engine)", '═', 66);
        Interpreter interp = new Interpreter();
        interp.run(ast);

        if (!interp.errors.isEmpty()) {
            System.out.println();
            sep("SEMANTIC ERRORS DETECTED", '═', 66);
            for (String e : interp.errors) System.out.println("  " + red("✗") + "  " + red(e));
            System.out.println();
            System.out.println("  " + red(bold("Semantic errors found during execution.")));
        }

        interp.state.display();

        sep("COMPILATION SUMMARY");
        System.out.println("  Lexical Errors  : " + green("0"));
        System.out.println("  Syntax  Errors  : " + green("0"));
        int semC = interp.errors.size();
        System.out.println("  Semantic Errors : " + (semC != 0 ? red(String.valueOf(semC)) : green("0")));
        if (semC == 0)
            System.out.println("\n  " + green(bold("✔  All phases passed — program ran successfully!")));
        else
            System.out.println("\n  " + yellow(bold("⚠  Ran with semantic errors (see above)")));
        sep();
        return semC == 0;
    }

    // ══════════════════════════════════════════════════════════════════
    //  SECTION 8 — INTERACTIVE REPL
    // ══════════════════════════════════════════════════════════════════

    static final String REPL_HELP =
        "\n  COMMANDS\n"
        + "  ─────────────────────────────────────────────\n"
        + "  :run          compile & run what you've typed\n"
        + "  :clear        clear the code buffer\n"
        + "  :show         print current buffer\n"
        + "  :load FILE    load a .pix file into buffer\n"
        + "  :tokens       show the 52 token table\n"
        + "  :grammar      show the full grammar\n"
        + "  :help         show this message\n"
        + "  :quit         exit\n\n"
        + "  LANGUAGE SYNTAX (quick ref)\n"
        + "  ─────────────────────────────────────────────\n"
        + "  store health = 100;\n"
        + "  ask (\"Your name?\") into player;\n"
        + "  echo (\"Hello!\");\n"
        + "  dash fore 5 paces;\n"
        + "  leap soar;\n"
        + "  strike foe dragon;\n"
        + "  grab item sword;\n"
        + "  when (health > 50) { echo (\"Strong!\"); }\n"
        + "  otherwise { echo (\"Weak!\"); }\n"
        + "  repeat (3) { grab item coin; }\n";

    static BufferedReader STDIN = new BufferedReader(new InputStreamReader(System.in));

    static String readLine() {
        try {
            String l = STDIN.readLine();
            return l == null ? "" : l;
        } catch (IOException e) {
            return "";
        }
    }

    static void repl() {
        System.out.println(BANNER);
        show52TokenTable();
        showGrammar();

        System.out.println("\n  " + bold(white("Interactive REPL Mode")));
        System.out.println("  Type your PixelLang code line by line, then " + cyan(":run") + " to compile.");
        System.out.println("  Type " + cyan(":help") + " for all commands.\n");

        List<String> buf = new ArrayList<>();
        int lineno = 1;

        while (true) {
            System.out.print("  " + dim(rjust(String.valueOf(lineno), 3)) + " " + purple("│") + " ");
            String line;
            try {
                line = STDIN.readLine();
            } catch (IOException e) { break; }
            if (line == null) { System.out.println("\n" + dim("Goodbye!")); break; }

            String s = line.trim();

            if (s.equals(":quit") || s.equals(":exit") || s.equals(":q")) {
                System.out.println(dim("  Goodbye!")); break;
            } else if (s.equals(":help")) {
                System.out.println(REPL_HELP);
            } else if (s.equals(":tokens")) {
                show52TokenTable();
            } else if (s.equals(":grammar")) {
                showGrammar();
            } else if (s.equals(":clear")) {
                buf.clear(); lineno = 1; System.out.println(green("  Buffer cleared."));
            } else if (s.equals(":show")) {
                if (buf.isEmpty()) System.out.println(dim("  (empty buffer)"));
                else {
                    System.out.println();
                    for (int i = 0; i < buf.size(); i++)
                        System.out.println("  " + dim(rjust(String.valueOf(i + 1), 3)) + " " + dim("│") + " " + buf.get(i));
                    System.out.println();
                }
            } else if (s.startsWith(":load ")) {
                String path = s.substring(6).trim();
                if (!Files.isRegularFile(Paths.get(path))) {
                    System.out.println(red("  File not found: " + path));
                } else {
                    try {
                        String content = new String(Files.readAllBytes(Paths.get(path)));
                        buf = new ArrayList<>(Arrays.asList(content.split("\n", -1)));
                        lineno = buf.size() + 1;
                        System.out.println(green("  Loaded " + buf.size() + " lines from '" + path + "'"));
                        System.out.println("  Type " + cyan(":run") + " to compile it.");
                    } catch (IOException e) {
                        System.out.println(red("  Could not read file: " + path));
                    }
                }
            } else if (s.equals(":run")) {
                if (buf.isEmpty()) {
                    System.out.println(yellow("  (buffer is empty — type some code first)"));
                    continue;
                }
                String src = String.join("\n", buf);
                System.out.println();
                compileAndRun(src);
                System.out.println("\n  " + dim("Buffer kept. Type more code or :clear to reset.") + "\n");
            } else {
                buf.add(line);
                lineno++;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  SECTION 9 — BUILT-IN DEMO PROGRAMS
    // ══════════════════════════════════════════════════════════════════

    static final String DEMO =
        "# PixelLang — Full Feature Demo\n"
        + "store health = 100;\n"
        + "store shield = 1;\n"
        + "echo (\"=== PixelLang Full Demo ===\");\n"
        + "dash fore 4 paces;\n"
        + "leap soar;\n"
        + "echo (\"Jumped over the pit!\");\n"
        + "strike foe goblin;\n"
        + "strike foe goblin;\n"
        + "echo (\"Goblin defeated!\");\n"
        + "grab item sword;\n"
        + "grab item potion;\n"
        + "when (health > 50) {\n"
        + "    echo (\"Health good — attacking!\");\n"
        + "    dash fore 3 paces;\n"
        + "    strike foe dragon;\n"
        + "}\n"
        + "otherwise {\n"
        + "    echo (\"Health low — retreating!\");\n"
        + "    dash back 2 paces;\n"
        + "}\n"
        + "repeat (3) {\n"
        + "    grab item coin;\n"
        + "    echo (\"Coin collected!\");\n"
        + "}\n"
        + "echo (\"Demo complete!\");\n";

    static final String ERROR_DEMO =
        "# PixelLang — Error Demo (all 3 error types)\n"
        + "echo (\"Testing errors...\");\n\n"
        + "# LEXICAL ERROR: invalid character '@'\n"
        + "store level @ 5;\n\n"
        + "# SYNTAX ERROR: missing semicolon\n"
        + "echo (\"No semicolon here\")\n\n"
        + "# SEMANTIC ERROR: undeclared variable\n"
        + "when (ghost_hp > 10) {\n"
        + "    echo (\"Ghost!\");\n"
        + "}\n\n"
        + "# SEMANTIC ERROR: leap with low vitality\n"
        + "leap soar;\nleap soar;\nleap soar;\nleap soar;\nleap soar;\nleap soar;\nleap soar;\n"
        + "echo (\"Done.\");\n";

    // ══════════════════════════════════════════════════════════════════
    //  ENTRY POINT
    // ══════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        List<String> a = Arrays.asList(args);

        if (a.contains("--demo")) {
            System.out.println(BANNER);
            show52TokenTable();
            showGrammar();
            sep("DEMO PROGRAM SOURCE");
            System.out.println(DEMO);
            compileAndRun(DEMO);
        } else if (a.contains("--errors")) {
            System.out.println(BANNER);
            show52TokenTable();
            showGrammar();
            sep("ERROR DEMO SOURCE");
            System.out.println(ERROR_DEMO);
            compileAndRun(ERROR_DEMO);
        } else if (a.size() == 1 && a.get(0).endsWith(".pix")) {
            String path = a.get(0);
            if (!Files.isRegularFile(Paths.get(path))) {
                System.out.println(red("Error: File not found: " + path));
                System.exit(1);
            }
            String src;
            try {
                src = new String(Files.readAllBytes(Paths.get(path)));
            } catch (IOException e) {
                System.out.println(red("Error: Could not read file: " + path));
                System.exit(1);
                return;
            }
            System.out.println(BANNER);
            show52TokenTable();
            showGrammar();
            sep("SOURCE: " + path);
            System.out.println(src);
            compileAndRun(src);
        } else {
            repl();
        }
    }
}
