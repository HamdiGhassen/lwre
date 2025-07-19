# Lightweight Rule Engine (LWRE)

The Lightweight Rule Engine (LWRE) is a high-performance, Java-based rule engine designed for executing business rules defined via a Domain-Specific Language (DSL). It leverages Directed Acyclic Graphs (DAGs)  for dependency management, supports parallel rule execution, retry mechanisms, timeouts, and performance metrics. LWRE is ideal for dynamic rule-based workflows in applications like validation, automation, or decision-making, logistics, and IoT systems.

## Features

- **Intuitive DSL**: Define rules with a simple syntax supporting directives like `#RULE`, `#USE`, `#PRODUCE`, `#RETRY`, `#TIMEOUT`, and more.
- **Dependency Management**: Organize rules into DAGs, ensuring correct execution order using Kahn’s algorithm.
- **Parallel Execution**: Execute independent rules concurrently with a work-stealing thread pool.
- **Circuit Breaker**: Prevent engine overload with a configurable circuit breaker.
- **Performance Metrics**: Track execution time and frequency with built-in `Timer` and `Meter` metrics.
- **Security**: Block unsafe operations (e.g., `System.exit`) during rule compilation.
- **Lightweight Design**: Minimal footprint compared to heavier alternatives like Drools.

## Installation

### Prerequisites
- Java 8 or higher
- Maven 3.6.0 or higher

### Steps
1. Clone the repository:
   ```bash
   git clone https://github.com/HamdiGhassen/lwre.git
   ```
2. Navigate to the project directory:
   ```bash
   cd lwre
   ```
3. Build the project with Maven:
   ```bash
   mvn clean install
   ```

### Dependencies
LWRE requires the following dependency, included in `pom.xml`:
```xml
<dependency>
    <groupId>org.codehaus.janino</groupId>
    <artifactId>janino</artifactId>
    <version>3.1.12</version>
</dependency>
```

## DSL Syntax

The LWRE DSL allows users to define rules, dependencies, and helpers with a clear, structured syntax. Below are the supported directives with examples:

### `#HELPER`
Defines reusable helper functions available to all rules.
```dsl
#HELPER
boolean isEven(int num) {
    return num % 2 == 0;
}
```

### `#RULE <name>`
Defines a rule with a unique name.
```dsl
#RULE ComputeSum
```

### `#GROUP <group_name>`
Assigns the rule to a group for execution isolation (default: `MAIN`).
```dsl
#GROUP Validation
```

### `#PRIORITY <number>`
Sets the rule’s priority (higher numbers execute first within a group).
```dsl
#PRIORITY 10
```

### `#USE <variable> : <type> as <alias> FROM <source> [source_id]`
Declares input variables from global context or other rules.
```dsl
#USE
  input1 : Integer as a FROM Global
  result1 : Integer as r FROM RULE Rule1
```

### `#PRODUCE <variable> as <type>`
Declares output variables produced by the rule.
```dsl
#PRODUCE
  result as Integer
```

### `#CONDITION`
Defines a condition that must evaluate to `true` for the rule to execute.
```dsl
#CONDITION
  return a > 0;
```

### `#ACTION`
Specifies the main logic of the rule (Java code).
```dsl
#ACTION
  result = a + b;
```

### `#FINAL`
Defines the final output of the rule (must return a value).
```dsl
#FINAL
  return result;
```

### `#RETRY <count> DELAY <ms> [IF { condition }]`
Configures retry behavior for failed rules.
```dsl
#RETRY 3 DELAY 100 IF { error != null }
```

### `#TIMEOUT <duration>`
Sets a timeout for rule execution (e.g., `500ms` or `2s`).
```dsl
#TIMEOUT 500ms
```

### `#NEXT_ON_SUCCESS <rule_name>`
Specifies the next rule to execute on success.
```dsl
#NEXT_ON_SUCCESS ProcessNext
```

### `#NEXT_ON_FAILURE <rule_name>`
Specifies the next rule to execute on failure.
```dsl
#NEXT_ON_FAILURE HandleError
```

### `#VERSION <version>`
Sets the rule version (e.g., `1.0.0`).
```dsl
#VERSION 1.0.0
```

### `#MAX_EXECUTIONS <count>`
Limits the number of times a rule can execute.
```dsl
#MAX_EXECUTIONS 10
```

## Usage

### Basic Example
Compute a sum of two global inputs:
```dsl
#RULE ComputeSum
#USE
  input1 : Integer as a FROM Global
  input2 : Integer as b FROM Global
#PRODUCE
  result as Integer
#ACTION
  result = a + b;
#FINAL
  return result;
```
```java
import org.pulse.lwre.core.LWREngine;

public class Main {
    public static void main(String[] args) throws Exception {
        String dsl = // Load DSL from above
        LWREngine engine = new LWREngine.Builder()
            .rules(dsl)
            .global("input1", 20)
            .global("input2", 22)
            .build();
        Object result = engine.executeRules("MAIN");
        System.out.println("Result: " + result); // Outputs: Result: 42
    }
}
```

### Dependency Example
Chain rules with dependencies:
```dsl
#RULE Rule1
#PRODUCE
  result1 as Integer
#ACTION
  result1 = 21;

#RULE Rule2
#USE
  result1 : Integer as input FROM RULE Rule1
#PRODUCE
  result2 as Integer
#ACTION
  result2 = input * 2;
#FINAL
  return result2;
```

### Retry Example
Handle transient failures:
```dsl
#RULE RetryRule
#RETRY 3 DELAY 100 IF { error != null }
#PRODUCE
  result as Integer
#ACTION
  int count = context.get("count") != null ? (int)context.get("count") : 0;
  context.put("count", count + 1);
  if (count < 2) throw new Exception("Retry");
  result = 42;
#FINAL
  return result;
```

### Timeout Example
Limit rule execution time:
```dsl
#RULE TimeoutRule
#TIMEOUT 100ms
#ACTION
  Thread.sleep(50); // Succeeds
#FINAL
  return true;
```

More examples are in the `examples/` directory.

## Project Structure

```
lwre/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── org/pulse/lwre/core/
│   │   │   ├── org/pulse/lwre/dsl/
│   │   │   ├── org/pulse/lwre/metric/
│   │   │   ├── org/pulse/lwre/utils/
│   │   ├── resources/
│   ├── test/
│   │   ├── java/
│   │   ├── resources/
├── examples/
│   ├── simple_rule.dsl
│   ├── dependencies.dsl
│   ├── retry_rule.dsl
│   ├── timeout_rule.dsl
├── LICENSE.md
├── README.md
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
├── pom.xml
```

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details. The software is provided "as is," without warranty of any kind.

## Support

For issues or questions, open a GitHub issue. No official support or guarantees are provided.