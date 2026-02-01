(ns nubank-analyzer.core
  "Main orchestration module for all components"
  (:gen-class)
  (:require [nubank-analyzer.config :as config]
            [nubank-analyzer.logger :as log]
            [nubank-analyzer.parser :as parser]
            [nubank-analyzer.analyzer :as analyzer]
            [nubank-analyzer.validation :as validation]
            [nubank-analyzer.reports :as reports]
            [nubank-analyzer.cli :as cli]
            [clojure.java.io :as io]
            [clojure.string :as str]))


(defn initialize-system
  "Initialize system with configuration"
  [cli-options]
  (let [;; Load configuration
        config-file (:config cli-options)
        config (if config-file
                 (do
                   (log/info "Loading configuration: %s" config-file)
                   (config/load-config config-file))
                 (do
                   (log/debug "Using default configuration")
                   config/default-config))

        ;; Configure logging
        log-level (cond
                    (:debug cli-options) :debug
                    (:verbose cli-options) :debug
                    :else :info)]

    (log/configure! {:level log-level
                     :console true
                     :file (get-in config [:logging :file])})

    (log/info "Nubank Analyzer v%s started" (config/get-app-version config))
    config))

(defn process-transactions
  "Complete transaction processing pipeline"
  [input-file cli-options config]
  (try
    ;; 1. PARSING
    (log/info "============================================================")
    (log/info "STEP 1: CSV READING AND PARSING")
    (log/info "============================================================")

    (let [parse-result (parser/parse-transactions input-file config)
          transactions (:transactions parse-result)
          parse-stats (:stats parse-result)]

      (when (empty? transactions)
        (throw (ex-info "No valid transactions found in file"
                        {:file input-file})))

      ;; 2. VALIDATION
      (log/info "============================================================")
      (log/info "STEP 2: DATA VALIDATION")
      (log/info "============================================================")

      (let [validation-report (validation/generate-validation-report transactions config)]

        (when (:validate-only cli-options)
          (println (cli/format-validation-message (:validation validation-report)))
          (when (:verbose cli-options)
            (doseq [invalid (get-in validation-report [:validation :invalid-transactions])]
              (println (format "Line %d: %s"
                               (:index invalid)
                               (str/join ", " (:errors invalid))))))
          (System/exit 0))

        ;; 3. FILTERING
        (let [filters (cli/build-filters cli-options)
              filtered-txs (if (seq filters)
                             (do
                               (log/info "Applying filters: %s" filters)
                               (analyzer/filter-transactions transactions filters))
                             transactions)]

          (when (empty? filtered-txs)
            (log/warn "No transactions found after applying filters")
            (println "\n[WARNING] No transactions match the specified filters.\n")
            (System/exit 0))

          (log/info "%d transactions after filtering" (count filtered-txs))

          ;; 4. ANALYSIS
          (log/info "============================================================")
          (log/info "STEP 3: TRANSACTION ANALYSIS")
          (log/info "============================================================")

          (let [analysis (analyzer/perform-complete-analysis filtered-txs config)]

            ;; 5. REPORT GENERATION
            (log/info "============================================================")
            (log/info "STEP 4: REPORT GENERATION")
            (log/info "============================================================")

            {:analysis analysis
             :stats {:parse parse-stats
                     :validation validation-report
                     :filtered-count (count filtered-txs)}}))))

    (catch Exception e
      (log/exception "Error during processing" e)
      (throw e))))

(defn export-results
  "Export analysis results"
  [analysis cli-options input-file]
  (let [format-opt (keyword (:format cli-options))
        output-path (cli/determine-output-path cli-options input-file)]

    (if (= format-opt :all)
      ;; Export all formats
      (do
        (log/info "Exporting report in all formats...")
        (let [result (reports/export-all-formats analysis output-path)]
          (if (:success result)
            (do
              (println "\n[SUCCESS] Reports generated:")
              (doseq [r (:results result)]
                (println (format "  - %s" (:path r)))))
            (println "\n[ERROR] Failed to generate some reports"))))

      ;; Export specific format
      (let [format (or format-opt
                       (cli/determine-output-format cli-options))]
        (if output-path
          ;; Save to file
          (let [result (reports/export-report analysis format output-path)]
            (when (:success result)
              output-path))

          ;; Print to console
          (do
            (reports/generate-txt-report analysis *out*)
            nil))))))


(defn export-default-config
  "Export default configuration to file"
  [output-path]
  (log/info "Exporting default configuration to: %s" output-path)

  (try
    (config/generate-default-config-file output-path)
    (println (format "\n[SUCCESS] Default configuration exported to: %s\n" output-path))
    (println "You can edit this file and use it with the -c/--config option\n")
    0

    (catch Exception e
      (log/error "Error exporting configuration: %s" (.getMessage e))
      (println (format "\n[ERROR] Failed to export configuration: %s\n" (.getMessage e)))
      1)))


(defn -main
  "Application entry point"
  [& args]
  (let [start-time (System/currentTimeMillis)]

    ;; Parse CLI arguments
    (let [{:keys [options exit-message ok? action export-path]} (cli/validate-args args)]

      ;; Handle exit message (help or error)
      (when exit-message
        (println exit-message)
        (System/exit (if ok? 0 1)))

      ;; Handle special action (export-config)
      (when (= action :export-config)
        (System/exit (export-default-config export-path)))

      ;; Normal processing
      (try
        ;; Initialize system
        (let [config (initialize-system options)
              input-file (:input options)]

          ;; Check if file exists
          (when-not (.exists (io/file input-file))
            (log/error "File not found: %s" input-file)
            (println (format "\n[ERROR] File not found: %s\n" input-file))
            (System/exit 1))

          (log/info "Processing file: %s" input-file)

          ;; Process transactions
          (let [result (process-transactions input-file options config)
                analysis (:analysis result)]

            ;; Export results
            (let [output-path (export-results analysis options input-file)
                  elapsed (- (System/currentTimeMillis) start-time)]

              ;; Success message
              (when output-path
                (println (cli/format-success-message output-path elapsed)))

              (log/info "Processing completed in %.2fs" (/ elapsed 1000.0))
              (System/exit 0))))

        (catch clojure.lang.ExceptionInfo e
          (log/error "Error: %s" (.getMessage e))
          (println (format "\n[ERROR] %s\n" (.getMessage e)))
          (when (:verbose options)
            (.printStackTrace e))
          (System/exit 1))

        (catch Exception e
          (log/exception "Unexpected error" e)
          (println (format "\n[ERROR] Unexpected error: %s\n" (.getMessage e)))
          (when (or (:verbose options) (:debug options))
            (.printStackTrace e))
          (System/exit 1))))))


(defn analyze-file
  "Helper function for REPL usage
   Example: (analyze-file \"transactions.csv\")"
  [file-path]
  (let [config config/default-config]
    (log/configure! {:level :info :console true})
    (-> (parser/parse-transactions file-path config)
        :transactions
        (analyzer/perform-complete-analysis config))))

(defn quick-report
  "Generate quick report in console
   Example: (quick-report \"transactions.csv\")"
  [file-path]
  (let [analysis (analyze-file file-path)]
    (reports/generate-txt-report analysis *out*)
    :done))

(comment
  ;; REPL usage examples

  ;; Simple analysis
  (def analysis (analyze-file "example-transactions.csv"))

  ;; View report
  (quick-report "example-transactions.csv")

  ;; Export
  (reports/export-report analysis :html "report.html")

  ;; Statistics
  (get-in analysis [:general :stats])

  ;; By category
  (:by-category analysis))
