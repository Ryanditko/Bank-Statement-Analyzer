(ns nubank-analyzer.reports
  "Report generation module supporting multiple formats: TXT, JSON, EDN, CSV, HTML"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.data.csv :as csv]
            [clojure.data.json :as json]
            [clojure.pprint :refer [pprint]]
            [clojure.stacktrace]
            [clojure.walk]
            [nubank-analyzer.logger :as log]))


(defn format-currency
  "Format number as Brazilian currency"
  [n]
  (format "R$ %,.2f" n))

(defn format-percentage
  "Format number as percentage"
  [n]
  (format "%.1f%%" n))

(defn format-date-br
  "Format ISO date to Brazilian format"
  [date-str]
  (if-let [[_ y m d] (re-find #"(\d{4})-(\d{2})-(\d{2})" date-str)]
    (str d "/" m "/" y)
    date-str))


(defn print-separator
  [length char]
  (str/join (repeat length char)))

(defn print-section-header
  [title]
  (str "\n" (print-separator 80 "=") "\n"
       "  " title "\n"
       (print-separator 80 "=")))

(defn generate-txt-report
  "Generate report in text format"
  [analysis output-stream]
  (binding [*out* output-stream]
    (println (print-separator 80 "="))
    (println "           NUBANK TRANSACTION ANALYSIS")
    (println "                    v2.0")
    (println (print-separator 80 "="))

    ;; GENERAL SUMMARY
    (println (print-section-header "GENERAL SUMMARY"))
    (let [stats (get-in analysis [:general :stats])]
      (println (format "  Total Transactions:       %d" (get-in analysis [:general :total-transactions])))
      (println (format "  Total Amount:             %s" (format-currency (:total stats))))
      (println (format "  Average per Transaction:  %s" (format-currency (:mean stats))))
      (println (format "  Median:                   %s" (format-currency (:median stats))))
      (println (format "  Largest Transaction:      %s" (format-currency (:max stats))))
      (println (format "  Smallest Transaction:     %s" (format-currency (:min stats))))
      (println (format "  Standard Deviation:       %s" (format-currency (:std-dev stats)))))

    ;; MONTHLY ANALYSIS
    (println (print-section-header "MONTHLY ANALYSIS"))
    (doseq [[month data] (:by-month analysis)]
      (let [stats (:stats data)]
        (println (format "\n  %s" month))
        (println (format "    Total:           %s (%d transactions)"
                         (format-currency (:total stats))
                         (:transactions-count data)))
        (println (format "    Average:         %s" (format-currency (:mean stats))))
        (println (format "    Largest expense: %s" (format-currency (:max stats))))
        (println "    Top 3 categories:")
        (doseq [[cat amount] (take 3 (sort-by val > (:categories data)))]
          (println (format "      %-20s %d" cat amount)))))

    ;; CATEGORY ANALYSIS
    (println (print-section-header "CATEGORY ANALYSIS"))
    (let [sorted-cats (sort-by #(get-in % [1 :stats :total]) > (:by-category analysis))]
      (doseq [[category data] sorted-cats]
        (let [stats (:stats data)]
          (println (format "\n  %s" category))
          (println (format "    Total:           %s (%s of total)"
                           (format-currency (:total stats))
                           (format-percentage (:percentage stats))))
          (println (format "    Transactions:    %d (avg: %s)"
                           (:transactions-count data)
                           (format-currency (:mean stats))))
          (println "    Top 3 merchants:")
          (doseq [[merchant freq] (take 3 (sort-by val > (:merchants data)))]
            (println (format "      %-40s (%dx)"
                             (subs merchant 0 (min 40 (clojure.core/count merchant)))
                             freq))))))

    ;; TOP 20 EXPENSES
    (println (print-section-header "TOP 20 LARGEST EXPENSES"))
    (doseq [[idx tx] (map-indexed vector (:top-expenses analysis))]
      (println (format "  %2d. %s | %s | %s | %s (%.1f%% of total)"
                       (inc idx)
                       (format-date-br (:date tx))
                       (format-currency (:amount tx))
                       (:categoria tx)
                       (subs (:description tx) 0 (min 35 (count (:description tx))))
                       (:percentage-of-total tx))))

    ;; RECURRING TRANSACTIONS
    (println (print-section-header "RECURRING TRANSACTIONS"))
    (if (empty? (:recurring analysis))
      (println "  No recurring transactions detected")
      (doseq [rec (take 10 (:recurring analysis))]
        (println (format "\n  %s" (:description rec)))
        (println (format "    Amount:          %s" (format-currency (:amount rec))))
        (println (format "    Occurrences:     %d times" (:occurrences rec)))
        (println (format "    Months:          %s" (str/join ", " (sort (:months rec)))))))

    ;; MERCHANTS
    (println (print-section-header "TOP MERCHANTS"))
    (println "\n  By Total Volume:")
    (doseq [[idx merchant] (map-indexed vector (take 10 (get-in analysis [:merchants :by-total])))]
      (println (format "    %2d. %-40s %s (%d transactions, %s)"
                       (inc idx)
                       (subs (:merchant merchant) 0 (min 40 (count (:merchant merchant))))
                       (format-currency (:total merchant))
                       (:count merchant)
                       (:category merchant))))

    (println "\n  By Frequency:")
    (doseq [[idx merchant] (map-indexed vector (take 10 (get-in analysis [:merchants :by-frequency])))]
      (println (format "    %2d. %-40s %dx (%s total)"
                       (inc idx)
                       (subs (:merchant merchant) 0 (min 40 (count (:merchant merchant))))
                       (:count merchant)
                       (format-currency (:total merchant)))))

    ;; TRENDS
    (println (print-section-header "SPENDING TRENDS"))
    (let [trends (:trends analysis)
          trend-data (:trend trends)]
      (println (format "  Overall trend:            %s"
                       (case (:direction trend-data)
                         :increasing "INCREASING"
                         :decreasing "DECREASING"
                         "STABLE")))
      (when (not= (:direction trend-data) :unknown)
        (println (format "  Change:                   %s"
                         (format-percentage (double (:change-percentage trend-data))))))
      (println "\n  Monthly Spending:")
      (doseq [[month total] (:monthly-totals trends)]
        (println (format "    %s: %s" month (format-currency total)))))

    ;; DUPLICATES
    (println (print-section-header "POSSIBLE DUPLICATES"))
    (if (empty? (:duplicates analysis))
      (println "  No duplicates detected")
      (do
        (println (format "  Found %d groups of possible duplicates:\n"
                         (count (:duplicates analysis))))
        (doseq [[idx group] (map-indexed vector (take 5 (:duplicates analysis)))]
          (println (format "  Group %d:" (inc idx)))
          (doseq [tx group]
            (println (format "    - %s | %s | %s"
                             (format-date-br (:date tx))
                             (format-currency (:amount tx))
                             (:description tx)))))))

    (println "\n" (print-separator 80 "="))
    (println "  Report generated by Nubank Analyzer v2.0")
    (println (print-separator 80 "=") "\n")))


(defn prepare-for-json
  "Prepare data for JSON serialization"
  [data]
  (clojure.walk/postwalk
   (fn [x]
     (cond
       (keyword? x) (name x)
       (set? x) (vec x)
       :else x))
   data))

(defn generate-json-report
  "Generate report in JSON format"
  [analysis output-stream]
  (let [json-data (prepare-for-json analysis)]
    (json/write json-data output-stream :indent true)))


(defn generate-edn-report
  "Generate report in EDN format"
  [analysis output-stream]
  (binding [*out* output-stream]
    (pprint analysis)))


(defn generate-csv-report
  "Generate transaction report in CSV format"
  [analysis output-stream]
  (let [transactions (:transactions analysis)
        headers ["Date" "Description" "Amount" "Category" "Month"]
        rows (map (fn [tx]
                    [(format-date-br (:date tx))
                     (:description tx)
                     (format "%.2f" (:amount tx))
                     (:categoria tx)
                     (:month tx)])
                  transactions)]
    (csv/write-csv output-stream (cons headers rows))))


(defn generate-html-report
  "Generate report in HTML format with styling"
  [analysis output-stream]
  (binding [*out* output-stream]
    (println "<!DOCTYPE html>")
    (println "<html lang='en'>")
    (println "<head>")
    (println "  <meta charset='UTF-8'>")
    (println "  <meta name='viewport' content='width=device-width, initial-scale=1.0'>")
    (println "  <title>Nubank Analysis - Report</title>")
    (println "  <style>")
    (println "    body { font-family: 'Segoe UI', Arial, sans-serif; margin: 20px; background: #f5f5f5; }")
    (println "    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }")
    (println "    h1 { color: #820AD1; text-align: center; border-bottom: 3px solid #820AD1; padding-bottom: 10px; }")
    (println "    h2 { color: #333; margin-top: 30px; border-left: 4px solid #820AD1; padding-left: 15px; }")
    (println "    .stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin: 20px 0; }")
    (println "    .stat-card { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; border-radius: 8px; }")
    (println "    .stat-label { font-size: 14px; opacity: 0.9; }")
    (println "    .stat-value { font-size: 24px; font-weight: bold; margin-top: 5px; }")
    (println "    table { width: 100%; border-collapse: collapse; margin: 20px 0; }")
    (println "    th { background: #820AD1; color: white; padding: 12px; text-align: left; }")
    (println "    td { padding: 10px; border-bottom: 1px solid #ddd; }")
    (println "    tr:hover { background: #f9f9f9; }")
    (println "    .category-badge { display: inline-block; padding: 5px 10px; border-radius: 15px; font-size: 12px; background: #e0e0e0; }")
    (println "    .amount { font-weight: bold; color: #d32f2f; }")
    (println "  </style>")
    (println "</head>")
    (println "<body>")
    (println "  <div class='container'>")
    (println "    <h1>Nubank Transaction Analysis</h1>")

    ;; Stats cards
    (let [stats (get-in analysis [:general :stats])]
      (println "    <div class='stat-grid'>")
      (println (format "      <div class='stat-card'><div class='stat-label'>Total Transactions</div><div class='stat-value'>%d</div></div>"
                       (get-in analysis [:general :total-transactions])))
      (println (format "      <div class='stat-card'><div class='stat-label'>Total Amount</div><div class='stat-value'>%s</div></div>"
                       (format-currency (:total stats))))
      (println (format "      <div class='stat-card'><div class='stat-label'>Average per Transaction</div><div class='stat-value'>%s</div></div>"
                       (format-currency (:mean stats))))
      (println (format "      <div class='stat-card'><div class='stat-label'>Largest Transaction</div><div class='stat-value'>%s</div></div>"
                       (format-currency (:max stats))))
      (println "    </div>"))

    ;; By Category
    (println "    <h2>Spending by Category</h2>")
    (println "    <table>")
    (println "      <thead><tr><th>Category</th><th>Total</th><th>% of Total</th><th>Transactions</th></tr></thead>")
    (println "      <tbody>")
    (doseq [[cat data] (sort-by #(get-in % [1 :stats :total]) > (:by-category analysis))]
      (println (format "        <tr><td>%s</td><td class='amount'>%s</td><td>%s</td><td>%d</td></tr>"
                       cat
                       (format-currency (get-in data [:stats :total]))
                       (format-percentage (get-in data [:stats :percentage]))
                       (:transactions-count data))))
    (println "      </tbody>")
    (println "    </table>")

    ;; Top 20
    (println "    <h2>Top 20 Largest Expenses</h2>")
    (println "    <table>")
    (println "      <thead><tr><th>#</th><th>Date</th><th>Amount</th><th>Category</th><th>Description</th></tr></thead>")
    (println "      <tbody>")
    (doseq [[idx tx] (map-indexed vector (take 20 (:top-expenses analysis)))]
      (println (format "        <tr><td>%d</td><td>%s</td><td class='amount'>%s</td><td><span class='category-badge'>%s</span></td><td>%s</td></tr>"
                       (inc idx)
                       (format-date-br (:date tx))
                       (format-currency (:amount tx))
                       (:categoria tx)
                       (:description tx))))
    (println "      </tbody>")
    (println "    </table>")

    (println "  </div>")
    (println "</body>")
    (println "</html>")))


(defn export-report
  "Export report in specified format"
  [analysis format output-path]
  (log/info "Generating %s report: %s" (name format) output-path)

  (try
    (io/make-parents output-path)
    (with-open [writer (io/writer output-path)]
      (case format
        :txt (generate-txt-report analysis writer)
        :json (generate-json-report analysis writer)
        :edn (generate-edn-report analysis writer)
        :csv (generate-csv-report analysis writer)
        :html (generate-html-report analysis writer)
        (throw (ex-info (str "Unsupported format: " format) {:format format}))))

    (log/info "Report generated successfully: %s" output-path)
    {:success true :path output-path :format format}

    (catch Exception e
      (log/error "Error generating report: %s" (.getMessage e))
      (log/error "Stack trace: %s" (with-out-str (clojure.stacktrace/print-stack-trace e)))
      {:success false :error (.getMessage e)})))

(defn export-all-formats
  "Export report in all formats"
  [analysis base-path]
  (log/info "Exporting report in all formats...")

  (let [formats [:txt :json :edn :csv :html]
        base (if (re-find #"\.\w+$" base-path)
               (str/replace base-path #"\.\w+$" "")
               base-path)
        results (doall
                 (for [fmt formats]
                   (let [ext (name fmt)
                         path (str base "." ext)]
                     (export-report analysis fmt path))))]

    {:results results
     :success (every? :success results)}))
