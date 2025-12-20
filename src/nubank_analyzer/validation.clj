(ns nubank-analyzer.validation
  "Validation module using Clojure Spec
   Validates transactions, dates, values and data structures"
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [nubank-analyzer.logger :as log])
  (:import [java.time LocalDate]
           [java.time.format DateTimeFormatter DateTimeParseException]))

;; ============================================================================
;; Transaction Specs
;; ============================================================================

(s/def ::date (s/and string? #(not (str/blank? %))))
(s/def ::description (s/and string? #(not (str/blank? %))))
(s/def ::amount number?)
(s/def ::category string?)
(s/def ::month string?)

(s/def ::transaction
  (s/keys :req-un [::date ::description ::amount]
          :opt-un [::category ::month ::date-raw]))

(s/def ::transactions
  (s/coll-of ::transaction :kind vector? :min-count 0))

;; ============================================================================
;; Configuration Specs
;; ============================================================================

(s/def ::name string?)
(s/def ::version string?)
(s/def ::app (s/keys :req-un [::name ::version]))

(s/def ::keywords (s/coll-of string? :kind vector?))
(s/def ::color string?)
(s/def ::icon string?)
(s/def ::category-config (s/keys :req-un [::keywords]))

(s/def ::categories (s/map-of string? ::category-config))

(s/def ::config
  (s/keys :req-un [::app ::categories]))

;; ============================================================================
;; Date Validation
;; ============================================================================

(defn valid-date-format?
  "Check if string is a valid date in a known format"
  [date-str formats]
  (boolean
   (some (fn [fmt]
           (try
             (LocalDate/parse date-str (DateTimeFormatter/ofPattern fmt))
             true
             (catch DateTimeParseException _ false)))
         formats)))

(defn validate-date
  "Valida e retorna erros de data"
  [date-str formats]
  (cond
    (str/blank? date-str)
    {:valid? false :error "Data vazia"}

    (not (valid-date-format? date-str formats))
    {:valid? false :error (str "Formato de data inválido: " date-str)}

    :else
    {:valid? true}))

;; ============================================================================
;; Monetary Value Validation
;; ============================================================================

(defn valid-amount?
  "Check if monetary value is valid"
  [amount]
  (and (number? amount)
       (not (Double/isNaN amount))
       (not (Double/isInfinite amount))))

(defn validate-amount
  "Validate monetary value"
  [amount]
  (cond
    (nil? amount)
    {:valid? false :error "Amount missing"}

    (not (number? amount))
    {:valid? false :error "Amount is not a number"}

    (Double/isNaN amount)
    {:valid? false :error "Invalid amount (NaN)"}

    (Double/isInfinite amount)
    {:valid? false :error "Infinite amount"}

    :else
    {:valid? true}))

;; ============================================================================
;; Transaction Validation
;; ============================================================================

(defn validate-transaction
  "Validate a complete transaction"
  [transaction date-formats]
  (let [errors (atom [])]

    ;; Validar data
    (let [date-result (validate-date (:date transaction) date-formats)]
      (when-not (:valid? date-result)
        (swap! errors conj (:error date-result))))

    ;; Validar descrição
    (when (str/blank? (:description transaction))
      (swap! errors conj "Descrição vazia"))

    ;; Validar amount
    (let [amount-result (validate-amount (:amount transaction))]
      (when-not (:valid? amount-result)
        (swap! errors conj (:error amount-result))))

    ;; Retornar resultado
    (if (empty? @errors)
      {:valid? true}
      {:valid? false :errors @errors})))

(defn validate-transactions
  "Validate list of transactions and return report"
  [transactions date-formats]
  (log/info "Validating %d transactions..." (count transactions))

  (let [results (map-indexed
                 (fn [idx tx]
                   (assoc (validate-transaction tx date-formats)
                          :index idx
                          :transaction tx))
                 transactions)
        invalid (filter #(not (:valid? %)) results)
        valid-count (- (count results) (count invalid))]

    (log/info "Validation completed: %d valid, %d invalid"
              valid-count (count invalid))

    {:valid-count valid-count
     :invalid-count (count invalid)
     :invalid-transactions invalid
     :all-valid? (empty? invalid)}))

;; ============================================================================
;; CSV Validation
;; ============================================================================

(defn validate-csv-headers
  "Validate if CSV has required headers"
  [headers]
  (let [required-fields #{"date" "data" "description" "descrição" "descricao"
                          "amount" "valor" "value"}
        headers-lower (set (map str/lower-case headers))
        has-date? (some #(contains? headers-lower %) ["date" "data"])
        has-desc? (some #(contains? headers-lower %) ["description" "descrição" "descricao" "title"])
        has-amount? (some #(contains? headers-lower %) ["amount" "valor" "value"])]

    (cond
      (not has-date?)
      {:valid? false :error "Coluna de data não encontrada (esperado: 'date' ou 'data')"}

      (not has-desc?)
      {:valid? false :error "Coluna de descrição não encontrada"}

      (not has-amount?)
      {:valid? false :error "Coluna de valor não encontrada (esperado: 'amount' ou 'valor')"}

      :else
      {:valid? true})))

(defn validate-csv-structure
  "Valida estrutura básica do CSV"
  [csv-data]
  (cond
    (empty? csv-data)
    {:valid? false :error "Arquivo CSV vazio"}

    (< (count csv-data) 2)
    {:valid? false :error "CSV deve ter pelo menos header e uma linha de dados"}

    :else
    (validate-csv-headers (first csv-data))))

;; ============================================================================
;; Suspicious Value Validation
;; ============================================================================

(defn detect-outliers
  "Detect outlier values using IQR method"
  [amounts threshold]
  (let [sorted (sort amounts)
        n (count sorted)
        q1-idx (quot n 4)
        q3-idx (quot (* 3 n) 4)
        q1 (nth sorted q1-idx)
        q3 (nth sorted q3-idx)
        iqr (- q3 q1)
        lower-bound (- q1 (* threshold iqr))
        upper-bound (+ q3 (* threshold iqr))]

    {:lower-bound lower-bound
     :upper-bound upper-bound
     :outliers (filter #(or (< % lower-bound) (> % upper-bound)) amounts)}))

(defn validate-amounts-distribution
  "Validate value distribution looking for anomalies"
  [transactions]
  (let [amounts (map :amount transactions)
        outlier-analysis (detect-outliers amounts 3.0)
        outlier-count (count (:outliers outlier-analysis))]

    (when (> outlier-count 0)
      (log/warn "Detected %d outlier values" outlier-count))

    {:outlier-count outlier-count
     :outlier-percentage (* 100 (/ outlier-count (count amounts)))
     :outlier-bounds {:lower (:lower-bound outlier-analysis)
                      :upper (:upper-bound outlier-analysis)}}))

;; ============================================================================
;; Sanitization Functions
;; ============================================================================

(defn sanitize-description
  "Remove special characters and normalize description"
  [desc]
  (-> desc
      str/trim
      (str/replace #"\s+" " ")
      (str/replace #"[^\w\s\-\.\,\(\)]" "")))

(defn sanitize-transaction
  "Sanitize transaction fields"
  [transaction]
  (-> transaction
      (update :description sanitize-description)
      (update :date str/trim)))

(defn sanitize-transactions
  "Sanitize list of transactions"
  [transactions]
  (map sanitize-transaction transactions))

;; ============================================================================
;; Validation Reports
;; ============================================================================

(defn generate-validation-report
  "Generate complete validation report"
  [transactions config]
  (log/info "Generating validation report...")

  (let [date-formats (get-in config [:parser :date-formats])
        validation-result (validate-transactions transactions date-formats)
        distribution-result (validate-amounts-distribution transactions)]

    {:validation validation-result
     :distribution distribution-result
     :summary {:total-transactions (count transactions)
               :valid-transactions (:valid-count validation-result)
               :invalid-transactions (:invalid-count validation-result)
               :success-rate (if (pos? (count transactions))
                               (* 100 (/ (:valid-count validation-result)
                                         (count transactions)))
                               0)}}))

(comment
  ;; Validate transaction
  (validate-transaction
   {:date "15/10/2025" :description "IFOOD" :amount -45.50}
   ["dd/MM/yyyy" "yyyy-MM-dd"])

  ;; Validate list
  (def txs [{:date "15/10/2025" :description "Test" :amount 100}
            {:date "invalid" :description "Bad" :amount nil}])
  (validate-transactions txs ["dd/MM/yyyy"])

  ;; Detect outliers
  (detect-outliers [10 20 30 40 50 1000] 3.0))
