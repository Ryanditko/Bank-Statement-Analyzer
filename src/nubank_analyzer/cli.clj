(ns nubank-analyzer.cli
  "Command-line interface for advanced transaction analysis"
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]))

;; ============================================================================
;; CLI Options Definition
;; ============================================================================

(def cli-options
  [["-i" "--input FILE" "Input CSV transactions file (required)"
    :required "FILE"]

   ["-o" "--output PATH" "Output path for report"
    :default nil]

   ["-f" "--format FORMAT" "Report format: txt, json, edn, csv, html, all"
    :default "txt"
    :parse-fn str/lower-case
    :validate [#(contains? #{"txt" "json" "edn" "csv" "html" "all"} %)
               "Format must be: txt, json, edn, csv, html or all"]]

   ["-c" "--config FILE" "Custom configuration file (EDN)"
    :default nil]

   [nil "--category CATEGORY" "Filter by specific category"
    :default nil]

   [nil "--min-amount AMOUNT" "Minimum amount to filter transactions"
    :parse-fn #(Double/parseDouble %)
    :default nil]

   [nil "--max-amount AMOUNT" "Maximum amount to filter transactions"
    :parse-fn #(Double/parseDouble %)
    :default nil]

   [nil "--month MONTH" "Filter by month (format: MM/YYYY)"
    :default nil]

   ["-v" "--verbose" "Verbose mode (detailed logging)"
    :default false]

   [nil "--debug" "Debug mode (full logging)"
    :default false]

   [nil "--no-duplicates" "Skip duplicate detection"
    :default false]

   [nil "--validate-only" "Only validate CSV without generating report"
    :default false]

   [nil "--export-config PATH" "Export default configuration to file"
    :default nil]

   ["-h" "--help" "Display this help message"
    :default false]])

;; ============================================================================
;; Help Messages
;; ============================================================================

(defn usage-banner []
  (str/join "\n"
            ["============================================================"
             "           NUBANK ANALYZER v2.0"
             "       Advanced Bank Transaction Analysis"
             "============================================================"
             ""]))

(defn usage [options-summary]
  (str/join "\n"
            [(usage-banner)
             "USAGE:"
             "  clojure -M -m nubank-analyzer.core [options]"
             ""
             "OPTIONS:"
             options-summary
             ""
             "EXAMPLES:"
             ""
             "  Basic analysis:"
             "    clojure -M -m nubank-analyzer.core -i transactions.csv"
             ""
             "  Save report in specific format:"
             "    clojure -M -m nubank-analyzer.core -i transactions.csv -o report.html -f html"
             ""
             "  Export all formats:"
             "    clojure -M -m nubank-analyzer.core -i transactions.csv -o report -f all"
             ""
             "  Filter by category:"
             "    clojure -M -m nubank-analyzer.core -i transactions.csv --category Food"
             ""
             "  Filter by amount:"
             "    clojure -M -m nubank-analyzer.core -i transactions.csv --min-amount 100"
             ""
             "  Use custom configuration:"
             "    clojure -M -m nubank-analyzer.core -i transactions.csv -c config.edn"
             ""
             "  Export default configuration:"
             "    clojure -M -m nubank-analyzer.core --export-config my-config.edn"
             ""
             "  Verbose mode:"
             "    clojure -M -m nubank-analyzer.core -i transactions.csv --verbose"
             ""
             "  Validate CSV only:"
             "    clojure -M -m nubank-analyzer.core -i transactions.csv --validate-only"
             ""
             "SUPPORTED FORMATS:"
             "  txt  - Formatted text report (default)"
             "  json - JSON format for integration"
             "  edn  - Clojure EDN format"
             "  csv  - Transactions in CSV"
             "  html - Interactive HTML report"
             "  all  - Export in all formats"
             ""
             "MORE INFO:"
             "  https://github.com/Ryanditko/Bank-Statement-Analyzer"
             ""]))

(defn error-msg [errors]
  (str "ERROR: Command line validation failed\n\n"
       (str/join "\n" (map #(str "  - " %) errors))
       "\n\nUse -h or --help to see available options.\n"))

;; ============================================================================
;; Argument Validation
;; ============================================================================

(defn validate-args
  "Validate command-line arguments"
  [args]
  (let [{:keys [options errors summary]} (parse-opts args cli-options)]
    (cond
      ;; Help requested
      (:help options)
      {:exit-message (usage summary) :ok? true}

      ;; Export config
      (:export-config options)
      {:action :export-config
       :export-path (:export-config options)
       :ok? true}

      ;; Parsing errors
      errors
      {:exit-message (error-msg errors)}

      ;; Input required (except for export-config)
      (and (not (:export-config options))
           (not (:input options)))
      {:exit-message (error-msg ["Input file (-i/--input) is required"])}

      ;; All good
      :else
      {:options options :ok? true})))

;; ============================================================================
;; Filter Construction
;; ============================================================================

(defn build-filters
  "Build filter map based on CLI options"
  [options]
  (cond-> {}
    (:category options) (assoc :category (:category options))
    (:min-amount options) (assoc :min-amount (:min-amount options))
    (:max-amount options) (assoc :max-amount (:max-amount options))
    (:month options) (assoc :month (:month options))))

;; ============================================================================
;; Output Format Determination
;; ============================================================================

(defn determine-output-format
  "Determine output format based on options"
  [options]
  (let [format-str (:format options)
        output-path (:output options)]

    (cond
      ;; Explicit format
      (not= format-str "txt")
      (keyword format-str)

      ;; Infer from file extension
      (and output-path (re-find #"\.(json|edn|csv|html)$" output-path))
      (keyword (second (re-find #"\.(\w+)$" output-path)))

      ;; Default
      :else
      :txt)))

(defn determine-output-path
  "Determine output path based on options"
  [options input-path]
  (or (:output options)
      (let [base-name (str/replace input-path #"\.\w+$" "")
            format (determine-output-format options)
            ext (name format)]
        (str base-name "-report." ext))))

;; ============================================================================
;; Message Formatting
;; ============================================================================

(defn format-success-message
  "Format success message"
  [output-path elapsed-ms]
  (let [elapsed-sec (/ elapsed-ms 1000.0)]
    (str "\n"
         "============================================================\n"
         "                    SUCCESS\n"
         "============================================================\n"
         "\n"
         (format "  Report generated: %s\n" output-path)
         (String/format java.util.Locale/US "  Execution time: %.2f seconds\n" (into-array Object [elapsed-sec]))
         "\n")))

(defn format-validation-message
  "Format validation result message"
  [validation-result]
  (let [valid (:valid-count validation-result)
        invalid (:invalid-count validation-result)
        total (+ valid invalid)
        success-rate (if (pos? total)
                       (* 100.0 (/ (double valid) (double total)))
                       0.0)]
    (str "\n"
         "============================================================\n"
         "                VALIDATION RESULTS\n"
         "============================================================\n"
         "\n"
         (format "  Total lines:        %d\n" total)
         (String/format java.util.Locale/US "  Valid lines:        %d (%.1f%%)\n" (into-array Object [valid success-rate]))
         (format "  Invalid lines:      %d\n" invalid)
         "\n"
         (if (pos? invalid)
           (str "  [WARNING] Invalid transactions detected. Use --verbose for details.\n\n")
           "  [OK] All transactions are valid!\n\n"))))

(comment
  ;; Test argument parsing
  (validate-args ["-i" "test.csv"])
  (validate-args ["-i" "test.csv" "-o" "out.json" "-f" "json"])
  (validate-args ["--help"])
  (validate-args ["-i" "test.csv" "--min-amount" "100" "--category" "Food"]))
