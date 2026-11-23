# Programs That Ask

A book, in progress: effects as the architecture and the control of software — every chapter drafted, every
example run. Written in the open; comments and corrections welcome as issues.

The book is tied to no implementation and no language. The idea is stated in a notation of its own (chapter 2)
and then exactly (chapter 2b): algebraic effects are operations as algebraic data together with the rest of
the program as a delimited continuation. It is built once in full (chapter 3), laid out as every way it can be
built, language by language, with what each gives and costs (Part III, chapters 3a to 3h), and used as the
ruler for everything else engineers use for the same problems (chapter 5, the map). Chapter 16 returns to it.
One implementation, the author's free monad with rows in Scala, is grown from chapter 3's forty lines, its
stage 0, in four more stages that run (chapter 3i, `code/scala/path/`): the reference demonstration, with the
other renderings as its comparisons. What must hold of any of them is chapter 4, the laws: stated in the
notation, printed as twelve lines by every rendering, asked of generated programs, and proved in Lean for
the tree. Chapter 8 is the catalogue of standard effects: ten of them, each with what its handlers do with
the rest of the program, what they owe and what goes wrong, every listing an assertion on the forty lines.
Chapter 9 builds a screen, a store and a server as effects in miniature, says beside each what the author's
library does and what the usual tool does well, and runs one application under three stacks of handlers.

- `PLAN.md` — every chapter, its thesis, its sources and its state; the rules the book is written under.
- `BACKLOG.md` — what is not written or not settled.
- `chapters/` — the chapters, all drafted. Chapters 6 to 12 show their listings in the book's
  notation; each section names the file under `code/` that checks it.
- `code/` — one small program written sixteen times in thirteen languages, each by a technique natural to its
  language, each printing the same lines: `sh code/run-all.sh` runs them all and compares. `code/README.md` has
  the table, the one command for each, and the toolchains. The same script checks the laws of chapter 4:
  `code/laws-expected.txt` against a law file beside each rendering, three programs that must not compile,
  the laws on generated programs (`code/scala/laws/`) and the theorems of `code/lean/Laws.lean`.
  The examples of the later chapters are under
  `code/scala/`, with assertions (`scala-cli run code/scala --main-class book.examples`, `book.control`,
  `book.continuation`, `book.stopping`, `book.transaction`, `book.agents`, and `book.catalogue` for chapter
  8 and `book.applications` for chapter 9, which `run-all.sh` runs too).
- "Note NN" in a chapter is one of the author's own notes (drafts of posts), kept private; a number taken from
  one says so beside it. The inventory of the chapters before their restructuring of 2026-11-18 is private too.

## Licence

The text (everything but `code/`): [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) — share and adapt
it, with credit to the author. The code under `code/`: Apache-2.0 (`code/LICENSE`).

---

The author's libraries: [okay-effects](https://okay-effects.github.io/) · Need more? [okay-effects.github.io/#author](https://okay-effects.github.io/#author)
