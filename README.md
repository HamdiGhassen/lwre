# Lightweight Rule Engine (LWRE)
[![Java CI with Maven and Release](https://github.com/HamdiGhassen/lwre/actions/workflows/maven.yml/badge.svg?branch=main)](https://github.com/HamdiGhassen/lwre/actions/workflows/maven.yml)[![CodeQL](https://github.com/HamdiGhassen/lwre/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/HamdiGhassen/lwre/actions/workflows/github-code-scanning/codeql)

## Overview
The **Lightweight Rule Engine (LWRE)** is a  Java-based rule engine designed for dynamic rule execution in complex systems. It enables developers to define, compile, and execute business rules using a Domain-Specific Language (DSL), with support for dependency management, retries, timeouts, and parallel execution. The engine is optimized for performance with features like precomputed execution paths, thread-safe operations, and a circuit breaker pattern to prevent system overload.

## Features
- **DSL-Based Rule Definition**: Define rules using a concise and expressive DSL, supporting imports, variables, retries, and control flow directives.
- **Dynamic Compilation**: Rules are compiled into Java classes at runtime using the Janino compiler, with caching for performance.
- **Dependency Management**: Organizes rules into directed graphs to handle dependencies and execution order, with cycle detection.
- **Parallel Execution**: Supports asynchronous rule execution across groups using a thread pool, with timeout enforcement.
- **Retry Mechanism**: Configurable retry policies with delays and custom retry conditions for fault tolerance.
- **Circuit Breaker**: Prevents system overload by limiting executions when failure thresholds are reached.
- **Thread Safety**: Utilizes thread-safe data structures (`ConcurrentHashMap`, synchronized methods) for reliable concurrent operation.
- **Metrics and Tracing**: Provides execution metrics and optional tracing for debugging and performance monitoring.
- **Rule Versioning**: Supports rule versioning and rollback for safe updates and experimentation.

## Installation
### Prerequisites
- **Java 17** or higher
- **Maven** for dependency management

### Dependencies
The following dependencies are required. Add them to your `pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>org.codehaus.janino</groupId>
        <artifactId>janino</artifactId>
        <version>3.1.12</version>
    </dependency>
</dependencies>
```

### Building from Source
1. Clone the repository:
   ```bash
   git clone https://github.com/HamdiGhassen/lwre.git
   cd lwre
   ```
2. Build the project using Maven:
   ```bash
   mvn clean install
   ```

## Usage
### Defining Rules
Rules are defined using a DSL with directives for rule configuration, logic, and dependencies. Below is an example DSL snippet:

```plaintext
#GLOBAL
   userId : String
   threshold : Integer
                                
#HELPER
   int calculateScore(int value) {
       return value * 2;
   }
                                
#RULE ValidateUser
#GROUP UserValidation
#PRIORITY 1

#USE
   userId : String as user FROM Global
   threshold : Integer as threshold FROM Global

#PRODUCE
   score : Integer

#CONDITION
   return user != null && threshold > 100;

#ACTION
   score = calculateScore(threshold);
   System.out.println("the score is : "+score);

#FINAL
   return "Validation complete for user: " + user;
```

### Integrating with Java
Create and execute rules using the `LWREngine`:

```java
import org.pulse.lwre.core.LWREngine;

public class Main {
    public static void main(String[] args) throws Exception {
        // Initialize the engine with rules
        LWREngine engine = new LWREngine.Builder()
                .rules(dslContent) // Load DSL from file or string
                .global("userId", "user123")
                .global("threshold", 150)
                .debug(true)
                .build();

        // Execute rules for a specific group
        Object result = engine.executeRules("UserValidation");
        System.out.println("Execution result: " + result);

        // Or execute asynchronously
        engine.executeRulesAsync("UserValidation")
                .thenAccept(res -> System.out.println("Async result: " + res));
    }
}
```

### DSL Syntax
The DSL supports the following directives:
- `#GLOBAL <name> : <type>`: Define global variables.
- `#HELPER`: Define reusable helper methods.
- `#RULE <name>`: Define a rule with a unique name.
- `#GROUP <name>`: Assign the rule to a group.
- `#PRIORITY <number>`: Set execution priority.
- `#USE <variable> : <type> as <alias> FROM <Global|RULE <name>>`: Declare variables used by the rule.
- `#PRODUCE <variable> : <type>`: Declare variables produced by the rule.
- `#CONDITION`: Java code block evaluating to a boolean.
- `#ACTION`: Java code block for rule actions.
- `#FINAL`: Java code block for final processing.
- `#RETRY <count> [DELAY <ms>] [IF {condition}]`: Configure retry policy.
- `#NEXT_ON_SUCCESS <ruleName>`: Specify next rule on success.
- `#NEXT_ON_FAILURE <ruleName>`: Specify next rule on failure.
- `#TIMEOUT <value> <ms|s>`: Set execution timeout.
- `#VERSION <major.minor.patch>`: Set rule version.
- `#MAX_EXECUTIONS <number>`: Limit rule executions.

### Key Classes
- **LWREngine**: Core engine for rule execution, with synchronous and asynchronous methods.
- **RuleCompiler**: Compiles DSL rules into Java classes, ensuring script safety and caching.
- **RuleGraphProcessor**: Builds dependency graphs for rules, optimizing execution order.
- **CircuitBreaker**: Manages failure thresholds to prevent system overload.
- **DSLParser**: Parses DSL content into `Rule` objects and helper blocks.
- **Rule**: Represents a single rule with configuration and logic blocks.
- **CompiledRule**: Encapsulates a compiled rule with method references.
- **DirectedGraph**: Models rule dependencies for topological sorting and cycle detection.

## Example Workflow
1. Define rules in a DSL file or string, specifying conditions, actions, and dependencies.
2. Load rules into `LWREngine` using the `Builder` pattern.
3. Set global variables and configure tracing or metrics if needed.
4. Execute rules synchronously (`executeRules`) or asynchronously (`executeRulesAsync`).
5. Retrieve results or handle exceptions (e.g., `RuleExecutionException`, `RuleTimeoutException`).

## Thread Safety
LWRE is designed for concurrent environments:
- Uses `ConcurrentHashMap` for shared state.
- Thread-local context pools reduce memory allocation overhead.
- Synchronized methods in `CircuitBreaker` ensure safe state updates.
- Asynchronous execution via `ForkJoinPool` and `ScheduledExecutorService`.

## Security
- Restricts access to dangerous classes (e.g., `java.lang.Runtime`, `java.io.File`) and methods (e.g., `exec`, `exit`).
- Validates DSL syntax to prevent reserved keyword misuse.
- Enforces timeouts to prevent infinite loops or long-running rules.

## Performance Optimizations
- Caches compiled rules to avoid redundant compilation.
- Precomputes execution paths using topological sorting.
- Uses thread-local pools for context maps.
- Parallelizes rule execution across groups and independent rules.

## License
This project is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

## Contact
For questions or support, contact [Hamdi Ghassen](mailto:hamdighassen@gmail.com) or open an issue on GitHub.