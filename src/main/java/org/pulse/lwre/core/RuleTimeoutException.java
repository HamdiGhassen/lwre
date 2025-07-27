package org.pulse.lwre.core;

/**
     * Exception thrown when a rule execution exceeds its configured timeout.
     */
    public  class RuleTimeoutException extends RuleExecutionException {
        /**
         * Constructs a new {@code RuleTimeoutException} with the specified message.
         *
         * @param message the error message
         */
        public RuleTimeoutException(String message) {
            super(message);
        }
    }