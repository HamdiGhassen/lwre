package org.pulse.lwre.core;

public  class RuleCompilationException extends RuntimeException {
        public RuleCompilationException(String message, Throwable cause) {
            super(message, cause);
        }

        public RuleCompilationException(String message) {
            super(message);
        }
    }