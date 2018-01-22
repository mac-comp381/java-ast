package edu.macalester.plang;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dumps javaparser’s AST in a human-friendly form.
 *
 * The main function parses and dumps Java source files given as command-line args.
 */
public class AstPrinter {

    private final int tabSize;

    public static void main(String[] args) throws Exception {
        AstPrinter astPrinter = new AstPrinter(2);

        FileDialog fileDialog = new FileDialog((Frame) null);
        fileDialog.setVisible(true);
        File[] files = fileDialog.getFiles();

        for(File file : files) {
            CompilationUnit cu = JavaParser.parse(new FileInputStream(file));
            astPrinter.dump(cu, 0);
        }

        System.exit(0);
    }

    public AstPrinter(int tabSize) {
        this.tabSize = tabSize;
    }

    /**
     * Getter methods on various nodes that clutter the output. Includes
     */
    private static final Set<String> ignoredNodeAttrs;
    static {
        Set<String> attrs = new HashSet<>();
        attrs.addAll(
            Arrays.stream(Node.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet()));
        attrs.addAll(Arrays.asList(
            "getComments", "getJavadoc", "getJavadocComment", "getSignature", "getId"
            ));
        ignoredNodeAttrs = attrs;
    }

    /**
     * Recursively dumps the AST indented by the given number of spaces.
     */
    public void dump(Node node, int indentation) {
        printIndented(indentation, describe(node));

        for(Node child : node.getChildNodes()) {
            dump(child, indentation + tabSize);
        }
    }

    private String describe(Node node) {
        StringBuilder desc = new StringBuilder(unqualifiedName(node.getClass()));

        for(Method method : node.getClass().getMethods()) {
            if(method.getName().startsWith("get")                              // getters only
                && !method.getName().endsWith("AsString")
                && method.getParameterCount() == 0
                && !ignoredNodeAttrs.contains(method.getName()))
            {
                String name = uncapitalize(method.getName().replaceFirst("get", ""));
                Object value;
                try {
                    value = method.invoke(node);
                } catch(Exception e) {
                    continue;  // ignore anything that fails
                }

                while(value instanceof Optional) {
                    value = ((Optional) value).orElse(null);
                }

                if(value == null || value instanceof Node || value instanceof NodeList)
                    continue;

                if(value instanceof Collection) {
                    boolean emptyOrAllNodes = true;
                    for(Object elem : (Collection) value)
                        if(!(elem instanceof Node))
                            emptyOrAllNodes = false;
                    if(emptyOrAllNodes)
                        continue;
                }

                if(name.equals("arrayLevel") && value.equals(0))
                    continue;

                if(value instanceof String)
                    value = "\"" + value + "\"";

                desc.append(" ")
                    .append(name)
                    .append("=")
                    .append(value);
            }
        }

        return desc.toString();
    }

    private static void printIndented(int indentation, Object obj) {
        for(int n = 0; n < indentation; n++)
            System.out.print(" ");
        System.out.println(obj);
    }

    // Strips the package name
    private static String unqualifiedName(Class aClass) {
        String[] parts = aClass.getName().split("\\.");
        return parts[parts.length - 1];
    }

    private static String uncapitalize(String s) {
        if(s == null || s.isEmpty())
            return s;
        return s.substring(0, 1).toLowerCase() + s.substring(1);
    }
}
