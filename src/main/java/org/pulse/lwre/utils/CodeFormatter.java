package org.pulse.lwre.utils;
/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * The {@code CodeFormatter} class provides utilities for formatting Java code to improve readability,
 * particularly for error reporting in the Lightweight Rule Engine (LWRE). It converts single-line Java
 * code into a properly indented multi-line representation, handling strings, comments, and code structure
 * elements like semicolons and braces.
 *
 * @author Hamdi Ghassen
 */
public class CodeFormatter {
    /**
     * Formats a single-line Java code string into a multi-line representation for better error readability.
     *
     * @param code the Java code in a single line
     * @return formatted multi-line code
     */
    public static String formatJavaCode(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }

        StringBuilder formatted = new StringBuilder();
        int indentLevel = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        char stringDelimiter = '"';

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            char next = (i < code.length() - 1) ? code.charAt(i + 1) : 0;

            // Handle escapes in strings/chars
            if ((inString || inChar) && c == '\\') {
                formatted.append(c);
                if (i < code.length() - 1) {
                    formatted.append(next);
                    i++;
                }
                continue;
            }

            // Toggle string state
            if (c == '"' && !inChar && !inLineComment && !inBlockComment) {
                inString = !inString;
                stringDelimiter = '"';
            }

            // Toggle char state
            if (c == '\'' && !inString && !inLineComment && !inBlockComment) {
                inChar = !inChar;
            }

            // Toggle line comments
            if (!inString && !inChar && !inBlockComment && c == '/' && next == '/') {
                inLineComment = true;
            }

            // Toggle block comments
            if (!inString && !inChar && !inLineComment && c == '/' && next == '*') {
                inBlockComment = true;
            }

            // End line comments at newline
            if (inLineComment && (c == '\n' || i == code.length() - 1)) {
                inLineComment = false;
            }

            // End block comments
            if (inBlockComment && c == '*' && next == '/') {
                inBlockComment = false;
                formatted.append('*').append('/');
                i++;
                continue;
            }

            // Skip formatting inside comments/strings
            if (inString || inChar || inLineComment || inBlockComment) {
                formatted.append(c);
                continue;
            }

            // Handle special formatting cases
            switch (c) {
                case ';':
                    formatted.append(";\n");
                    appendIndent(formatted, indentLevel);
                    break;

                case '{':
                    formatted.append(" {\n");
                    indentLevel++;
                    appendIndent(formatted, indentLevel);
                    break;

                case '}':
                    indentLevel = Math.max(0, indentLevel - 1);
                    formatted.append("\n");
                    appendIndent(formatted, indentLevel);
                    formatted.append('}');
                    if (next != ';' && next != ',') {
                        formatted.append("\n");
                        appendIndent(formatted, indentLevel);
                    }
                    break;

                case '\n':
                    formatted.append("\n");
                    appendIndent(formatted, indentLevel);
                    break;

                default:
                    formatted.append(c);
            }
        }

        return formatted.toString();
    }
    /**
     * Appends the appropriate indentation to a StringBuilder based on the current indent level.
     *
     * @param sb the StringBuilder to append indentation to
     * @param indentLevel the number of indentation levels (each level is 4 spaces)
     */
    private static void appendIndent(StringBuilder sb, int indentLevel) {
        for (int i = 0; i < indentLevel * 4; i++) {
            sb.append(' ');
        }
    }
    /**
     * Adjusts all methods in helper code to be 'public static'
     * Handles multiple methods
     * @param helperCode the Java code of the helper block to process
     * @return the processed helper block
     */
    public static String adjustHelperMethodAccess(String helperCode) {
        String src = helperCode.replaceAll(
                "(?m)^(\\s*)(public|private|protected|static\\b\\s*)+",
                "$1"
        );

        return addPublicStaticToMethods(src);
    }
    /**
     * Modifies the input Java source code to add 'public static' to method declarations
     * that do not have certain modifiers (public, private, protected, static, etc.).
     * Processes the input line by line with a simplified regex to avoid stack overflow.
     *
     * @param sourceCode the input Java source code as a string
     * @return the modified source code with 'public static' added to eligible methods
     */
    public static String addPublicStaticToMethods(String sourceCode) {
        StringBuilder result = new StringBuilder();
        String[] lines = sourceCode.split("\n");
        boolean inMultiLineComment = false;

        // Simplified regex to match basic method declarations
        // Matches: [optional whitespace][return type][method name]([parameters]){
        String methodRegex = "^(\\s*)(\\w+\\s+\\w+\\s*\\([^)]*\\)\\s*\\{)";

        for (String line : lines) {
            String trimmedLine = line.trim();

            // Skip long lines to prevent regex engine strain (arbitrary limit, e.g., 1000 chars)
            if (line.length() > 1000) {
                result.append(line).append("\n");
                continue;
            }

            // Handle multi-line comments
            if (trimmedLine.startsWith("/*")) {
                inMultiLineComment = true;
                result.append(line).append("\n");
                continue;
            }
            if (inMultiLineComment) {
                if (trimmedLine.endsWith("*/")) {
                    inMultiLineComment = false;
                }
                result.append(line).append("\n");
                continue;
            }

            // Skip single-line comments and empty lines
            if (trimmedLine.startsWith("//") || trimmedLine.isEmpty()) {
                result.append(line).append("\n");
                continue;
            }

            // Check if the line is a method declaration
            if (line.matches(methodRegex)) {
                // Extract indentation and method signature
                String indent = line.replaceAll("^(\\s*).*$", "$1");
                String methodSignature = line.replaceAll(methodRegex, "$2");

                // Check for excluded modifiers and keywords
                boolean hasExcludedModifier = trimmedLine.contains("public ")
                        || trimmedLine.contains("private ")
                        || trimmedLine.contains("protected ")
                        || trimmedLine.contains("static ")
                        || trimmedLine.contains("class ")
                        || trimmedLine.contains("interface ")
                        || trimmedLine.contains("enum ")
                        || trimmedLine.contains("if ")
                        || trimmedLine.contains("for ")
                        || trimmedLine.contains("while ")
                        || trimmedLine.contains("return ")
                        || trimmedLine.contains("switch ");

                // Check for annotations (e.g., @Override) to avoid false positives
                boolean hasAnnotation = trimmedLine.startsWith("@");

                if (!hasExcludedModifier && !hasAnnotation) {
                    // Add 'public static' with proper indentation
                    result.append(indent).append("public static ").append(methodSignature.trim()).append("\n");
                } else {
                    result.append(line).append("\n");
                }
            } else {
                result.append(line).append("\n");
            }
        }

        return result.toString();
    }
}
