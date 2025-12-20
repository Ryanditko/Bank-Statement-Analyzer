# Development Guide

This document provides guidelines for contributing to the Bank Statement Analyzer project and understanding its architecture.

## Project Architecture

### Execution Flow

```
CLI → Config → Parser → Validation → Analyzer → Reports
```

### Core Modules

**`src/nubank_analyzer/`**

- `core.clj` - Main orchestration and entry point
- `cli.clj` - Command-line argument handling
- `config.clj` - Centralized configuration management
- `logger.clj` - Logging system
- `parser.clj` - CSV file parsing and reading
- `validation.clj` - Data validation using Clojure Spec
- `analyzer.clj` - Transaction analysis and statistics
- `reports.clj` - Report generation and export

## Adding Features

### Adding a New Category

Edit `src/nubank_analyzer/config.clj`:

```clojure
:categories {"My Category"
             {:keywords ["keyword1" "keyword2"]
              :color "#FF0000"}}
```

### Adding a New Report Format

Edit `src/nubank_analyzer/reports.clj`:

1. Create a new function:

```clojure
(defn generate-xml-report [analysis output-stream]
  ; Implementation here
  )
```

2. Register it in the `export-report` function:

```clojure
(case format
  :xml (generate-xml-report analysis writer)
  ; ...
  )
```

### Adding New Analysis

Edit `src/nubank_analyzer/analyzer.clj`:

```clojure
(defn my-analysis [transactions]
  ; Your analysis logic
  )
```

Then integrate it into the main `analyze` function:

```clojure
(assoc analysis
  :my-metric (my-analysis transactions))
```

## Commit Message Convention

Follow the Conventional Commits format:

- `feat:` - New feature
- `fix:` - Bug fix
- `docs:` - Documentation
- `test:` - Tests
- `refactor:` - Code refactoring
- `perf:` - Performance improvement

Example:
```
feat: add PDF export format

Implement PDF report generation using iText library.
```

## Testing

### Running Tests

```bash
clojure -X:test
```

### Test Structure

Tests are located in `test/nubank_analyzer/` with the same module names as the source code:

- `analyzer_test.clj`
- `parser_test.clj`
- `validation_test.clj`

### Writing Tests

Use `clojure.test` and follow this pattern:

```clojure
(deftest my-test
  (is (= expected (function-under-test input))))
```

## Building

### Building JAR

```bash
clojure -X:uberjar
```

### Running JAR

```bash
java -jar nubank-analyzer.jar -i transactions.csv
```

## Development Workflow

1. Create a feature branch from `main`
2. Implement your changes
3. Write tests for new functionality
4. Run the test suite to verify
5. Commit with meaningful messages
6. Create a pull request with a clear description

## Code Style

- Use meaningful variable names
- Keep functions small and focused
- Document public functions with docstrings
- Follow Clojure conventions and idioms
- Use `let` bindings for intermediate results

## Common Tasks

### Debug a Specific Module

```clojure
(require '[nubank-analyzer.parser :as parser])
(parser/parse-csv "transactions.csv")
```

### Check Data Validation

```clojure
(require '[clojure.spec.alpha :as s])
(s/conform :transaction/spec your-transaction)
```

## Roadmap

- [ ] Interactive HTML dashboard
- [ ] Excel export
- [ ] REST API
- [ ] Web dashboard
- [ ] Machine learning categorization
- [ ] Budget forecasting
- [ ] Integration with other banking APIs
