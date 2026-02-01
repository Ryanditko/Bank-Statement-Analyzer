(ns nubank-analyzer.analyzer
  "Transaction analysis and categorization module - v2"
  (:require [clojure.string :as str]
            [nubank-analyzer.logger :as log]))

(defn categorize-transaction
  "Categorize a transaction based on description and keywords"
  [transaction categories]
  (let [description (str/lower-case (or (:description transaction) ""))]
    (or (some (fn [[category-name config]]
                (when (some #(str/includes? description (str/lower-case %))
                            (:keywords config))
                  category-name))
              categories)
        "Others")))

(defn add-categories
  "Add category field to all transactions"
  [transactions categories]
  (log/info "Categorizing %d transactions..." (count transactions))
  (let [categorized (map (fn [tx]
                           (assoc tx :category
                                  (categorize-transaction tx categories)))
                         transactions)]
    (log/info "Categorization complete")
    categorized))

(defn extract-month-year
  "Extract month/year from ISO date"
  [date-str]
  (if (and date-str (string? date-str))
    (if-let [[_ y m] (re-find #"(\d{4})-(\d{2})" date-str)]
      (str m "/" y)
      "Unknown")
    "Unknown"))

(defn add-temporal-fields
  "Add temporal fields to transactions"
  [transactions]
  (map (fn [tx]
         (let [date (:date tx)]
           (assoc tx
                  :month (extract-month-year date)
                  :year (when date (subs date 0 4))
                  :month-num (when date (Integer/parseInt (subs date 5 7))))))
       transactions))

(defn calculate-basic-stats
  "Calculate basic statistics from a list of values"
  [values]
  (when (seq values)
    (let [n (count values)
          total (reduce + values)
          mean (double (/ total n))
          sorted (sort values)
          median (if (odd? n)
                   (nth sorted (quot n 2))
                   (/ (+ (nth sorted (quot n 2))
                         (nth sorted (dec (quot n 2))))
                      2))
          min-val (first sorted)
          max-val (last sorted)
          variance (/ (reduce + (map #(Math/pow (- % mean) 2) values)) n)
          std-dev (Math/sqrt variance)]

      {:count n
       :total total
       :mean mean
       :median median
       :min min-val
       :max max-val
       :std-dev std-dev
       :variance variance})))

(defn analyze-by-month
  "Analyze transactions grouped by month"
  [transactions]
  (let [by-month (group-by :month transactions)]
    (into (sorted-map)
          (for [[month txs] by-month]
            [month {:transactions-count (count txs)
                    :stats (calculate-basic-stats (map :amount txs))
                    :categories (frequencies (map :category txs))}]))))

(defn analyze-by-category
  "Analyze transactions grouped by category"
  [transactions]
  (let [by-category (group-by :category transactions)
        total-amount (reduce + (map :amount transactions))]
    (into {}
          (for [[category txs] by-category]
            (let [category-total (reduce + (map :amount txs))
                  stats (calculate-basic-stats (map :amount txs))]
              [category {:transactions-count (count txs)
                         :stats (assoc stats
                                       :percentage (* 100 (/ category-total total-amount)))
                         :merchants (frequencies (map :description txs))}])))))

(defn group-by-description
  "Group transactions by description for analysis"
  [transactions]
  (group-by :description transactions))

(defn get-transaction-key
  "Generate a key for transaction matching (for duplicates/recurring)"
  [transaction]
  [(extract-month-year (:date transaction))
   (str/lower-case (:description transaction))])

(defn find-top-expenses
  "Find top N expenses"
  ([transactions] (find-top-expenses transactions 10))
  ([transactions n]
   (let [total-amount (Math/abs (reduce + (map :amount transactions)))]
     (->> transactions
          (filter #(< (:amount %) 0))
          (map #(assoc % :abs-amount (Math/abs (:amount %))))
          (sort-by :abs-amount >)
          (take n)
          (map #(assoc % :percentage-of-total
                       (* 100 (/ (:abs-amount %) total-amount))))))))

(defn find-recurring-transactions
  "Find recurring transactions (appear in multiple months)"
  [transactions min-occurrences]
  (log/info "Detecting recurring transactions...")
  (let [by-description (group-by :description transactions)
        recurring (filter (fn [[_ txs]]
                            (and (>= (count txs) min-occurrences)
                                 (>= (count (distinct (map :month txs))) min-occurrences)))
                          by-description)]
    (log/info "Found %d recurring patterns" (count recurring))
    (map (fn [[desc txs]]
           {:description desc
            :count (count txs)
            :months (distinct (map :month txs))
            :average-amount (/ (reduce + (map :amount txs)) (count txs))
            :total-amount (reduce + (map :amount txs))})
         recurring)))

(defn find-duplicates
  "Find potential duplicate transactions"
  [transactions threshold-hours]
  (log/info "Detecting duplicate transactions...")
  (let [sorted (sort-by :date transactions)
        duplicates (atom [])]

    (doseq [[tx1 tx2] (partition 2 1 sorted)]
      (when (and (= (:description tx1) (:description tx2))
                 (= (:amount tx1) (:amount tx2))
                 (= (:date tx1) (:date tx2)))
        (swap! duplicates conj [tx1 tx2])))

    (log/info "Found %d potential duplicates" (count @duplicates))
    @duplicates))

(defn analyze-merchants
  "Analyze spending by merchant"
  [transactions]
  (let [by-merchant (group-by :description transactions)
        merchant-stats (map (fn [[merchant txs]]
                              {:merchant merchant
                               :count (count txs)
                               :total (reduce + (map :amount txs))
                               :average (/ (reduce + (map :amount txs)) (count txs))})
                            by-merchant)]
    {:by-total (take 20 (sort-by :total merchant-stats))
     :by-frequency (take 20 (sort-by :count > merchant-stats))}))

(defn calculate-trend
  "Calculate spending trend over months"
  [monthly-data]
  (let [months (sort (keys monthly-data))
        totals (map #(get-in monthly-data [% :stats :total] 0) months)]
    (if (< (count totals) 2)
      {:direction :insufficient-data
       :change-percentage 0}
      (let [first-total (first totals)
            last-total (last totals)
            change (- last-total first-total)
            change-pct (if (zero? first-total)
                         0
                         (* 100 (/ change (Math/abs first-total))))]
        {:direction (cond
                      (> change-pct 10) :increasing
                      (< change-pct -10) :decreasing
                      :else :stable)
         :change-percentage change-pct
         :first-month-total first-total
         :last-month-total last-total}))))

(defn analyze-trends
  "Analyze spending trends over time"
  [transactions]
  (let [by-month (analyze-by-month transactions)
        monthly-totals (into {} (map (fn [[m data]]
                                       [m (get-in data [:stats :total] 0)])
                                     by-month))]
    {:monthly-totals monthly-totals
     :trend (calculate-trend by-month)}))

(defn filter-transactions
  "Filter transactions based on criteria"
  [transactions filters]
  (cond->> transactions
    (:category filters)
    (filter #(= (:category %) (:category filters)))

    (:min-amount filters)
    (filter #(>= (Math/abs (:amount %)) (:min-amount filters)))

    (:max-amount filters)
    (filter #(<= (Math/abs (:amount %)) (:max-amount filters)))

    (:start-date filters)
    (filter #(>= (:date %) (:start-date filters)))

    (:end-date filters)
    (filter #(<= (:date %) (:end-date filters)))

    (:description-pattern filters)
    (filter #(str/includes? (str/lower-case (:description %))
                            (str/lower-case (:description-pattern filters))))))

(defn perform-complete-analysis
  "Perform complete analysis on transactions"
  [transactions config]
  (log/info "Starting complete analysis...")
  (log/info "Total transactions: %d" (count transactions))

  (let [categories (:categories config)
        categorized (add-categories transactions categories)
        with-temporal (add-temporal-fields categorized)
        amounts (map :amount with-temporal)
        expenses (filter #(< (:amount %) 0) with-temporal)

        general-stats (calculate-basic-stats amounts)
        by-month (analyze-by-month with-temporal)
        by-category (analyze-by-category with-temporal)
        top-expenses (find-top-expenses expenses 10)
        recurring (find-recurring-transactions with-temporal 2)
        duplicates (find-duplicates with-temporal
                                    (get-in config [:analysis :duplicate-threshold-hours] 24))
        trends (analyze-trends with-temporal)
        merchants (analyze-merchants with-temporal)]

    (log/info "Analysis complete!")

    {:general {:stats general-stats
               :total-transactions (count with-temporal)
               :total-expenses (count expenses)
               :total-income (count (filter #(>= (:amount %) 0) with-temporal))}
     :by-month by-month
     :by-category by-category
     :top-expenses top-expenses
     :recurring recurring
     :duplicates duplicates
     :trends trends
     :merchants merchants
     :transactions with-temporal}))