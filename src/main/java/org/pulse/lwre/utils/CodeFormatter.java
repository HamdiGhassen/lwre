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
}
