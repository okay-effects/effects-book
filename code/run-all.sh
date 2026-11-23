#!/bin/sh
# Runs every rendering of the one scenario and compares what it prints with code/expected.txt.
# Lines 1-5 must be the same everywhere. Line 6 (the rest of the program resumed twice) is either the expected
# line or begins "6 twice: cannot:" and says why that technique cannot do it.
# Then the laws of chapter 4: every rendering's twelve lines against code/laws-expected.txt, the programs that
# must not compile, the laws on generated programs (Scala), and the theorems (Lean).
# Usage: sh code/run-all.sh        (from anywhere; a missing toolchain is reported and skipped)

here=$(cd "$(dirname "$0")" && pwd)
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
PATH="$HOME/.elan/bin:$PATH"
failed=0

check() { # name, tool, command...
  name=$1; tool=$2; shift 2
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "skipped  $name ($tool is not installed)"; return
  fi
  if ! "$@" >"$out/lines" 2>"$out/errors"; then
    echo "FAILED   $name (did not run)"; sed 's/^/         /' "$out/errors" | tail -5; failed=1; return
  fi
  grep -v '^6 ' "$out/lines" >"$out/common"
  grep -v '^6 ' "$here/expected.txt" >"$out/wanted"
  if ! diff "$out/wanted" "$out/common" >"$out/diff"; then
    echo "FAILED   $name (lines 1-5 differ)"; sed 's/^/         /' "$out/diff"; failed=1; return
  fi
  last=$(grep '^6 ' "$out/lines")
  if [ "$last" = "$(grep '^6 ' "$here/expected.txt")" ]; then
    echo "same     $name"
  else
    case $last in
      "6 twice: cannot: "*) echo "same 1-5 $name -- $last" ;;
      *) echo "FAILED   $name (line 6: $last)"; failed=1 ;;
    esac
  fi
}

scala() { command scala-cli run "$here/scala" --server=false -q --main-class "$1"; }
rust() { rustc --edition 2021 -O "$here/rust/bank.rs" -o "$out/bank-rs" && "$out/bank-rs"; }
kotlin() { kotlinc "$here/kotlin/bank.kt" -include-runtime -d "$out/bank-kt.jar" && java -jar "$out/bank-kt.jar"; }
koka_() { koka -v0 --builddir="$out/koka" -o "$out/bank-koka" "$here/koka/bank.kk" >/dev/null && "$out/bank-koka"; }
zig_() { ZIG_LOCAL_CACHE_DIR="$out/zig" ZIG_GLOBAL_CACHE_DIR="$out/zig-global" zig run "$here/zig/bank.zig"; }
csharp() { DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1 dotnet run "$here/csharp/bank.cs"; }

check "scala: a tree and closures (kernel.scala)"      scala-cli scala book.bank
check "scala: capabilities as context parameters"      scala-cli scala book.capabilities
check "java: a sealed tree and an interpreter"         java      java "$here/java/Tree.java"
check "java: direct style on virtual threads"          java      java "$here/java/Loom.java"
check "rust: a state machine that is Clone"            rustc     rust
check "typescript: generators"                         node      node "$here/typescript/bank.ts"
check "python: generators"                             python3   python3 -I "$here/python/bank.py"
check "haskell: a tree over a GADT"                    runghc    runghc "$here/haskell/Bank.hs"
check "haskell: tagless final"                         runghc    runghc "$here/haskell/Tagless.hs"
check "lean: a tree over an indexed family"            lean      lean --run "$here/lean/Bank.lean"
check "go: direct style on goroutines"                 go        go run "$here/go/bank.go"
check "kotlin: suspend functions, the continuation"    kotlinc   kotlin
check "c#: an iterator state machine"                  dotnet    csharp
check "zig: a state machine copied by value"           zig       zig_
check "ocaml 5: the language's effect handlers"        ocaml     ocaml "$here/ocaml/bank.ml"
check "koka: effect types and handlers"                koka      koka_

# Chapter 3i: one implementation grown in four stages (code/scala/path). Stages 3 and 4 print the scenario.
check "scala, the path, stage 3: rows"                 scala-cli scala book.path.stage3.stage3
check "scala, the path, stage 4: more constructors"    scala-cli scala book.path.stage4.stage4

# The stages that are not renderings of the scenario assert what they claim; they must run and exit 0.
holds() { # name, main
  if ! command -v scala-cli >/dev/null 2>&1; then
    echo "skipped  $1 (scala-cli is not installed)"; return
  fi
  if scala "$2" >"$out/lines" 2>"$out/errors"; then
    echo "holds    $1"
  else
    echo "FAILED   $1"; tail -5 "$out/lines" "$out/errors" | sed 's/^/         /'; failed=1
  fi
}

holds "scala, the path, stage 1: a free monad with a trampoline" book.path.stage1.stage1
holds "scala, the path, stage 2: the handler receives the rest"  book.path.stage2.stage2
holds "scala, the path, stage 4: why each addition"              book.path.stage4.forced

# Chapter 8: the catalogue of standard effects, on the forty lines. Every listing of the chapter is an assertion.
holds "scala: chapter 8, the catalogue of standard effects"      book.catalogue

# Chapter 9: a screen, a store and a server as effects, and one application under three stacks of handlers.
holds "scala: chapter 9, screens, servers and stores"            book.applications


