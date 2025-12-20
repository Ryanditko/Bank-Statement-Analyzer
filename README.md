# Bank Statement Analyzer

A Clojure-based tool for analyzing bank statements from Nubank with comprehensive transaction categorization, statistical analysis, and flexible export formats.

## Features

- **CSV Parsing**: Robust parsing of CSV files with support for multiple formats
- **Automatic Categorization**: 14 predefined categories with machine learning-based classification
- **Monthly Analysis**: Detailed statistics including totals, averages, medians, and standard deviation
- **Recurring Transaction Detection**: Identify recurring transactions and subscriptions
- **Trend Analysis**: Temporal analysis with trend identification capabilities
- **Top Spending**: Identify and rank top spending categories and merchants
- **Statistical Outliers**: Detect unusual transactions and spending patterns

## Export Formats

- **TXT**: Plain text format for reading
- **JSON**: Structured data for integration
- **EDN**: Clojure format for advanced processing
- **CSV**: Processed transactions in tabular format
- **HTML**: Interactive visual reports

## Prerequisites

- Clojure 1.11 or later
- Java 11 or later

## Installation

Clone the repository:

```bash
git clone https://github.com/Ryanditko/Bank-Statement-Analyzer.git
cd Bank-Statement-Analyzer
```

## Usage

### Basic Analysis

```bash
clojure -M -m nubank-analyzer.core -i transactions.csv
```

### With Custom Configuration

```bash
clojure -M -m nubank-analyzer.core -i transactions.csv -c my-config.edn
```

Edit your `my-config.edn` to add custom categories:

```edn
{:categories {"My Category"
              {:keywords ["keyword1" "keyword2"]
               :color "#FF0000"}}}
```

### Export Formats

Specify the output format and file:

```bash
clojure -M -m nubank-analyzer.core -i transactions.csv -o report.html -f html
```

## Extracting Data from Nubank

1. Open Nubank app
2. Navigate to **Menu** → **Credit Card**
3. Select desired **invoice/statement**
4. Click **⋮** → **Export statement** → **CSV**
5. Use the exported file with this tool

## REPL Development

For interactive development and testing:

```clojure
; Load the core module
(require '[nubank-analyzer.core :as core])

; Analyze a CSV file
(def analysis (core/analyze-file "transactions.csv"))

; View general statistics
(get-in analysis [:general :stats])

; Export reports
(require '[nubank-analyzer.reports :as reports])
(reports/export-report analysis :html "report.html")
```

## Testing

Run the test suite:

```bash
clojure -X:test
```

## License

This project is free for personal and educational use.

## Contributing

Contributions are welcome! For major changes, please open an issue first to discuss proposed modifications.

Pull requests should:
- Include clear commit messages
- Add tests for new functionality
- Update documentation as needed
