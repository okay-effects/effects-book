// javac: method run in class Fx cannot be applied
package rows;
import static rows.Fx.*;
import static rows.Bank.*;
/** run takes only a program with nothing left to ask */
class RunWithEffects {
  String x() { return run(pay("ann", "bob", 30)); }
}
