// The scenario of code/expected.txt on Fx.java, the tree with the row in the type (chapter 3j). pay's type says
// that it asks Accounts, Clock and Refusal, and javac checks that every run below has a handler for each.
// `java code/java/rows/Bank.java`
package rows;

import static rows.Fx.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Bank {
  /** an effect is a sealed interface; an operation a record that says its answer and its effect */
  sealed interface Accounts {
    record Balance(String account) implements Accounts, Op<Long, Accounts> {
      public String toString() { return "Balance(" + account + ")"; }
    }
    record Move(String from, String to, long amount) implements Accounts, Op<Void, Accounts> {
      public String toString() { return "Move(" + from + "," + to + "," + amount + ")"; }
    }
  }
  sealed interface Clock {
    record Now() implements Clock, Op<Long, Clock> {}
  }
  sealed interface Refusal {
    record Refuse(String why) implements Refusal, Op<Void, Refusal> {}
  }

  /** the program: its row is what it may ask */
  static <R extends Accounts & Clock & Refusal> Prog<String, R> pay(String from, String to, long amount) {
    return ask(new Accounts.Balance(from)).then(have ->
        (have < amount ? Fx.<Void, Refusal, R>ask(new Refusal.Refuse(from + " has " + have + ", needs " + amount))
            : Fx.<Void, R>done(null))
            .then(ok -> ask(new Accounts.Move(from, to, amount)))
            .then(moved -> ask(new Clock.Now()))
            .then(at -> done("moved " + amount + " from " + from + " to " + to + " at " + at)));
  }

  sealed interface Result {}
  record Paid(String receipt) implements Result {}
  record Refused(String why) implements Result {}

  /** a bank in memory: a case for every operation of Accounts, or it does not compile */
  static <A, R> Handler<Accounts, Void, A, A, R> inMemory(Map<String, Long> balances) {
    return handling(Accounts.class, op -> switch (op) {
      case Accounts.Balance b -> answer(b, balances.getOrDefault(b.account(), 0L));
      case Accounts.Move m -> {
        balances.merge(m.from(), -m.amount(), Long::sum);
        balances.merge(m.to(), m.amount(), Long::sum);
        yield answer(m, null);
      }
    });
  }
  static <A, R> Handler<Clock, Void, A, A, R> fixedClock(long now) {
    return handling(Clock.class, op -> switch (op) {
      case Clock.Now n -> answer(n, now);
    });
  }
  /** zero resumptions: the rest is left, and the handler gives a value of its own */
  static <R> Handler<Refusal, Void, String, Result, R> refusals() {
    return new Handler<>(Refusal.class, null, (none, receipt) -> done(new Paid(receipt)), op -> switch (op) {
      case Refusal.Refuse r -> on(r, (none, k) -> done(new Refused(r.why())));
    });
  }
  /** an interceptor: each operation asked again of the bank around it, and written down with its answer. It
   * asks Accounts, so it can only be put inside a handler of Accounts */
  static <A, R extends Accounts> Handler<Accounts, Void, A, A, R> journal(List<String> lines) {
    return handling(Accounts.class, op -> switch (op) {
      case Accounts.Balance b -> on(b, (none, k) -> ask(b).then(x -> {
        lines.add(b + " -> " + x);
        return k.apply(x, none);
      }));
      case Accounts.Move m -> on(m, (none, k) -> ask(m).then(x -> {
        lines.add(m + " -> ()");
        return k.apply(x, none);
      }));
    });
  }
  /** a refusal before the bank sees a large move: it asks Accounts and Refusal of what is around it */
  static <A, R extends Accounts & Refusal> Handler<Accounts, Void, A, A, R> limit(long max) {
    return handling(Accounts.class, op -> switch (op) {
      case Accounts.Balance b -> on(b, (none, k) -> ask(b).then(x -> k.apply(x, none)));
      case Accounts.Move m -> on(m, (none, k) -> m.amount() <= max
          ? ask(m).then(x -> k.apply(x, none))
          : ask(new Refusal.Refuse("a move of " + m.amount() + " is over the limit of " + max))
              .then(never -> { throw new IllegalStateException("a refusal is not answered"); }));
    });
  }
  /** many resumptions: at Balance the rest is gone on with once for each supposed answer; nothing moves */
  static <R> Handler<Accounts, Void, Result, List<String>, R> whatIf(List<Long> suppose) {
    return new Handler<>(Accounts.class, null, (none, r) -> done(List.of(show(r).replace("paid: ", ""))),
        op -> switch (op) {
          case Accounts.Balance b -> on(b, (none, k) -> {
            Prog<List<String>, R> all = done(List.of());
            for (long s : suppose)
              all = all.then(seen -> k.apply(s, none).then(outcomes -> {
                var more = new ArrayList<>(seen);
                for (var o : outcomes) more.add(s + " -> " + o);
                return done(more);
              }));
            return all;
          });
          case Accounts.Move m -> answer(m, null);
        });
  }

  static String show(Result r) {
    return switch (r) {
      case Paid p -> "paid: " + p.receipt();
      case Refused f -> "refused: " + f.why();
    };
  }
  static Map<String, Long> balances(long ann) { return new LinkedHashMap<>(Map.of("ann", ann, "bob", 0L)); }
  static String shown(Map<String, Long> b) { return "ann=" + b.get("ann") + " bob=" + b.get("bob"); }

  public static void main(String[] args) {
    // the handlers outermost first: the bank, the clock, the refusals, then the part
    var rich = balances(100);
    var paid = run(Fx.<Result>top().handler(inMemory(rich)).handler(fixedClock(42)).handler(refusals())
        .handle(pay("ann", "bob", 30)));
    System.out.println("1 " + show(paid));
    System.out.println("1 balances: " + shown(rich));

    var poor = balances(10);
    var refused = run(Fx.<Result>top().handler(inMemory(poor)).handler(fixedClock(42)).handler(refusals())
        .handle(pay("ann", "bob", 30)));
    System.out.println("2 " + show(refused));
    System.out.println("2 balances: " + shown(poor));

    var lines = new ArrayList<String>();
    run(Fx.<Result>top().handler(inMemory(balances(100))).handler(fixedClock(42)).handler(refusals())
        .handler(journal(lines)).handle(pay("ann", "bob", 30)));
    lines.forEach(l -> System.out.println("3 journal: " + l));

    var held = balances(100);
    var over = run(Fx.<Result>top().handler(inMemory(held)).handler(fixedClock(42)).handler(refusals())
        .handler(limit(50)).handle(pay("ann", "bob", 80)));
    System.out.println("4 " + show(over));
    System.out.println("4 balances: " + shown(held));

    var seen = new ArrayList<String>();
    run(Fx.<Result>top().handler(inMemory(balances(10))).handler(fixedClock(42)).handler(refusals())
        .handler(journal(seen)).handle(pay("ann", "bob", 30)));
    System.out.println("5 left early: the journal has " + seen.size() + " line, " + String.join("; ", seen));

    var outcomes = run(Fx.<List<String>>top().handler(whatIf(List.of(100L, 10L))).handler(fixedClock(42))
        .handler(refusals()).handle(pay("ann", "bob", 30)));
    System.out.println("6 twice: " + String.join(" | ", outcomes));
  }
}
