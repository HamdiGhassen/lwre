package org.pulse.lwre.core;

/**
     * Exception thrown when the engine is overloaded and the circuit breaker trips.
     */
    public  class EngineOverloadException extends RuleExecutionException {
        /**
         * Constructs a new {@code EngineOverloadException} with the specified message.
         *
         * @param message the error message
         */
        public EngineOverloadException(String message) {
            super(message);
        }
    }