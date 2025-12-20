(ns clojure.client
  "Script for analyzing Nubank transactions
   - Reads CSV transactions exported from Nubank app
   - Automatically categorizes expenses
   - Generates monthly and category summaries
   - Detects possible duplicates
   - Calculates spending statistics"
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.string :as str]))

;; ============================================================================
;; Parsing Functions
;; ============================================================================

(defn parse-amount
  "Converts monetary value string to number.
   Accepts formats: 'R$ -1.234,56', '-1234.56', '1,234.56'"
  [s]
  (when (and s (not (str/blank? s)))
    (try
      (let [clean (-> s
                      (str/replace #"R\$\s*" "")           ; Remove R$
                      (str/replace #"\s+" "")              ; Remove spaces
                      (str/replace #"\.(?=\d{3})" "")      ; Remove thousands separator (dot)
                      (str/replace #"," "."))]             ; Decimal comma becomes dot
        (Double/parseDouble clean))
      (catch Exception _ nil))))

(defn parse-date
  "Converts date string to yyyy-MM-dd format"
  [date-str]
  (when-not (str/blank? date-str)
    (let [s (str/trim date-str)]
      (cond
        ;; dd/MM/yyyy -> yyyy-MM-dd
        (re-matches #"\d{2}/\d{2}/\d{4}" s)
        (let [[d m y] (str/split s #"/")] 
          (str y "-" m "-" d))
        
        ;; yyyy-MM-dd (already in format)
        (re-matches #"\d{4}-\d{2}-\d{2}" s)
        s
        
        :else
        date-str))))

(defn parse-row
  "Maps a CSV line to a transaction map.
   Expects columns: date/data, description/descrição, amount/valor"
  [headers row]
  (let [m (zipmap (map #(-> % str/trim str/lower-case) headers)
                  (map str/trim row))
        date-raw (or (get m "date") (get m "data"))
        desc (or (get m "description") (get m "descrição") (get m "descricao") (get m "title"))
        amount-str (or (get m "amount") (get m "valor") (get m "value"))]
    {:date (parse-date date-raw)
     :date-raw date-raw
     :description desc
     :amount (parse-amount amount-str)}))

;; ============================================================================
;; Automatic Categorization
;; ============================================================================

(def categorias-nubank
  "Map of categories with keywords for automatic classification"
  {"Food" ["restaurante" "lanchonete" "padaria" "ifood" "uber eats" 
           "rappi" "mcdonalds" "burger" "pizza" "açai" "acai"
           "cafe" "café" "bar" "pub"]
   
   "Transportation" ["uber" "99" "taxi" "metrô" "metro" "onibus" "ônibus"
                     "bus" "passagem" "combustivel" "combustível" "gasolina"
                     "posto" "ipiranga" "shell" "estacionamento"]
   
   "Subscriptions" ["spotify" "netflix" "amazon prime" "disney" "hbo"
                    "youtube" "apple music" "deezer" "globoplay"
                    "paramount" "crunchyroll" "prime video"]
   
   "Grocery" ["carrefour" "pão de açucar" "pao de acucar" "extra"
              "walmart" "mercado" "supermercado" "atacadão" "atacadao"
              "zaffari" "dia%" "sam's club"]
   
   "Health" ["drogaria" "farmacia" "farmácia" "clinica" "clínica"
             "hospital" "laboratorio" "laboratório" "consulta"
             "drogasil" "pacheco" "ultrafarma"]
   
   "Education" ["curso" "livro" "livraria" "udemy" "coursera"
                "faculdade" "escola" "material escolar"]
   
   "Entertainment" ["cinema" "teatro" "show" "ingresso" "parque"
                    "viagem" "hotel" "airbnb" "booking"]
   
   "Online Shopping" ["amazon" "mercado livre" "americanas" "magazine luiza"
                      "shopee" "aliexpress" "shein"]
   
   "Services" ["internet" "telefone" "celular" "luz" "energia"
               "água" "agua" "condominio" "condomínio" "aluguel"]
   
   "Transfers" ["pix" "transferencia" "transferência" "ted" "doc"]})

(defn categorizar
  "Determines category based on keywords in description"
  [descricao]
  (let [desc-lower (-> (or descricao "") str/lower-case)]
    (or (some (fn [[categoria keywords]]
                (when (some #(str/includes? desc-lower %) keywords)
                  categoria))
              categorias-nubank)
        "Others")))

;; ============================================================================
;; Transaction Analysis
;; ============================================================================

(defn month-key
  "Extracts month/year from date (format: MM/yyyy)"
  [date-str]
  (when date-str
    (if-let [[_ y m] (re-find #"(\d{4})-(\d{2})" date-str)]
      (str m "/" y)
      "Unknown")))

(defn read-transactions
  "Reads CSV file and returns vector of transactions"
  [file-path]
  (with-open [reader (io/reader file-path)]
    (let [all-lines (csv/read-csv reader)
          headers (first all-lines)
          rows (rest all-lines)]
      (->> rows
           (map #(parse-row headers %))
           (filter :amount)  ; Remove invalid lines
           (map #(assoc % 
                   :month (month-key (:date %))
                   :categoria (categorizar (:description %))))
           vec))))

(defn analyze-transactions
  "Analyzes transactions and generates complete statistics"
  [transactions]
  (let [;; General totals
        total-gasto (reduce + 0 (map :amount transactions))
        total-count (count transactions)
        
        ;; By month
        by-month (group-by :month transactions)
        month-summary (into (sorted-map)
                           (for [[mes txs] by-month]
                             [mes {:total (reduce + 0 (map :amount txs))
                                   :count (count txs)
                                   :media (/ (reduce + 0 (map :amount txs)) (count txs))}]))
        
        ;; By category
        by-category (group-by :categoria transactions)
        category-summary (into (sorted-map)
                              (for [[cat txs] by-category]
                                [cat {:total (reduce + 0 (map :amount txs))
                                      :count (count txs)
                                      :percent (* 100 (/ (reduce + 0 (map :amount txs)) 
                                                        total-gasto))}]))
        
        ;; Detect duplicates (same date + similar amount)
        dup-key (fn [t] 
                  [(:date t) 
                   (Math/round (* 100 (:amount t)))])
        groups (group-by dup-key transactions)
        duplicates (->> groups 
                       (filter #(> (count (val %)) 1)) 
                       (map val) 
                       vec)
        
        ;; Top 10 highest expenses
        top-10 (->> transactions
                    (sort-by :amount >)
                    (take 10))]
    
    {:resumo-geral {:total total-gasto
                    :quantidade total-count
                    :media (/ total-gasto total-count)}
     :por-mes month-summary
     :por-categoria category-summary
     :duplicatas duplicates
     :top-10-gastos top-10}))

;; ============================================================================
;; Reports
;; ============================================================================

(defn format-currency
  "Formats number as Brazilian currency"
  [n]
  (format "R$ %.2f" n))

(defn print-separator []
  (println (str/join (repeat 70 "="))))

(defn print-report
  "Prints complete report to console"
  [analysis]
  (println "\n")
  (print-separator)
  (println "          NUBANK TRANSACTION ANALYSIS")
  (print-separator)
  
  ;; General Summary
  (println "\n📊 GENERAL SUMMARY")
  (println "  Total spent:" (format-currency (get-in analysis [:resumo-geral :total])))
  (println "  Number of transactions:" (get-in analysis [:resumo-geral :quantidade]))
  (println "  Average per transaction:" (format-currency (get-in analysis [:resumo-geral :media])))
  
  ;; By Month
  (println "\n📅 EXPENSES BY MONTH")
  (doseq [[mes dados] (:por-mes analysis)]
    (println (format "  %s → %s (%d transactions, average: %s)"
                    mes
                    (format-currency (:total dados))
                    (:count dados)
                    (format-currency (:media dados)))))
  
  ;; By Category
  (println "\n🏷️  EXPENSES BY CATEGORY")
  (doseq [[cat dados] (reverse (sort-by #(get-in % [1 :total]) (:por-categoria analysis)))]
    (println (format "  %-20s %s (%d transactions, %.1f%%)"
                    cat
                    (format-currency (:total dados))
                    (:count dados)
                    (:percent dados))))
  
  ;; Top 10 Expenses
  (println "\n💰 TOP 10 HIGHEST EXPENSES")
  (doseq [[idx tx] (map-indexed vector (:top-10-gastos analysis))]
    (println (format "  %2d. %s | %s | %s"
                    (inc idx)
                    (:date tx)
                    (format-currency (:amount tx))
                    (or (:description tx) "No description"))))
  
  ;; Duplicates
  (println "\n⚠️  POSSIBLE DUPLICATES")
  (if (empty? (:duplicatas analysis))
    (println "  ✓ No duplicates found")
    (doseq [[idx group] (map-indexed vector (:duplicatas analysis))]
      (println (format "  Group %d:" (inc idx)))
      (doseq [tx group]
        (println (format "    - %s | %s | %s"
                        (:date tx)
                        (format-currency (:amount tx))
                        (:description tx))))))
  
  (println "\n")
  (print-separator))

(defn save-report
  "Saves report to text file"
  [analysis output-file]
  (with-open [w (io/writer output-file)]
    (binding [*out* w]
      (print-report analysis)))
  (println (str "✓ Report saved to: " output-file)))

;; ============================================================================
;; Main Function
;; ============================================================================

(defn -main
  "Main script function
   Usage: clojure -M -m clojure.client <file.csv> [output.txt]"
  [& args]
  (try
    (cond
      (empty? args)
      (do
        (println "❌ Error: Please provide the CSV file path")
        (println "\nUsage:")
        (println "  clojure -M -m clojure.client transactions.csv")
        (println "  clojure -M -m clojure.client transactions.csv report.txt"))
      
      :else
      (let [csv-file (first args)
            output-file (second args)]
        (println "📂 Reading file:" csv-file)
        (let [transactions (read-transactions csv-file)
              analysis (analyze-transactions transactions)]
          (println (format "✓ %d transactions loaded" (count transactions)))
          
          (if output-file
            (do
              (save-report analysis output-file)
              (println "\n✓ Analysis completed!"))
            (print-report analysis)))))
    
    (catch java.io.FileNotFoundException e
      (println "❌ Error: File not found -" (.getMessage e)))
    (catch Exception e
      (println "❌ Error processing:" (.getMessage e))
      (.printStackTrace e))))

(comment
  ;; Usage examples in REPL:
  
  ;; Read transactions
  (def txs (read-transactions "transactions.csv"))
  
  ;; Analyze
  (def analysis (analyze-transactions txs))
  
  ;; Print report
  (print-report analysis)
  
  ;; Save to file
  (save-report analysis "report.txt")
  
  ;; Filter by category
  (filter #(= (:categoria %) "Food") txs)
  
  ;; Transactions above R$ 100
  (filter #(> (:amount %) 100) txs)
  )
