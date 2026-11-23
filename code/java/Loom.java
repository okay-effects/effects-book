// The scenario of code/expected.txt in Java, in DIRECT STYLE ON VIRTUAL THREADS (chapter 3d): the program is
// plain code, asking is a blocking call, and the rest of the program is its own parked thread. One resumption.
// `java code/java/Loom.java`

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.SynchronousQueue;
import java.util.function.Supplier;

public class Loom {
  sealed interface Op<X> {}
  record Balance(String account) implements Op<Long> {
    public String toString() { return "Balance(" + account + ")"; }
  }
  record Move(String from, String to, long amount) implements Op<Void> {
    public String toString() { return "Move(" + from + "," + to + "," + amount + ")"; }
  }
  record Now() implements Op<Long> {}
  record Refuse(String why) implements Op<Void> {}

  /** the program: ordinary Java, top to bottom */
  static String pay(String from, String to, long amount) {
    long have = ask(new Balance(from));
    if (have < amount) ask(new Refuse(from + " has " + have + ", needs " + amount));
    ask(new Move(from, to, amount));
    long at = ask(new Now());
    return "moved " + amount + " from " + from + " to " + to + " at " + at;
  }

  // ---- the mechanism: two hand-over points between the body's thread and the thread that handles

  record Asked(Op<?> op) {}
  record Finished(String value) {}
  record Answer(Object value) {}
  /** thrown at the question a body is parked in when its rest is dropped: its `finally` blocks run */
  static final class Abandoned extends Error {
    Abandoned() { super(null, null, false, false); }
  }
  record Channel(SynchronousQueue<Object> toHandler, SynchronousQueue<Object> toBody) {}
  static final ScopedValue<Channel> CHANNEL = ScopedValue.newInstance();

  @SuppressWarnings("unchecked")
  static <X> X ask(Op<X> op) {
    var channel = CHANNEL.get();
    try {
      channel.toHandler().put(new Asked(op));
      if (channel.toBody().take() instanceof Answer a) return (X) a.value();
      throw new Abandoned();
    } catch (InterruptedException e) {
      throw new Abandoned();
    }
  }

  /** a handler answers by RETURNING; it may ask whoever is outside; it leaves by throwing Leave */
  static final Object PASS = new Object();
  interface Outer { Object ask(Op<?> op); }
  interface Handler { Object on(Op<?> op, Outer outer); }
  static final class Leave extends RuntimeException {
    final Result result;
    Leave(Result result) { super(null, null, false, false); this.result = result; }
  }

  sealed interface Result {}
  record Paid(String receipt) implements Result {}
  record Refused(String why) implements Result {}

  /** handlers are listed innermost first */
  static Object answer(List<Handler> handlers, int from, Op<?> op) {
    for (int i = from; i < handlers.size(); i++) {
      int next = i + 1;
      Object x = handlers.get(i).on(op, o -> answer(handlers, next, o));
      if (x != PASS) return x;
    }
    throw new IllegalStateException("nobody answers " + op);
  }

  static Result run(List<Handler> handlers, Supplier<String> body) {
    var channel = new Channel(new SynchronousQueue<>(), new SynchronousQueue<>());
    var thread = Thread.ofVirtual().start(() -> ScopedValue.where(CHANNEL, channel).run(() -> {
      try {
        String value = body.get();
        channel.toHandler().put(new Finished(value));
      } catch (Abandoned | InterruptedException left) {
        // the rest was dropped: the stack has been unwound, the thread ends
      }
    }));
    try {
      while (true) {
        switch (channel.toHandler().take()) {
          case Finished f -> { return new Paid(f.value()); }
          case Asked a -> {
            Object x;
            try {
              x = answer(handlers, 0, a.op());
            } catch (Leave leave) {
              channel.toBody().put(PASS);        // not an Answer: the body unwinds
              thread.join();
              return leave.result;
            }
            channel.toBody().put(new Answer(x));  // the one resumption
          }
          default -> throw new IllegalStateException();
        }
      }
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  static final class Accounts implements Handler {
    final Map<String, Long> balances = new LinkedHashMap<>();
    Accounts(long ann) { balances.put("ann", ann); balances.put("bob", 0L); }
    public Object on(Op<?> op, Outer outer) {
      return switch (op) {
        case Balance b -> balances.getOrDefault(b.account(), 0L);
        case Move m -> {
          balances.merge(m.from(), -m.amount(), Long::sum);
          balances.merge(m.to(), m.amount(), Long::sum);
          yield null;
        }
        default -> PASS;
      };
    }
    public String toString() { return "ann=" + balances.get("ann") + " bob=" + balances.get("bob"); }
  }
  static final Handler CLOCK = (op, outer) -> op instanceof Now ? (Object) 42L : PASS;
  static final Handler REFUSALS = (op, outer) -> {
    if (op instanceof Refuse r) throw new Leave(new Refused(r.why()));
    return PASS;
  };
  static final class Journal implements Handler {
    final List<String> lines = new ArrayList<>();
    public Object on(Op<?> op, Outer outer) {
      if (!(op instanceof Balance || op instanceof Move)) return PASS;
      Object x = outer.ask(op);
      lines.add(op + " -> " + (x == null ? "()" : x));
      return x;
    }
  }
  static Handler limit(long max) {
    return (op, outer) -> op instanceof Move m && m.amount() > max
        ? outer.ask(new Refuse("a move of " + m.amount() + " is over the limit of " + max))
        : PASS;
  }

  static String show(Result r) {
    return switch (r) {
      case Paid p -> "paid: " + p.receipt();
      case Refused f -> "refused: " + f.why();
    };
  }

  public static void main(String[] args) {
    var rich = new Accounts(100);
    System.out.println("1 " + show(run(List.of(REFUSALS, CLOCK, rich), () -> pay("ann", "bob", 30))));
    System.out.println("1 balances: " + rich);

    var poor = new Accounts(10);
    System.out.println("2 " + show(run(List.of(REFUSALS, CLOCK, poor), () -> pay("ann", "bob", 30))));
    System.out.println("2 balances: " + poor);

    var journal = new Journal();
    run(List.of(journal, REFUSALS, CLOCK, new Accounts(100)), () -> pay("ann", "bob", 30));
    journal.lines.forEach(l -> System.out.println("3 journal: " + l));

    var limited = new Accounts(100);
    System.out.println("4 " + show(run(List.of(limit(50), REFUSALS, CLOCK, limited), () -> pay("ann", "bob", 80))));
    System.out.println("4 balances: " + limited);

    var seen = new Journal();
    run(List.of(seen, REFUSALS, CLOCK, new Accounts(10)), () -> pay("ann", "bob", 30));
    System.out.println("5 left early: the journal has " + seen.lines.size() + " line, "
        + String.join("; ", seen.lines));

    System.out.println("6 twice: cannot: the rest is a parked thread; a thread's stack cannot be copied, "
        + "so it is resumed once");
  }
}
