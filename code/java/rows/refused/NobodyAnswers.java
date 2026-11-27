// javac: inference variable R has incompatible bounds
package rows;
import static rows.Fx.*;
/** law 7: the clock is asked and nobody answers it, so the program cannot be run: it does not compile */
class NobodyAnswers {
  Laws.Held<Long> x() { return run(Fx.<Laws.Held<Long>>top().handler(Laws.cell(0)).handle(Laws.timed())); }
}
