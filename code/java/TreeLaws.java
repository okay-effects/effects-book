// The laws of code/laws-expected.txt in Java, as PROGRAMS AS DATA: the sealed tree and the interpreter of
// Tree.java (chapter 3b), and twelve observations on them (chapter 4). Each line runs both sides of a law and
// prints what was seen. No handler here has a variable: what a handler remembers is in what it returns, and so
// the rest of the program can be called twice and each call has its own.
// `java code/java/TreeLaws.java`

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class TreeLaws {
  /** an operation: a question as data; X is the type of its answer */
  sealed interface Op<X> {}
  record Get() implements Op<Long> {
    public String toString() { return "Get"; }
  }
  record Put(long n) implements Op<Void> {
    public String toString() { return "Put(" + n + ")"; }
  }
  record Refuse(String why) implements Op<Void> {}
  record Now() implements Op<Long> {
    public String toString() { return "Now"; }
  }
  record Choose(List<Long> options) implements Op<Long> {}

  /** a program: done, or a question with the rest */
  sealed interface Prog<A> {
    default <B> Prog<B> then(Function<A, Prog<B>> f) {
      return switch (this) {
        case Done<A> d -> f.apply(d.value());
        case Ask<?, A> a -> a.followedBy(f);
      };
    }
  }
  record Done<A>(A value) implements Prog<A> {}
  record Ask<X, A>(Op<X> op, Function<X, Prog<A>> rest) implements Prog<A> {
    <B> Prog<B> followedBy(Function<A, Prog<B>> f) { return new Ask<>(op, x -> rest.apply(x).then(f)); }
  }
  static <X> Prog<X> ask(Op<X> op) { return new Ask<>(op, Done::new); }
  static <A> Prog<A> done(A a) { return new Done<>(a); }

  /** a handler: empty means "not my question". A switch on the operation does not tell Java what X is, so the
   * answer is passed through `answer`, one cast, here and nowhere else */
  interface Handler<A, B> {
    Prog<B> done(A a);
    <X> Optional<Prog<B>> on(Op<X> op, Function<X, Prog<B>> rest);
  }
  @SuppressWarnings("unchecked")
  static <X, B> Prog<B> answer(Function<X, Prog<B>> rest, Object x) { return rest.apply((X) x); }

  static <A, B> Prog<B> handle(Handler<A, B> h, Prog<A> p) {
    return switch (p) {
      case Done<A> d -> h.done(d.value());
      case Ask<?, A> a -> step(h, a);
    };
  }
  static <X, A, B> Prog<B> step(Handler<A, B> h, Ask<X, A> a) {
    Function<X, Prog<B>> handled = x -> handle(h, a.rest().apply(x));
    return h.on(a.op(), handled).orElseGet(() -> new Ask<>(a.op(), handled));
  }
  static <A> A run(Prog<A> p) {
    return switch (p) {
      case Done<A> d -> d.value();
      case Ask<?, A> a -> throw new IllegalStateException("nobody answers " + a.op());
    };
  }

  // ---- the handlers of the chapter, and what each makes of a program

  /** a value with the cell's content after it */
  record Held<A>(A value, long cell) {
    public String toString() { return value + ", cell " + cell; }
  }
  /** the state: a handled program is a function from the cell's content to the program that goes on from it */
  interface From<A> extends Function<Long, Prog<Held<A>>> {}
  record Cell<A>() implements Handler<A, From<A>> {
    public Prog<From<A>> done(A a) { return new Done<>(s -> new Done<>(new Held<>(a, s))); }
    public <X> Optional<Prog<From<A>>> on(Op<X> op, Function<X, Prog<From<A>>> rest) {
      return switch (op) {
        case Get g -> Optional.of(new Done<>(s -> answer(rest, s).then(from -> from.apply(s))));
        case Put p -> Optional.of(new Done<>(s -> answer(rest, null).then(from -> from.apply(p.n()))));
        default -> Optional.empty();
      };
    }
  }
  /** `p under Cell(s)` */
  static <A> Prog<Held<A>> cell(long s, Prog<A> p) { return handle(new Cell<>(), p).then(from -> from.apply(s)); }

  sealed interface Outcome<A> {}
  record Paid<A>(A value) implements Outcome<A> {
    public String toString() { return "paid(" + value + ")"; }
  }
  record Refused<A>(String why) implements Outcome<A> {
    public String toString() { return "refused(" + why + ")"; }
  }
  /** zero resumptions: the rest is not called */
  record Refusals<A>() implements Handler<A, Outcome<A>> {
    public Prog<Outcome<A>> done(A a) { return new Done<>(new Paid<>(a)); }
    public <X> Optional<Prog<Outcome<A>>> on(Op<X> op, Function<X, Prog<Outcome<A>>> rest) {
      return op instanceof Refuse r ? Optional.of(new Done<>(new Refused<>(r.why()))) : Optional.empty();
    }
  }
  static <A> Prog<Outcome<A>> refusals(Prog<A> p) { return handle(new Refusals<>(), p); }

  record FixedClock<A>(long now) implements Handler<A, A> {
    public Prog<A> done(A a) { return new Done<>(a); }
    public <X> Optional<Prog<A>> on(Op<X> op, Function<X, Prog<A>> rest) {
      return op instanceof Now ? Optional.of(answer(rest, now)) : Optional.empty();
    }
  }
  static <A> Prog<A> fixedClock(long now, Prog<A> p) { return handle(new FixedClock<>(now), p); }

  /** many resumptions: every option taken, the rest called once for each */
  record AllChoices<A>() implements Handler<A, List<A>> {
    public Prog<List<A>> done(A a) { return new Done<>(List.of(a)); }
    public <X> Optional<Prog<List<A>>> on(Op<X> op, Function<X, Prog<List<A>>> rest) {
      if (!(op instanceof Choose c)) return Optional.empty();
      Prog<List<A>> all = new Done<>(List.of());
      for (long o : c.options())
        all = all.then(seen -> answer(rest, o).then(more -> {
          var both = new ArrayList<>(seen);
          both.addAll(more);
          return new Done<List<A>>(both);
        }));
      return Optional.of(all);
    }
  }
  static <A> Prog<List<A>> allChoices(Prog<A> p) { return handle(new AllChoices<>(), p); }

  /** a value with the questions of the state that were asked on the way to it */
  record Logged<A>(List<String> lines, A value) {
    Logged<A> after(String line) {
      var all = new ArrayList<>(List.of(line));
      all.addAll(lines);
      return new Logged<>(all, value);
    }
    public String toString() { return String.join(" ", lines) + " => " + value; }
  }
  /** the observer, an interceptor: every question of the state asked again for whoever is outside, and
   * written down with its answer */
  record Log<A>() implements Handler<A, Logged<A>> {
    public Prog<Logged<A>> done(A a) { return new Done<>(new Logged<>(List.of(), a)); }
    public <X> Optional<Prog<Logged<A>>> on(Op<X> op, Function<X, Prog<Logged<A>>> rest) {
      if (!(op instanceof Get || op instanceof Put)) return Optional.empty();
      return Optional.of(new Ask<>(op, x ->
          rest.apply(x).then(later -> new Done<>(later.after(op instanceof Get ? "Get=" + x : op.toString())))));
    }
  }
  static <A> Prog<Logged<A>> log(Prog<A> p) { return handle(new Log<>(), p); }

  // ---- the programs of the twelve lines

  static Prog<Long> put(long n) { return ask(new Put(n)).then(unit -> done(n)); }
  static Prog<Long> get() { return ask(new Get()); }
  static Prog<Long> bump() { return get().then(x -> ask(new Put(x + 1)).then(unit -> done(x))); }
  static Prog<Long> stopped() {
    return ask(new Put(1)).then(unit -> get()).then(x -> ask(new Refuse("stop at " + x))).then(never -> put(9));
  }
  static Prog<Long> lost() {
    return ask(new Put(1)).then(unit -> ask(new Refuse("no"))).then(never -> done(0L));
  }
  static Prog<Long> timed() {
    return ask(new Now()).then(t -> ask(new Put(t)).then(unit -> get()).then(x -> done(x + t)));
  }
  static Prog<Long> forked() {
    return ask(new Put(1)).then(unit -> ask(new Choose(List.of(10L, 20L))))
        .then(x -> get().then(y -> ask(new Put(y + x)))).then(unit -> get());
  }

  /** what a program over the state is seen to do: its questions with their answers, its value, the cell after */
  static String seen(Prog<Long> p) { return run(cell(0, log(p))).toString(); }

  static String shown(List<?> all) { return String.join(" | ", all.stream().map(Object::toString).toList()); }

  public static void main(String[] args) {
    // 1-3. sequencing: `done` is a unit on each side, and grouping does not matter
    System.out.println("1 done then f: " + seen(done(5L).then(TreeLaws::put)) + " = " + seen(put(5)));
    System.out.println("2 p then done: " + seen(bump().then(TreeLaws::done)) + " = " + seen(bump()));
    Function<Long, Prog<Long>> f = x -> put(x + 1), g = y -> ask(new Put(y * 2)).then(unit -> done(y));
    System.out.println("3 grouping: " + seen(get().then(f).then(g))
        + " = " + seen(get().then(x -> f.apply(x).then(g))));

    // 4-5. a handler is a fold: what it makes of `done`, and of one program after another
    System.out.println("4 handling done: " + run(cell(3, done(7L))) + " | " + run(refusals(done(7L))));
    var whole = cell(0, bump().then(a -> bump().then(b -> done(a + b))));
    var pieces = cell(0, bump()).then(first ->
        cell(first.cell(), bump()).then(second -> done(new Held<>(first.value() + second.value(), second.cell()))));
    System.out.println("5 handling a sequence: " + run(whole) + " = " + run(pieces));

    // 6. a handler sees every operation of its effect, in order, and nothing of another's
    System.out.println("6 its own and no other's: " + run(cell(0, log(refusals(stopped())))));

    // 7. a question nobody answers: the program has no value, and `run` says which question
    String unanswered;
    try {
      unanswered = "it ran: " + run(cell(0, timed()));
    } catch (IllegalStateException e) {
      unanswered = e.getMessage().replace("nobody answers ", "");
    }
    System.out.println("7 nobody answers: " + unanswered);

    // 8. two effects whose order is the meaning: is the cell part of what a refusal throws away?
    System.out.println("8 state then failure: " + run(refusals(cell(0, lost())))
        + " | failure then state: " + run(cell(0, refusals(lost()))));

    // 9. two handlers that only answer: either order
    System.out.println("9 two that only answer: " + run(cell(0, fixedClock(7, timed())))
        + " = " + run(fixedClock(7, cell(0, timed()))));

    // 10-11. the rest resumed twice: a state handled inside the choice is each path's own, outside it is one
    System.out.println("10 twice, state inside: " + shown(run(allChoices(cell(0, forked())))));
    var outside = run(cell(0, allChoices(forked())));
    System.out.println("11 twice, state outside: " + shown(outside.value()) + ", cell " + outside.cell());

    // 12. what this handler owes its own operations: the four equations of a state
    var afterPut = run(cell(0, ask(new Put(3)).then(unit -> get()))).value();
    var lastPut = run(cell(0, ask(new Put(3)).then(unit -> ask(new Put(4))).then(unit -> get()))).value();
    var twice = run(cell(0, get().then(x -> get().then(y -> done(x + "=" + y))))).value();
    var putBack = run(cell(5, get().then(x -> ask(new Put(x))))).cell();
    System.out.println("12 the cell's own: " + afterPut + " " + lastPut + " " + twice + " cell " + putBack);
  }
}
