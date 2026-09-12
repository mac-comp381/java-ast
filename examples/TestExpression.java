public class TestExpression {
    static double foo(int x, double y, String s) {
        return x + 65 * (y + s.indexOf("z")) * (x - 1);
    }
}
