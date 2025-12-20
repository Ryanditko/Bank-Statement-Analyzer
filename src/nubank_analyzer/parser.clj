(ns nubank-analyzer.parser
  "Robust CSV and transaction parsing module
   Supports multiple date and currency formats"
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [nubank-analyzer.logger :as log]
            [nubank-analyzer.validation :as v])
  (:import [java.time LocalDate]
           [java.time.format DateTimeFormatter DateTimeParseException]))

;; ============================================================================
;; Monetary Value Parsing
;; ============================================================================

(defn parse-amount
  "Convert monetary value string to double number.
   Supports Brazilian and international formats:
   - R$ -1.234,56
   - -1234.56
   - (1,234.56) for negative values
   - 1.234,56"
  [s]
  (when (and s (not (str/blank? s)))
    (try
      (let [;; Detect negative by parentheses
            is-negative-parens? (and (str/starts-with? s "(")
                                     (str/ends-with? s ")"))
            ;; Clean string
            clean (-> s
                      (str/replace #"[R\$\s\(\)]" "")     ; Remove R$, spaces and parentheses
                      (str/trim))
            ;; Detect format (BR uses comma decimal, US/INT uses period)
            has-comma-decimal? (re-find #",\d{2}$" clean)
            ;; Process based on format
            normalized (if has-comma-decimal?
                         ;; Brazilian format: 1.234,56
                         (-> clean
                             (str/replace "." "")       ; Remove thousand separator
                             (str/replace "," "."))      ; Comma becomes period
                         ;; International format: 1,234.56
                         (str/replace clean "," ""))           ; Remove thousand separator
            value (Double/parseDouble normalized)]

        ;; Apply negative sign if necessary
        (if (or is-negative-parens?
                (and (not is-negative-parens?) (< value 0)))
          (- (Math/abs value))
          (Math/abs value)))

      (catch Exception e
        (log/warn "Error parsing value '%s': %s" s (.getMessage e))
        nil))))

;; ============================================================================
;; Date Parsing
;; ============================================================================

(defn try-parse-date
  "Try to parse date with a specific format"
  [date-str format-str]
  (try
    (let [formatter (DateTimeFormatter/ofPattern format-str)
          date (LocalDate/parse date-str formatter)]
      (.format date (DateTimeFormatter/ISO_LOCAL_DATE)))
    (catch DateTimeParseException _ nil)))

(defn parse-date
  "Convert date string to ISO format (yyyy-MM-dd).
   Try multiple formats automatically"
  [date-str formats]
  (when-not (str/blank? date-str)
    (let [trimmed (str/trim date-str)
          ;; Try each configured format
          parsed (some #(try-parse-date trimmed %) formats)]
      (or parsed
          (do
            (log/debug "Date '%s' could not be parsed with known formats" trimmed)
            trimmed)))))

;; ============================================================================
;; CSV Header Parsing
;; ============================================================================

(defn normalize-header
  "Normalize CSV column name"
  [header]
  (-> header
      str/trim
      str/lower-case
      (str/replace #"\s+" "-")
      (str/replace #"[áàâã]" "a")
      (str/replace #"[éèê]" "e")
      (str/replace #"[íì]" "i")
      (str/replace #"[óòôõ]" "o")
      (str/replace #"[úù]" "u")
      (str/replace #"[ç]" "c")))

(defn map-header-to-field
  "Map CSV header to internal field"
  [header]
  (let [normalized (normalize-header header)]
    (cond
      (some #(str/includes? normalized %) ["date" "data"])
      :date

      (some #(str/includes? normalized %) ["description" "descricao" "title" "estabelecimento"])
      :description

      (some #(str/includes? normalized %) ["amount" "valor" "value" "price"])
      :amount

      (some #(str/includes? normalized %) ["category" "categoria"])
      :category

      (some #(str/includes? normalized %) ["type" "tipo"])
      :type

      :else
      (keyword normalized))))

(defn build-field-mapping
  "Cria mapeamento de índices de coluna para campos"
  [headers]
  (into {}
        (map-indexed (fn [idx header]
                       [(map-header-to-field header) idx])
                     headers)))

;; ============================================================================
;; CSV Row Parsing
;; ============================================================================

(defn parse-csv-row
  "Parse a CSV row using field mapping"
  [row field-mapping date-formats]
  (try
    (let [get-field (fn [field]
                      (when-let [idx (get field-mapping field)]
                        (nth row idx nil)))
          date-raw (get-field :date)
          description (get-field :description)
          amount-str (get-field :amount)]

      (when (and date-raw description amount-str)
        {:date-raw date-raw
         :date (parse-date date-raw date-formats)
         :description (str/trim description)
         :amount (parse-amount amount-str)
         :category (get-field :category)
         :type (get-field :type)}))

    (catch Exception e
      (log/warn "Erro ao parsear linha: %s" (.getMessage e))
      nil)))

;; ============================================================================
;; Leitura de CSV
;; ============================================================================

(defn read-csv-file
  "Read CSV file and return [headers rows]"
  [file-path encoding]
  (log/info "Reading CSV: %s (encoding: %s)" file-path encoding)

  (try
    (with-open [reader (io/reader file-path :encoding encoding)]
      (let [data (csv/read-csv reader)
            headers (first data)
            rows (rest data)]

        (log/debug "Headers found: %s" (str/join ", " headers))
        (log/debug "Total lines: %d" (count rows))

        {:headers headers
         :rows rows
         :line-count (count rows)}))

    (catch java.io.FileNotFoundException e
      (log/error "File not found: %s" file-path)
      (throw e))

    (catch Exception e
      (log/error "Error reading CSV: %s" (.getMessage e))
      (throw e))))

;; ============================================================================
;; Complete Parsing Pipeline
;; ============================================================================

(defn parse-transactions
  "Complete pipeline: read CSV and parse transactions
   Returns {:transactions [...] :stats {...} :errors [...]}"
  [file-path config]
  (log/with-timing (str "Parsing " file-path)
    (let [;; Extrair configurações
          encoding (get-in config [:csv :encoding] "UTF-8")
          date-formats (get-in config [:parser :date-formats])

          ;; Ler CSV
          csv-data (read-csv-file file-path encoding)
          headers (:headers csv-data)
          rows (:rows csv-data)

          ;; Validar estrutura
          validation (v/validate-csv-headers headers)
          _ (when-not (:valid? validation)
              (throw (ex-info (:error validation) validation)))

          ;; Create field mapping
          field-mapping (build-field-mapping headers)
          _ (log/debug "Field mapping: %s" field-mapping)

          ;; Parse rows
          parsed-transactions (keep #(parse-csv-row % field-mapping date-formats) rows)

          ;; Filter valid transactions
          valid-transactions (filter #(and (:date %) (:amount %)) parsed-transactions)

          ;; Statistics
          total-rows (count rows)
          parsed-count (count parsed-transactions)
          valid-count (count valid-transactions)
          failed-count (- total-rows valid-count)]

      (log/info "Parsing completed: %d/%d valid transactions (%.1f%%)"
                valid-count total-rows
                (* 100.0 (/ valid-count (max total-rows 1))))

      (when (> failed-count 0)
        (log/warn "%d rows failed to parse" failed-count))

      {:transactions valid-transactions
       :stats {:total-rows total-rows
               :parsed-count parsed-count
               :valid-count valid-count
               :failed-count failed-count
               :success-rate (* 100.0 (/ valid-count (max total-rows 1)))}
       :field-mapping field-mapping})))

;; ============================================================================
;; Multiple File Parsing
;; ============================================================================

(defn parse-multiple-files
  "Parse multiple CSV files and combine results"
  [file-paths config]
  (log/info "Parsing %d files..." (count file-paths))

  (let [results (map #(parse-transactions % config) file-paths)
        all-transactions (vec (mapcat :transactions results))
        combined-stats {:total-files (count file-paths)
                        :total-transactions (count all-transactions)
                        :files (map (fn [path result]
                                      {:path path
                                       :stats (:stats result)})
                                    file-paths results)}]

    (log/info "Total of %d transactions from %d files"
              (count all-transactions) (count file-paths))

    {:transactions all-transactions
     :stats combined-stats}))

;; ============================================================================
;; Export Utilities
;; ============================================================================

(defn transactions-to-csv
  "Export transactions to CSV format"
  [transactions output-path]
  (log/info "Exporting %d transactions to CSV: %s" (count transactions) output-path)

  (try
    (with-open [writer (io/writer output-path)]
      (csv/write-csv writer
                     (cons ["Date" "Description" "Amount" "Category" "Month"]
                           (map (fn [tx]
                                  [(:date tx)
                                   (:description tx)
                                   (format "%.2f" (:amount tx))
                                   (:categoria tx "")
                                   (:month tx "")])
                                transactions))))
    (log/info "CSV exportado com sucesso")
    {:success true :path output-path}

    (catch Exception e
      (log/error "Error exporting CSV: %s" (.getMessage e))
      {:success false :error (.getMessage e)})))

(comment
  ;; Test amount parsing
  (parse-amount "R$ -1.234,56")  ; => -1234.56
  (parse-amount "(1,234.56)")    ; => -1234.56
  (parse-amount "1234.56")       ; => 1234.56

  ;; Test date parsing
  (parse-date "15/10/2025" ["dd/MM/yyyy" "yyyy-MM-dd"])  ; => "2025-10-15"

  ;; Parse file
  (def config {:csv {:encoding "UTF-8"}
               :parser {:date-formats ["dd/MM/yyyy" "yyyy-MM-dd"]}})
  (def result (parse-transactions "exemplo-transacoes.csv" config)))
