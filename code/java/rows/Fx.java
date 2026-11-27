// THE TREE IN JAVA, WITH THE ROW IN THE TYPE (chapter 3j). The forty lines of chapter 3 again, for a language
// without GADTs or higher kinds: the answer of every operation is typed, the effects a program may ask are a
// set in its type, a handler's clauses may ask only what is around it, and javac refuses a program run with an
// effect unhandled and a handler that leaves an operation out. Made to be read, not to be fast: recursive, and
// a node is made anew for every handler it passes.
package rows;

import java.util.function.BiFunction;
import java.util.function.Function;

public final class Fx {
  private Fx() {}

  /** an operation: a record of the effect F whose answer is an X */
  public interface Op<X, F> {}

  /** a program that gives an A and may ask the effects R; R is in the type only, no value holds it */
  public sealed interface Prog<A, R> {
    /** a program of fewer effects is one of more as it is: Q has every effect R has */
    <B, Q extends R> Prog<B, Q> then(Function<A, Prog<B, Q>> next);
  }
  private record Done<A, R>(A value) implements Prog<A, R> {
    public <B, Q extends R> Prog<B, Q> then(Function<A, Prog<B, Q>> next) { return next.apply(value); }
  }
  private record Ask<X, A, R>(Op<X, ?> op, Function<X, Prog<A, R>> rest) implements Prog<A, R> {
    public <B, Q extends R> Prog<B, Q> then(Function<A, Prog<B, Q>> next) {
      return new Ask<>(op, x -> rest.apply(x).then(next));
    }
  }
  public static <A, R> Prog<A, R> done(A value) { return new Done<>(value); }
  /** asking: only in a row that has the operation's effect */
  public static <X, F, R extends F> Prog<X, R> ask(Op<X, F> op) { return new Ask<>(op, Fx::done); }

  /** what a handler does with one operation of F: given its state and the rest of the part, k, a function of
   * the answer and the next state. The answer's type is the operation's */
  public record Clause<X, F, S, B, R>(Op<X, F> op, BiFunction<S, BiFunction<X, S, Prog<B, R>>, Prog<B, R>> body) {}
  public static <X, F, S, B, R> Clause<X, F, S, B, R> on(
      Op<X, F> op, BiFunction<S, BiFunction<X, S, Prog<B, R>>, Prog<B, R>> body) {
    return new Clause<>(op, body);
  }
  public static <X, F, S, B, R> Clause<X, F, S, B, R> answer(Op<X, F> op, X x) {
    return on(op, (s, k) -> k.apply(x, s));
  }

  /** a handler of F is a fold of the part it is around: a state, what it gives where the part ends with an A,
   * and a clause for each operation, by a switch that javac checks is whole. Its clauses may ask R */
  public record Handler<F, S, A, B, R>(Class<F> effect, S state, BiFunction<S, A, Prog<B, R>> done,
                                       Function<F, Clause<?, F, S, B, R>> clauses) {}
  /** one with no state, that gives what its part gives */
  public static <F, B, R> Handler<F, Void, B, B, R> handling(
      Class<F> effect, Function<F, Clause<?, F, Void, B, R>> clauses) {
    return new Handler<>(effect, null, (none, b) -> done(b), clauses);
  }

  /** Fx.<R, B>row().handler(outer)...handler(inner).handle(part): the handlers outermost first. Each adds its
   * effect to W, the set inside it, and may itself ask only what is around it; javac refuses a part that asks
   * more than the last W. B is what the whole gives, A what the part must give */
  public static <R, B> Handlers<R, R, B, B> row() { return new Handlers<>(p -> p); }
  public static <B> Handlers<Object, Object, B, B> top() { return row(); }
  public static final class Handlers<R, W, A, B> {
    private final Function<Prog<A, ?>, Prog<B, ?>> around;
    private Handlers(Function<Prog<A, ?>, Prog<B, ?>> around) { this.around = around; }

    public <V extends W, P> Handlers<R, V, P, B> handler(Handler<? super V, ?, P, A, W> h) {
      return new Handlers<>(p -> around.apply(under(h, p)));
    }
    public Prog<B, R> handle(Prog<A, ? super W> part) { return rowed(around.apply(part)); }
  }

  /** a part under a handler: its operations answered by the clauses, every other asked further out */
  private static <F, S, A, B, R> Prog<B, R> under(Handler<F, S, A, B, R> h, Prog<A, ?> p) {
    return under(h, h.state(), p);
  }
  private static <F, S, A, B, R> Prog<B, R> under(Handler<F, S, A, B, R> h, S s, Prog<A, ?> p) {
    return switch (p) {
      case Done<A, ?> d -> h.done().apply(s, d.value());
      case Ask<?, A, ?> a -> step(h, s, a);
    };
  }
  private static <X, F, S, A, B, R> Prog<B, R> step(Handler<F, S, A, B, R> h, S s, Ask<X, A, ?> a) {
    if (!h.effect().isInstance(a.op())) return new Ask<>(a.op(), x -> under(h, s, a.rest().apply(x)));
    BiFunction<X, S, Prog<B, R>> k = (x, next) -> under(h, next, a.rest().apply(x));
    return given(h.clauses().apply(h.effect().cast(a.op())), s, a.op(), k);
  }
  /** The one cast. The clause was made for the operation asked, so its Y is the rest's X: a switch does not
   * tell Java so (chapter 3b), and the test of identity stands for it. */
  @SuppressWarnings("unchecked")
  private static <Y, X, F, S, B, R> Prog<B, R> given(Clause<Y, F, S, B, R> c, S s, Op<X, ?> asked,
                                                     BiFunction<X, S, Prog<B, R>> k) {
    if (c.op() != asked) throw new IllegalStateException("a clause for " + c.op() + " was given " + asked);
    return c.body().apply(s, (BiFunction<Y, S, Prog<B, R>>) (BiFunction<?, S, Prog<B, R>>) k);
  }
  /** the same program in another row: no value holds a row, so it is made anew, not cast */
  private static <A, R> Prog<A, R> rowed(Prog<A, ?> p) {
    return switch (p) {
      case Done<A, ?> d -> new Done<>(d.value());
      case Ask<?, A, ?> a -> rowedAsk(a);
    };
  }
  private static <X, A, R> Prog<A, R> rowedAsk(Ask<X, A, ?> a) {
    return new Ask<>(a.op(), x -> rowed(a.rest().apply(x)));
  }

  /** running: only a program with nothing left to ask */
  public static <A> A run(Prog<A, Object> p) {
    return switch (p) {
      case Done<A, Object> d -> d.value();
      case Ask<?, A, Object> a -> throw new IllegalStateException("nobody answers " + a.op());
    };
  }
}
