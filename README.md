# Bank Statement Analyzer

> **A Clojure-based tool for analyzing bank statements**
>
> **⚠️ Disclaimer:** This is an independent educational project demonstrating data analysis with Clojure. It is **NOT** affiliated with, endorsed by, or officially associated with Nubank in any way. This tool uses Nubank's CSV export format as a practical example for financial data analysis.

Automatically categorize transactions, detect duplicates, and generate detailed financial reports from CSV bank statement exports.

---

## Overview

This tool processes CSV bank statements and provides:

- **Automatic categorization** into 10 predefined categories
- **Statistical analysis** (totals, averages, min/max per category and month)
- **Duplicate detection** to identify repeated transactions
- **Multiple export formats** (TXT, JSON, EDN, CSV, HTML)
- **Flexible filtering** by category, amount, and date
- **Custom configuration** for personalized categories

## Quick Start

```bash
# Basic analysis (console output)
clj -M:run -i transactions.csv

# Generate HTML report
clj -M:run -i transactions.csv -o report.html -f html

# Export all formats at once
clj -M:run -i transactions.csv -o report -f all
```

---

## Features

### Transaction Processing

- **CSV Parsing** - Reads Nubank CSV exports with date, description, and amount
- **Auto-categorization** - 10 categories: Food, Transportation, Subscriptions, Grocery, Health, Education, Entertainment, Online Shopping, Services, Transfers, Others
- **Statistics** - Calculates totals, counts, and averages per category and month
- **Duplicate Detection** - Identifies transactions with same date and similar amounts
- **Top Expenses** - Shows your highest spending transactions

### Export Formats

| Format | Description | Use Case |
|--------|-------------|----------|
| **TXT** | Formatted console text | Quick viewing |
| **JSON** | Structured data | API integration |
| **EDN** | Clojure data format | Advanced processing |
| **CSV** | Spreadsheet format | Excel/Sheets analysis |
| **HTML** | Web report | Visual exploration |

### Filtering Options

- Filter by specific category
- Set minimum/maximum amount thresholds
- Filter by month (MM/YYYY format)
- Validation-only mode (no report generation)

---

## Prerequisites

- **Clojure 1.11.1+**
- **Java 8+**

---

## Installation

### Clojure Installation

**Windows:**
```powershell
# Download and run the official installer from:
# https://github.com/clojure/tools.deps.alpha/wiki/clj-on-Windows
```

**macOS/Linux:**
```bash
# Using Homebrew
brew install clojure/tools/clojure
```

### Verify Installation

```bash
clj -Sdescribe
```

---

## Usage

### Basic Commands

```bash
# Simple analysis
clj -M:run -i example_transactions.csv

# Specify output format
clj -M:run -i transactions.csv -o report.txt -f txt
clj -M:run -i transactions.csv -o report.json -f json
clj -M:run -i transactions.csv -o report.html -f html

# Generate all formats
clj -M:run -i transactions.csv -o report -f all
```

### Filtering Examples

```bash
# Filter by category
clj -M:run -i transactions.csv --category Food

# Filter by amount range
clj -M:run -i transactions.csv --min-amount 50 --max-amount 200

# Filter by month
clj -M:run -i transactions.csv --month "10/2025"

# Combine filters
clj -M:run -i transactions.csv --category Transportation --min-amount 100
```

### Configuration

```bash
# Export default configuration template
clj -M:run --export-config my-config.edn

# Use custom configuration
clj -M:run -i transactions.csv -c my-config.edn
```

### Validation

```bash
# Validate CSV without generating report
clj -M:run -i transactions.csv --validate-only
```

### Additional Options

```bash
# Verbose logging
clj -M:run -i transactions.csv -v

# Debug mode
clj -M:run -i transactions.csv --debug

# Skip duplicate detection
clj -M:run -i transactions.csv --no-duplicates

# Show help
clj -M:run --help
```

---

## CSV Format

The tool expects a CSV file with the following columns:

```csv
date,description,amount
15/10/2025,IFOOD *IFOOD,R$ -45.50
14/10/2025,POSTO IPIRANGA,R$ -250.00
13/10/2025,Netflix Servicos,R$ -44.90
```

