# Bank Statement Analyzer

> **A powerful Clojure-based tool for comprehensive bank transaction analysis**

Analyze your Nubank statements with automatic categorization, statistical insights, and flexible export formats.

---

## Overview

This tool processes CSV bank statements and generates detailed financial reports with:

- **Automatic categorization** across 9+ categories
- **Statistical analysis** (mean, median, std deviation, outliers)
- **Temporal trends** (monthly patterns, spending trajectories)
- **Duplicate detection** (identify repeated transactions)
- **Recurring transactions** (subscriptions and regular payments)
- **Multiple export formats** (TXT, JSON, EDN, CSV, HTML)

## Quick Example

```powershell
# Analyze transactions and generate HTML report
clj -M:run -i transactions.csv -o report.html -f html

# Filter high-value transactions
clj -M:run -i transactions.csv --min-amount 100

# Export all formats at once
clj -M:run -i transactions.csv -o report -f all
```

## Features

### Transaction Analysis

- **CSV Parsing** - Robust multi-format CSV file processing
- **Categorization** - 9 predefined categories (Food, Transport, Subscriptions, Shopping, Health, Entertainment, Bills, Education, Others)
- **Statistics** - Mean, median, min/max, standard deviation per category and month
- **Outlier Detection** - Identify unusual spending patterns
- **Duplicates** - Find potentially duplicate transactions
- **Recurring** - Detect subscriptions and regular payments
- **Merchants** - Analyze spending by establishment
- **Trends** - Monthly spending trajectories with direction indicators

### Export Formats

| Format | Description | Use Case |
|--------|-------------|----------|
| **TXT** | Formatted plain text | Quick reading and printing |
| **JSON** | Structured data | API integration and automation |
| **EDN** | Clojure native format | Advanced processing in Clojure |
| **CSV** | Processed transactions | Spreadsheet analysis |
| **HTML** | Interactive report | Visual exploration and sharing |

### Filtering & Customization

- Filter by category, amount range, date range
- Custom configuration files for personalized categories
- Flexible output paths and naming
- Verbose and debug logging modes

---

## Prerequisites

- **Clojure 1.11.1+**
- **Java 8+** (usually pre-installed)
- **Windows PowerShell** (for helper scripts)

---

---

## Installation

### Windows

Run the automated installer:

```powershell
.\install-clojure.ps1
```

**Important:** Restart your PowerShell terminal after installation.

For manual installation or troubleshooting, see [INSTALACAO-CLOJURE.md](INSTALACAO-CLOJURE.md).

### Verification

```powershell
# Check Clojure installation
clj -Sdescribe

# Run test suite
clj -M:dev -m kaocha.runner
```

Expected output: `25 tests, 101 assertions, 0 failures`

---

## Usage

### Basic Commands

```powershell
# Simple analysis (console output)
clj -M:run -i transactions.csv

# Generate specific format
clj -M:run -i transactions.csv -o report.html -f html

# Export all formats
clj -M:run -i transactions.csv -o report -f all
```

### Filtering

```powershell
# By category
clj -M:run -i transactions.csv --category Food

# By amount
clj -M:run -i transactions.csv --min-amount 100 --max-amount 500

# By date
clj -M:run -i transactions.csv --month "10/2025"
```

### Configuration

```powershell
# Export default config
clj -M:run --export-config my-config.edn

# Use custom config
clj -M:run -i transactions.csv -c my-config.edn
```

### Validation Only

```powershell
# Check CSV without analyzing
clj -M:run -i transactions.csv --validate-only
```

### Helper Scripts

```powershell
# Run tests
.\run-tests.ps1

# Analyze with script
.\run-analyzer.ps1 -InputFile "transactions.csv"
```

### All Options

```powershell
clj -M:run --help
```

For more examples, see [COMANDOS-RAPIDOS.md](COMANDOS-RAPIDOS.md).

---

## Configuration

### Custom Categories

Edit `my-config.edn`:

```edn
{:categories {"Custom Category"
              {:keywords ["keyword1" "keyword2"]
               :color "#FF0000"}}}
```

Then apply it:

```powershell
clj -M:run -i transactions.csv -c my-config.edn
```

---

## Getting Data from Nubank

1. Open the Nubank app
2. Navigate to **Menu** → **Credit Card**
3. Select the desired **invoice**
4. Tap **⋮** (menu) → **Export statement** → **CSV**
5. Transfer the CSV file to your computer
6. Run the analyzer on the exported file

---

## Development

### REPL Usage

```clojure
; Start REPL
; $ clj

; Load modules
(require '[nubank-analyzer.core :as core])
(require '[nubank-analyzer.reports :as reports])

; Analyze file
(def analysis (core/analyze-file "transactions.csv"))

; View statistics
(get-in analysis [:general :stats])

; Generate report
(reports/export-report analysis :html "report.html")
```

### Project Structure

```
Bank-Statement-Analyzer/
├── src/nubank_analyzer/
│   ├── core.clj          # Main orchestration
│   ├── cli.clj           # CLI argument handling
│   ├── parser.clj        # CSV parsing
│   ├── analyzer.clj      # Transaction analysis
│   ├── reports.clj       # Report generation
│   ├── validation.clj    # Data validation
│   ├── config.clj        # Configuration management
│   └── logger.clj        # Logging system
├── test/nubank_analyzer/ # Test suite
├── resources/            # Config examples
├── docs/                 # Documentation
└── deps.edn              # Dependencies
```

### Testing

```powershell
# Run all tests
clj -M:dev -m kaocha.runner

# Watch mode (auto-rerun on changes)
clj -M:dev -m kaocha.runner --watch

# Using helper script
.\run-tests.ps1
```

**Expected:** `25 tests, 101 assertions, 0 failures`

### Contributing

Contributions welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Write tests for new functionality
4. Ensure all tests pass
5. Follow existing code style
6. Commit with clear messages
7. Submit a pull request

See [docs/dev.md](docs/dev.md) for development guidelines.

---

## Documentation

- **[INSTALACAO-CLOJURE.md](INSTALACAO-CLOJURE.md)** - Complete installation guide
- **[COMANDOS-RAPIDOS.md](COMANDOS-RAPIDOS.md)** - Quick command reference
- **[docs/dev.md](docs/dev.md)** - Development and architecture guide
- **[README-NEXT-STEPS.md](README-NEXT-STEPS.md)** - Getting started guide

---

## Troubleshooting

### Common Issues

**Clojure not found:**
```powershell
# Restart PowerShell after installation
# Verify installation
clj -Sdescribe
```

**CSV parsing errors:**
```powershell
# Validate CSV structure
clj -M:run -i file.csv --validate-only
```

**Permission errors:**
```powershell
# Run PowerShell as Administrator
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
```

For more help, see [COMANDOS-RAPIDOS.md](COMANDOS-RAPIDOS.md).

---

## Roadmap

- [ ] Interactive HTML dashboard with charts
- [ ] Excel (.xlsx) export format
- [ ] REST API for web integration
- [ ] Machine learning-based categorization
- [ ] Budget forecasting and predictions
- [ ] Multi-bank support (beyond Nubank)
- [ ] Mobile app companion

---

## License

Free for personal and educational use.

---

## Acknowledgments

Built with [Clojure](https://clojure.org/) • Testing with [Kaocha](https://github.com/lambdaisland/kaocha) • Inspired by Nubank's transparency

---

*Made with ❤️ for better financial insights*
