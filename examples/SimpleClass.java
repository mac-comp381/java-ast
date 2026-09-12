package edu.macalester.plang;

public class SimpleClass {
    private String message;

    SimpleClass(String message) {
        this.message = message;
    }

    public void sayIt() {
        System.out.println("The message is: " + message);
    }

    public static void main(String[] args) {
        new SimpleClass("ahoy!").sayIt();
    }
}