**Supported formats:**
- **Date**: `dd/MM/yyyy` or `yyyy-MM-dd`
- **Amount**: `R$ -1.234,56` or `-1234.56`
- **Headers**: Flexible (date/data, description/descrição, amount/valor)

---

## Getting Data from Nubank

1. Open the **Nubank app**
2. Go to **Menu** → **Credit Card**
3. Select the desired **invoice**
4. Tap **⋮** (menu) → **Export statement** → **CSV**
5. Transfer the CSV to your computer
6. Run the analyzer

---

## Configuration Files

### Custom Categories

Create a custom config file (e.g., `my-config.edn`):

```edn
{:categories
 {"My Custom Category"
  {:keywords ["keyword1" "keyword2" "brand name"]
   :color "#FF5733"}
  
  "Pets"
  {:keywords ["petshop" "veterinario" "vet" "racao"]
   :color "#4CAF50"}}}
```

Then use it:

```bash
clj -M:run -i transactions.csv -c my-config.edn
```

---

## Development

### Project Structure

```
Bank-Statement-Analyzer/
├── src/nubank_analyzer/
│   ├── core.clj          # Main orchestration
│   ├── cli.clj           # CLI argument parsing
│   ├── parser.clj        # CSV parsing logic
│   ├── analyzer.clj      # Transaction analysis
│   ├── reports.clj       # Report generation (TXT/JSON/EDN/CSV/HTML)
│   ├── validation.clj    # Data validation
│   ├── config.clj        # Configuration management
│   └── logger.clj        # Logging system
├── src/clojure/
│   └── client.clj        # Standalone script version
├── test/nubank_analyzer/ # Test suite
├── resources/
│   └── config-example.edn # Configuration example
├── docs/
│   └── dev.md            # Development guide
├── deps.edn              # Project dependencies
└── example_transactions.csv # Sample data
```

### Running Tests

```bash
# Run all tests
clj -M:dev -m kaocha.runner

# Watch mode (auto-rerun on changes)
clj -M:dev -m kaocha.runner --watch
```

### REPL Development

```clojure
; Start REPL
; $ clj

; Load namespaces
(require '[nubank-analyzer.core :as core])
(require '[nubank-analyzer.parser :as parser])
(require '[nubank-analyzer.analyzer :as analyzer])
(require '[nubank-analyzer.reports :as reports])

; Parse transactions
(def txs (parser/parse-csv-file "example_transactions.csv"))

; Analyze
(def analysis (analyzer/analyze-transactions txs))

; View results
(:general analysis)
(:by-category analysis)
(:by-month analysis)

; Generate report
(reports/export-report analysis :html "output.html")
```

---

## Categories

The tool automatically categorizes transactions into:

| Category | Examples |
|----------|----------|
| **Food** | Restaurants, iFood, cafes, fast food |
| **Transportation** | Uber, gas stations, parking, metro |
| **Subscriptions** | Netflix, Spotify, streaming services |
| **Grocery** | Supermarkets (Carrefour, Extra, etc.) |
| **Health** | Pharmacies, clinics, hospitals |
| **Education** | Courses, books, bookstores |
| **Entertainment** | Cinema, hotels, travel, shows |
| **Online Shopping** | Amazon, Mercado Livre, Shopee |
| **Services** | Internet, phone, utilities, rent |
| **Transfers** | PIX, bank transfers (TED/DOC) |
| **Others** | Uncategorized transactions |

---

## Troubleshooting

### Clojure not found
```bash
# Verify installation
clj -Sdescribe

# May need to restart terminal after installation
```

### CSV parsing errors
```bash
# Validate CSV first
clj -M:run -i file.csv --validate-only

# Check file encoding (should be UTF-8)
# Check date/amount formats match expected patterns
```

### Permission errors (Windows)
```powershell
# Run PowerShell as Administrator
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
```

---

## License

Free for personal and educational use.

---

## Important Notes

**This is an independent educational project:**
- Not affiliated with, endorsed by, or officially associated with Nubank
- Nubank's name and CSV format are used solely as practical examples for financial data analysis
- This tool can be adapted to work with CSV exports from any bank or financial institution
- All trademarks and registered trademarks are the property of their respective owners

---

## Acknowledgments

Built with [Clojure](https://clojure.org/) • Testing with [Kaocha](https://github.com/lambdaisland/kaocha)

---