# ---- Chapter 4: the laws. Every rendering prints the twelve lines of code/laws-expected.txt. A line a
# technique cannot express begins "<n> <name>: cannot: " and says why; line 7, where the types refuse the
# program, begins "7 nobody answers: does not compile: ". Any other difference is a failure.
law() { # name, tool, command...
  name=$1; tool=$2; shift 2
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "skipped  $name ($tool is not installed)"; return
  fi
  if ! "$@" >"$out/lines" 2>"$out/errors"; then
    echo "FAILED   $name (did not run)"; sed 's/^/         /' "$out/errors" | tail -5; failed=1; return
  fi
  if [ "$(wc -l <"$out/lines")" -ne "$(wc -l <"$here/laws-expected.txt")" ]; then
    echo "FAILED   $name (not twelve lines)"; sed 's/^/         /' "$out/lines"; failed=1; return
  fi
  cannot=""
  bad=0
  while IFS= read -r wanted <&3 && IFS= read -r got <&4; do
    [ "$wanted" = "$got" ] && continue
    n=${wanted%% *}
    label=${wanted%%:*}
    case $got in
      "$label: cannot: "*) cannot="$cannot $n cannot;" ;;
      "7 nobody answers: does not compile: "*) cannot="$cannot 7 does not compile;" ;;
      *) echo "FAILED   $name (law $n: $got)"; bad=1 ;;
    esac
  done 3<"$here/laws-expected.txt" 4<"$out/lines"
  if [ $bad -eq 1 ]; then failed=1
  elif [ -z "$cannot" ]; then echo "laws     $name"
  else echo "laws     $name --$cannot"
  fi
}

refused() { # name, tool, words the compiler must say, command that must fail...
  name=$1; tool=$2; words=$3; shift 3
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "skipped  $name ($tool is not installed)"; return
  fi
  if "$@" >"$out/lines" 2>&1; then
    echo "FAILED   $name (it compiled)"; failed=1
  elif grep -q "$words" "$out/lines"; then
    echo "refused  $name"
  else
    echo "FAILED   $name (refused, but not for \"$words\")"; tail -5 "$out/lines" | sed 's/^/         /'; failed=1
  fi
}

rust_l() { rustc --edition 2021 -O "$here/rust/laws.rs" -o "$out/laws-rs" && "$out/laws-rs"; }
kotlin_l() { kotlinc "$here/kotlin/laws.kt" -include-runtime -d "$out/laws-kt.jar" && java -jar "$out/laws-kt.jar"; }
koka_l() { koka -v0 --builddir="$out/koka" -o "$out/laws-koka" "$here/koka/laws.kk" >/dev/null && "$out/laws-koka"; }
zig_l() { ZIG_LOCAL_CACHE_DIR="$out/zig" ZIG_GLOBAL_CACHE_DIR="$out/zig-global" zig run "$here/zig/laws.zig"; }
csharp_l() { DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1 dotnet run "$here/csharp/laws.cs"; }
ghc_no() { ghc -fno-code -outputdir "$out/ghc" "$here/haskell/TaglessUnanswered.hs"; }
koka_no() { koka -v0 --builddir="$out/koka-no" -c "$here/koka/unanswered.kk"; }
kotlin_no() { kotlinc "$here/kotlin/unanswered.kt" -d "$out/kotlin-no"; }

law "scala: a tree and closures (kernel.scala)"        scala-cli scala book.laws.laws
law "scala: capabilities as context parameters"        scala-cli scala book.laws.capabilities
law "java: a sealed tree and an interpreter"           java      java "$here/java/TreeLaws.java"
law "java: direct style on virtual threads"            java      java "$here/java/LoomLaws.java"
law "rust: a state machine that is Clone"              rustc     rust_l
law "typescript: generators"                           node      node "$here/typescript/laws.ts"
law "python: generators"                               python3   python3 -I "$here/python/laws.py"
law "haskell: a tree over a GADT"                      runghc    runghc "$here/haskell/Laws.hs"
law "haskell: tagless final"                           runghc    runghc "$here/haskell/TaglessLaws.hs"
law "lean: a tree over an indexed family"              lean      lean --run "$here/lean/Laws.lean"
law "go: direct style on goroutines"                   go        go run "$here/go/laws.go"
law "kotlin: suspend functions, the continuation"      kotlinc   kotlin_l
law "c#: an iterator state machine"                    dotnet    csharp_l
law "zig: a state machine copied by value"             zig       zig_l
law "ocaml 5: the language's effect handlers"          ocaml     ocaml "$here/ocaml/laws.ml"
law "koka: effect types and handlers"                  koka      koka_l
law "scala, the path, stage 4"                         scala-cli scala book.path.stage4.laws

# Line 7 where it is the compiler that refuses: the program with its handler missing must not compile, and
# for that reason. (The two Scala files ask their own compiler, inside the main, with typeCheckErrors.)
refused "haskell, tagless: the clock left unanswered"  ghc     "No instance for"     ghc_no
refused "koka: the clock left unanswered"              koka    "unhandled effect"    koka_no
refused "kotlin: the clock left unanswered"            kotlinc "no context argument" kotlin_no

# The same laws over generated programs, and a proof beside the tests.
holds "scala: the laws on 1,000 generated programs, the forty lines" book.laws.generated
holds "scala: the laws on 1,000 generated programs, stage 4"         book.path.stage4.generated

# Lean reads the theorems of code/lean/Laws.lean when it reads the file: no message means every proof checked.
if command -v lean >/dev/null 2>&1; then
  if lean "$here/lean/Laws.lean" >"$out/lines" 2>&1 && [ ! -s "$out/lines" ]; then
    echo "proved   lean: $(grep -c '^theorem' "$here/lean/Laws.lean") theorems of code/lean/Laws.lean"
  else
    echo "FAILED   lean: the theorems of code/lean/Laws.lean"; tail -5 "$out/lines" | sed 's/^/         /'; failed=1
  fi
else
  echo "skipped  lean: the theorems of code/lean/Laws.lean (lean is not installed)"
fi

exit $failed
