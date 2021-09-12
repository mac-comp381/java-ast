package edu.macalester.plang;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.FileNotFoundException;

public class AstTransformer {
    public static void main(String[] args) throws FileNotFoundException {
        var config = new ParserConfiguration();
        config.setSymbolResolver(
            new JavaSymbolSolver(
                new CombinedTypeSolver(
                    new ReflectionTypeSolver())));  // use AstTransformer's classpath for input file

        CompilationUnit ast = new JavaParser(config)
            .parse(
                """
                import java.util.*;
                
                public class Foo {
                    void bar() {
                        for (int x = 0; x < 100; x++) {
                            System.out.println(x);
                        }
                        
                        StringBuilder poem = new StringBuilder();
                        for (String s : List.of("fee", "fi", "fo", "fum")) {
                            if (poem.size() > 0) {
                                poem.append(" ");
                            }
                            poem.append(s);
                        }
                        System.out.println(poem);
                        
                        for (int x = 10, y = 1; x > 0 && y < 100; x--, y *= 2)
                            for(int z = x; z < y; z++)
                                System.out.println(z);
                        
                        System.out.println("forever");
                        for(;;) System.out.println("and ever");
                    }
                }
                """
            )
            .getResult()
            .orElseThrow(() -> new RuntimeException("Parsing failed"));

        new AstPrinter(4).dump(ast, 0);

        desugarForLoops(ast);
        desugarForEachLoops(ast);

        System.out.println("––––––––––––––––––––––––––––––––––––––");
        System.out.println(ast);
        System.out.println("––––––––––––––––––––––––––––––––––––––");
    }

    /**
     * Expands all C-style (three-pronged) for loops in the given AST into equivalent while loops.
     *
     * Sample input:
     *
     *      for (int x = 0; x < 10; x++) {
     *          doStuff();
     *      }
     *
     * Sample output:
     *
     *      {
     *          int x = 0;
     *          while (x < 10) {
     *              doStuff();
     *              x++;
     *          }
     *      }
     */
    private static void desugarForLoops(Node ast) {
        for (var forLoop : ast.findAll(ForStmt.class)) {
            BlockStmt loopBody = convertToBlock(forLoop.getBody());
            appendStatementExprs(loopBody.getStatements(), forLoop.getUpdate());

            BlockStmt outerBlock = new BlockStmt();
            appendStatementExprs(
                outerBlock.getStatements(),
                forLoop.getInitialization());  // initializers go before while loop
            outerBlock.getStatements().add(
                new WhileStmt(
                    forLoop.getCompare()
                        .orElse(new BooleanLiteralExpr(true)),  // for(;;) → while(true)
                    loopBody
                )
            );

            forLoop.replace(outerBlock);
        }
    }

    /**
     * Expands all for-each loops in the given AST into equivalent while loops.
     *
     * Sample input:
     *
     *      for (Foo foo : items) {
     *          doStuff();
     *      }
     *
     * Sample output:
     *
     *      {
     *          Iterator<Foo> fooIter = items.iterator();
     *          while (fooIter.hasNext()) {
     *              Foo foo = fooIter.next();
     *              doStuff();
     *          }
     *      }
     */
    private static void desugarForEachLoops(Node ast) {
        for (var forEachLoop : ast.findAll(ForEachStmt.class)) {
            VariableDeclarator loopVar = forEachLoop.getVariableDeclarator();
            String iterVarName = loopVar.getNameAsString() + "Iter";

            BlockStmt loopBody = convertToBlock(forEachLoop.getBody());
            loopBody.getStatements().add(0,  // Insert `Foo bar = barIter.next();` at start of loop body
                new ExpressionStmt(
                    new VariableDeclarationExpr(
                        new VariableDeclarator(
                            loopVar.getType(),
                            loopVar.getName(),
                            new MethodCallExpr(
                                new NameExpr(iterVarName),
                                "next"
                            )
                        )
                    )
                )
            );

            var newBlock = new BlockStmt(
                new NodeList<>(
                    new ExpressionStmt(  // Iterator<Foo> barIter = someCollection.iterator();
                        new VariableDeclarationExpr(
                            new VariableDeclarator(
                                new ClassOrInterfaceType(null,
                                    new SimpleName("java.util.Iterator"),
                                    new NodeList<>(loopVar.getType())),
                                iterVarName,
                                new MethodCallExpr(
                                    forEachLoop.getIterable(),
                                    "iterator"
                                )
                            )
                        )
                    ),
                    new WhileStmt(  // while (barIter.hasNext())
                        new MethodCallExpr(
                            new NameExpr(iterVarName),
                            "hasNext"
                        ),
                        loopBody
                    )
                )
            );
            forEachLoop.replace(newBlock);
        }
    }

    /**
     * Converts an arbitrary statement to an equivalent BlockStmt. If the given statement is already
     * a block, this method passes it through unchanged. For any other type of statement, this
     * method wraps it in a one-statement block.
     *
     * Useful for converting a single statement to multiple statements.
     */
    private static BlockStmt convertToBlock(Statement stmt) {
        if (stmt instanceof BlockStmt) {
            return (BlockStmt) stmt;
        } else {
            return new BlockStmt(new NodeList<>(stmt));
        }
    }

    private static void appendStatementExprs(NodeList<Statement> statements, NodeList<Expression> exprs) {
        for (Expression expr : exprs) {
            statements.add(
                new ExpressionStmt(expr));
        }
    }
}
