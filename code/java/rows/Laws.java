// The laws of code/laws-expected.txt on Fx.java, the tree with the row in the type (chapter 3j). Each line runs
// both sides of a law and prints what was seen. A handler's state is its fold's, a value passed to the rest,
// so the rest gone on with twice has two. Line 7 is the compiler's: refused/NobodyAnswers.java must not compile.
// `java code/java/rows/Laws.java`
package rows;

import static rows.Fx.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class Laws {
  sealed interface State {
    record Get() implements State, Op<Long, State> {
      public String toString() { return "Get"; }
    }
    record Put(long n) implements State, Op<Void, State> {
      public String toString() { return "Put(" + n + ")"; }
    }
  }
  sealed interface Refusal {
    record Refuse(String why) implements Refusal, Op<Void, Refusal> {}
  }
  sealed interface Clock {
    record Now() implements Clock, Op<Long, Clock> {
      public String toString() { return "Now"; }
    }
  }
  sealed interface Choice {
    record Choose(List<Long> options) implements Choice, Op<Long, Choice> {}
  }

  // ---- the handlers of the chapter

  /** a value with the cell's content after it */
  record Held<A>(A value, long cell) {
    public String toString() { return value + ", cell " + cell; }
  }
  /** the state: the cell's content is the fold's state */
  static <A, R> Handler<State, Long, A, Held<A>, R> cell(long initial) {
    return new Handler<>(State.class, initial, (s, a) -> done(new Held<>(a, s)), op -> switch (op) {
      case State.Get g -> on(g, (s, k) -> k.apply(s, s));
      case State.Put p -> on(p, (s, k) -> k.apply(null, p.n()));
    });
  }

  sealed interface Outcome<A> {}
  record Paid<A>(A value) implements Outcome<A> {
    public String toString() { return "paid(" + value + ")"; }
  }
  record Refused<A>(String why) implements Outcome<A> {
    public String toString() { return "refused(" + why + ")"; }
  }
  /** zero resumptions: the rest is left */
  static <A, R> Handler<Refusal, Void, A, Outcome<A>, R> refusals() {
    return new Handler<>(Refusal.class, null, (none, a) -> done(new Paid<>(a)), op -> switch (op) {
      case Refusal.Refuse r -> on(r, (none, k) -> done(new Refused<>(r.why())));
    });
  }

  static <A, R> Handler<Clock, Void, A, A, R> fixedClock(long now) {
    return handling(Clock.class, op -> switch (op) {
      case Clock.Now n -> answer(n, now);
    });
  }

  /** many resumptions: every option taken, the rest gone on with once for each */
  static <A, R> Handler<Choice, Void, A, List<A>, R> allChoices() {
    return new Handler<>(Choice.class, null, (none, a) -> done(List.of(a)), op -> switch (op) {
      case Choice.Choose c -> on(c, (none, k) -> {
        Prog<List<A>, R> all = done(List.of());
        for (long o : c.options())
          all = all.then(seen -> k.apply(o, none).then(more -> {
            var both = new ArrayList<>(seen);
            both.addAll(more);
            return done(both);
          }));
        return all;
      });
    });
  }

  /** a value with the questions of the state asked on the way to it */
  record Logged<A>(List<String> lines, A value) {
    Logged<A> after(String line) {
      var all = new ArrayList<>(List.of(line));
      all.addAll(lines);
      return new Logged<>(all, value);
    }
    public String toString() { return String.join(" ", lines) + " => " + value; }
  }
  /** the observer: every question of the state asked again of the state around it, and written down with its
   * answer. It asks State, so it goes only inside a handler of State */
  static <A, R extends State> Handler<State, Void, A, Logged<A>, R> log() {
    return new Handler<>(State.class, null, (none, a) -> done(new Logged<>(List.of(), a)), op -> switch (op) {
      case State.Get g -> on(g, (none, k) -> ask(g)
          .then(x -> k.apply(x, none).then(later -> done(later.after("Get=" + x)))));
      case State.Put p -> on(p, (none, k) -> ask(p)
          .then(x -> k.apply(x, none).then(later -> done(later.after(p.toString())))));
    });
  }

  // ---- the programs of the twelve lines; each row says what the program may ask

  static <R extends State> Prog<Long, R> put(long n) { return ask(new State.Put(n)).then(u -> done(n)); }
  static <R extends State> Prog<Long, R> get() { return ask(new State.Get()); }
  static <R extends State> Prog<Long, R> bump() {
    return get().then(x -> ask(new State.Put(x + 1)).then(u -> done(x)));
  }
  static <R extends State & Refusal> Prog<Long, R> stopped() {
    return ask(new State.Put(1)).then(u -> get())
        .then(x -> ask(new Refusal.Refuse("stop at " + x))).then(never -> put(9));
  }
  static <R extends State & Refusal> Prog<Long, R> lost() {
    return ask(new State.Put(1)).then(u -> ask(new Refusal.Refuse("no")))
        .then(never -> done(0L));
  }
  static <R extends State & Clock> Prog<Long, R> timed() {
    return ask(new Clock.Now())
        .then(t -> ask(new State.Put(t)).then(u -> get()).then(x -> done(x + t)));
  }
  static <R extends State & Choice> Prog<Long, R> forked() {
    return ask(new State.Put(1)).then(u -> ask(new Choice.Choose(List.of(10L, 20L))))
        .then(x -> get().then(y -> ask(new State.Put(y + x)))).then(u -> get());
  }

  /** `p under cell(s)`, as a program */
  static <A> Prog<Held<A>, Object> cell(long s, Prog<A, State> p) {
    return Fx.<Held<A>>top().handler(Laws.<A, Object>cell(s)).handle(p);
  }
  /** what a program over the state is seen to do: its questions with their answers, its value, the cell after */
  static String seen(Prog<Long, State> p) {
    return run(Fx.<Held<Logged<Long>>>top().handler(cell(0)).handler(log()).handle(p)).toString();
  }
  static String shown(List<?> all) { return String.join(" | ", all.stream().map(Object::toString).toList()); }

  public static void main(String[] args) {
    // 1-3. sequencing: done is a unit on each side, and grouping does not matter
    System.out.println("1 done then f: " + seen(Fx.<Long, State>done(5L).then(Laws::put)) + " = " + seen(put(5)));
    System.out.println("2 p then done: " + seen(bump().then(Fx::done)) + " = " + seen(bump()));
    Function<Long, Prog<Long, State>> f = x -> put(x + 1),
        g = y -> ask(new State.Put(y * 2)).then(u -> done(y));
    System.out.println("3 grouping: " + seen(get().then(f).then(g))
        + " = " + seen(get().then(x -> f.apply(x).then(g))));

    // 4-5. a handler is a fold: what it makes of done, and of one program after another
    System.out.println("4 handling done: " + run(cell(3, done(7L)))
        + " | " + run(Fx.<Outcome<Long>>top().handler(refusals()).handle(done(7L))));
    var whole = cell(0, bump().then(a -> bump().then(b -> done(a + b))));
    var pieces = cell(0, bump()).then(first ->
        cell(first.cell(), bump()).then(second -> done(new Held<>(first.value() + second.value(), second.cell()))));
    System.out.println("5 handling a sequence: " + run(whole) + " = " + run(pieces));

    // 6. a handler sees every operation of its effect, in order, and nothing of another's
    System.out.println("6 its own and no other's: "
        + run(Fx.<Held<Logged<Outcome<Long>>>>top().handler(cell(0)).handler(log()).handler(refusals())
            .handle(stopped())));

    // 7. a question nobody answers: the program does not compile (refused/NobodyAnswers.java)
    System.out.println("7 nobody answers: does not compile: run takes only a program with nothing left to ask, "
        + "and Now is left in this one");

    // 8. two effects whose order is the meaning: is the cell part of what a refusal throws away?
    System.out.println("8 state then failure: "
        + run(Fx.<Outcome<Held<Long>>>top().handler(refusals()).handler(cell(0)).handle(lost()))
        + " | failure then state: "
        + run(Fx.<Held<Outcome<Long>>>top().handler(cell(0)).handler(refusals()).handle(lost())));

    // 9. two handlers that only answer: either order
    System.out.println("9 two that only answer: "
        + run(Fx.<Held<Long>>top().handler(cell(0)).handler(fixedClock(7)).handle(timed()))
        + " = " + run(Fx.<Held<Long>>top().handler(fixedClock(7)).handler(cell(0)).handle(timed())));

    // 10-11. the rest gone on with twice: a state inside the choice is each way's own, outside it is one
    System.out.println("10 twice, state inside: "
        + shown(run(Fx.<List<Held<Long>>>top().handler(allChoices()).handler(cell(0)).handle(forked()))));
    var outside = run(Fx.<Held<List<Long>>>top().handler(cell(0)).handler(allChoices()).handle(forked()));
    System.out.println("11 twice, state outside: " + shown(outside.value()) + ", cell " + outside.cell());

    // 12. what this handler owes its own operations: the four equations of a state
    var afterPut = run(cell(0, ask(new State.Put(3)).then(u -> get()))).value();
    var lastPut = run(cell(0, ask(new State.Put(3))
        .then(u -> ask(new State.Put(4))).then(u -> get()))).value();
    var twice = run(cell(0, get().then(x -> get().then(y -> done(x + "=" + y))))).value();
    var putBack = run(cell(5, get().then(x -> ask(new State.Put(x))))).cell();
    System.out.println("12 the cell's own: " + afterPut + " " + lastPut + " " + twice + " cell " + putBack);
  }
}
