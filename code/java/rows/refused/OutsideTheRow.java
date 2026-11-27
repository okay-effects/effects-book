// javac: incompatible types
package rows;
import static rows.Fx.*;
import rows.Bank.Accounts;
import rows.Bank.Clock;
/** a program whose row is Accounts asks the clock */
class OutsideTheRow {
  static <R extends Accounts> Prog<Long, R> p() { return ask(new Clock.Now()); }
}
