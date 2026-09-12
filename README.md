# Java AST printer

Prints a full AST for a Java source file.

Open with either IntelliJ or VS Code (with Java plugin) and run the `main` method in `AstPrinter`, or from the command line:

    ./gradlew ast-print

Fromt the command line, you can also pass a specific file to bypass the file chooser dialog:

    ./gradlew ast-print --args=examples/TestExpression.java

The program will prompt you to open a `.java` file, then print the AST to the console. You can parse the same file repeatedly if you want to experiment with modifications.

The project also includes a simple AST transformation demo in `AstTranformer`.
