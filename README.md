# PixelLang Compiler

[![CI](https://github.com/MuhammadSaadAsad20/pixellang-compiler/actions/workflows/ci.yml/badge.svg)](https://github.com/MuhammadSaadAsad20/pixellang-compiler/actions/workflows/ci.yml)

A compiler and interpreter for PixelLang, a small language for scripting a game
character, written from scratch in a single Java file with no external libraries.
Built as my Compiler Construction final project.

Each program goes through three phases, and the output shows what every phase did:

1. **Tokenizer (DFA):** turns source text into a token table, across 52 token types.
2. **Parser (LL(1)):** checks the program against the grammar and prints a parse trace.
3. **Semantic engine:** reports errors and stops, or runs the program and prints the
   final game state (position, vitality, stamina, score, inventory, enemies and variables).

Lexical, syntax and semantic errors are reported with line numbers.

## Example

```
store health = 100;
dash fore 4 paces;
strike foe goblin;

when (health > 50) {
    echo ("Health good - attacking!");
    strike foe dragon;
}
otherwise {
    dash back 2 paces;
}

repeat (3) {
    grab item coin;
}
```

## Running it

Requires a JDK (tested on Java 21).

```
javac -encoding UTF-8 PixelLang.java
java PixelLang --demo              # built-in demo
java PixelLang --errors            # built-in demo of error reporting
java PixelLang examples/demo.pix   # compile a file
java PixelLang                     # interactive mode
```
