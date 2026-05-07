package org.enthusia.teleport.command;

final class CommandStrings {

    private CommandStrings() {
    }

    static boolean ignoresEqualCase(String left, String right) {
        return left != null && left.equalsIgnoreCase(right);
    }
}
