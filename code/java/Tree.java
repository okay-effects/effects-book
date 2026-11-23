// The scenario of code/expected.txt in Java, as PROGRAMS AS DATA: a sealed tree of two cases and an
// interpreter (chapter 3b). The rest of the program is a closure, so it can be called twice.
// `java code/java/Tree.java`

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class Tree {
  /** an operation: a question as data; X is the type of its answer */
  sealed interface Op<X> {}
  record Balance(String account) implements Op<Long> {
    public String toString() { return "Balance(" + account + ")"; }
  }
  record Move(String from, String to, long amount) implements Op<Void> {
    public String toString() { return "Move(" + from + "," + to + "," + amount + ")"; }
  }
  record Now() implements Op<Long> {}
  record Refuse(String why) implements Op<Void> {}

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

  /** the program: Java has no notation for `then`, so the chain is written out */
  static Prog<String> pay(String from, String to, long amount) {
    return ask(new Balance(from)).then(have ->
        (have < amount ? ask(new Refuse(from + " has " + have + ", needs " + amount)) : new Done<Void>(null))
            .then(ok -> ask(new Move(from, to, amount)))
            .then(moved -> ask(new Now()))
            .then(at -> new Done<>("moved " + amount + " from " + from + " to " + to + " at " + at)));
  }

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

  /** what a refusable program ends with */
  sealed interface Result {}
  record Paid(String receipt) implements Result {}
  record Refused(String why) implements Result {}

  static final class Accounts<A> implements Handler<A, A> {
    final Map<String, Long> balances = new LinkedHashMap<>();
    Accounts(long ann) { balances.put("ann", ann); balances.put("bob", 0L); }
    public Prog<A> done(A a) { return new Done<>(a); }
    public <X> Optional<Prog<A>> on(Op<X> op, Function<X, Prog<A>> rest) {
      return switch (op) {
        case Balance b -> Optional.of(answer(rest, balances.getOrDefault(b.account(), 0L)));
        case Move m -> {
          balances.merge(m.from(), -m.amount(), Long::sum);
          balances.merge(m.to(), m.amount(), Long::sum);
          yield Optional.of(answer(rest, null));
        }
        default -> Optional.empty();
      };
    }
    public String toString() { return "ann=" + balances.get("ann") + " bob=" + balances.get("bob"); }
  }

  record FixedClock<A>(long now) implements Handler<A, A> {
    public Prog<A> done(A a) { return new Done<>(a); }
    public <X> Optional<Prog<A>> on(Op<X> op, Function<X, Prog<A>> rest) {
      return op instanceof Now ? Optional.of(answer(rest, now)) : Optional.empty();
    }
  }

  /** zero resumptions: the rest is not called */
  record Refusals() implements Handler<String, Result> {
    public Prog<Result> done(String a) { return new Done<>(new Paid(a)); }
    public <X> Optional<Prog<Result>> on(Op<X> op, Function<X, Prog<Result>> rest) {
      return op instanceof Refuse r ? Optional.of(new Done<>(new Refused(r.why()))) : Optional.empty();
    }
  }

  /** an interceptor: the question is asked again for whoever is outside, and the answer written down */
  static final class Journal<A> implements Handler<A, A> {
    final List<String> lines = new ArrayList<>();
    public Prog<A> done(A a) { return new Done<>(a); }
    public <X> Optional<Prog<A>> on(Op<X> op, Function<X, Prog<A>> rest) {
      if (!(op instanceof Balance || op instanceof Move)) return Optional.empty();
      return Optional.of(new Ask<>(op, x -> {
        lines.add(op + " -> " + (x == null ? "()" : x));
        return rest.apply(x);
      }));
    }
  }

  record Limit<A>(long max) implements Handler<A, A> {
    public Prog<A> done(A a) { return new Done<>(a); }
    public <X> Optional<Prog<A>> on(Op<X> op, Function<X, Prog<A>> rest) {
      if (op instanceof Move m && m.amount() > max)
        return Optional.of(new Ask<>(new Refuse("a move of " + m.amount() + " is over the limit of " + max),
            never -> { throw new IllegalStateException("a refusal is not answered"); }));
      return Optional.empty();
    }
  }

  /** many resumptions: at Balance the rest is called once per supposed answer */
  record WhatIf(List<Long> suppose) implements Handler<Result, List<String>> {
    public Prog<List<String>> done(Result r) { return new Done<>(List.of(show(r).replace("paid: ", ""))); }
    public <X> Optional<Prog<List<String>>> on(Op<X> op, Function<X, Prog<List<String>>> rest) {
      if (!(op instanceof Balance)) return Optional.empty();
      Prog<List<String>> all = new Done<>(List.of());
      for (long s : suppose)
        all = all.then(seen -> answer(rest, s).then(outcomes -> {
          var more = new ArrayList<>(seen);
          for (var o : outcomes) more.add(s + " -> " + o);
          return new Done<List<String>>(more);
        }));
      return Optional.of(all);
    }
  }

  /** a bank that moves nothing */
  record DryRun<A>() implements Handler<A, A> {
    public Prog<A> done(A a) { return new Done<>(a); }
    public <X> Optional<Prog<A>> on(Op<X> op, Function<X, Prog<A>> rest) {
      return op instanceof Move ? Optional.of(answer(rest, null)) : Optional.empty();
    }
  }

  static String show(Result r) {
    return switch (r) {
      case Paid p -> "paid: " + p.receipt();
      case Refused f -> "refused: " + f.why();
    };
  }
  static Result edge(Accounts<Result> bank, Prog<Result> p) {
    return run(handle(bank, handle(new FixedClock<>(42), p)));
  }

  public static void main(String[] args) {
    var rich = new Accounts<Result>(100);
    System.out.println("1 " + show(edge(rich, handle(new Refusals(), pay("ann", "bob", 30)))));
    System.out.println("1 balances: " + rich);

    var poor = new Accounts<Result>(10);
    System.out.println("2 " + show(edge(poor, handle(new Refusals(), pay("ann", "bob", 30)))));
    System.out.println("2 balances: " + poor);

    var journal = new Journal<String>();
    edge(new Accounts<>(100), handle(new Refusals(), handle(journal, pay("ann", "bob", 30))));
    journal.lines.forEach(l -> System.out.println("3 journal: " + l));

    var limited = new Accounts<Result>(100);
    var over = edge(limited, handle(new Refusals(), handle(new Limit<>(50), pay("ann", "bob", 80))));
    System.out.println("4 " + show(over));
    System.out.println("4 balances: " + limited);

    var seen = new Journal<String>();
    edge(new Accounts<>(10), handle(new Refusals(), handle(seen, pay("ann", "bob", 30))));
    System.out.println("5 left early: the journal has " + seen.lines.size() + " line, "
        + String.join("; ", seen.lines));

    var outcomes = run(handle(new WhatIf(List.of(100L, 10L)), handle(new DryRun<>(),
        handle(new FixedClock<>(42), handle(new Refusals(), pay("ann", "bob", 30))))));
    System.out.println("6 twice: " + String.join(" | ", outcomes));
  }
}
