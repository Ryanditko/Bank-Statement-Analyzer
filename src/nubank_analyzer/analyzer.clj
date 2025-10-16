(ns nubank-analyzer.analyzer
  "Módulo de análise de transações
   Estatísticas, tendências, padrões e insights"
  (:require [clojure.string :as str]
            [nubank-analyzer.logger :as log]
            [nubank-analyzer.config :as config]))

;; ============================================================================
;; Categorização
;; ============================================================================

(defn categorize-transaction
  "Categoriza uma transação baseado em palavras-chave"
  [transaction categories-config]
  (let [desc-lower (-> (:description transaction "") str/lower-case)
        category (some (fn [[cat-name cat-data]]
                        (when (some #(str/includes? desc-lower %) 
                                   (:keywords cat-data))
                          cat-name))
                      categories-config)]
    (or category "Outros")))

(defn categorize-transactions
  "Adiciona categoria a todas as transações"
  [transactions config]
  (log/info "Categorizando %d transações..." (count transactions))
  
  (let [categories (get config :categories)
        categorized (map (fn [tx]
                          (assoc tx :categoria 
                                 (categorize-transaction tx categories)))
                        transactions)
        category-counts (frequencies (map :categoria categorized))]
    
    (log/debug "Distribuição por categoria: %s" category-counts)
    categorized))

;; ============================================================================
;; Análise Temporal
;; ============================================================================

(defn extract-month-year
  "Extrai mês/ano de uma data ISO"
  [date-str]
  (when date-str
    (if-let [[_ y m] (re-find #"(\d{4})-(\d{2})" date-str)]
      (str m "/" y)
      "Desconhecido")))

(defn add-temporal-fields
  "Adiciona campos temporais às transações"
  [transactions]
  (map (fn [tx]
         (let [date (:date tx)]
           (assoc tx
                  :month (extract-month-year date)
                  :year (when date (subs date 0 4))
                  :month-num (when date (Integer/parseInt (subs date 5 7))))))
       transactions))

;; ============================================================================
;; Estatísticas Básicas
;; ============================================================================

(defn calculate-basic-stats
  "Calcula estatísticas básicas de uma lista de valores"
  [values]
  (when (seq values)
    (let [n (count values)
          total (reduce + values)
          mean (/ total n)
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

;; ============================================================================
;; Análise por Período
;; ============================================================================

(defn analyze-by-month
  "Analisa transações agrupadas por mês"
  [transactions]
  (let [by-month (group-by :month transactions)]
    (into (sorted-map)
          (for [[month txs] by-month]
            (let [amounts (map :amount txs)
                  stats (calculate-basic-stats amounts)
                  by-category (group-by :categoria txs)
                  category-totals (into {}
                                       (for [[cat cat-txs] by-category]
                                         [cat (reduce + (map :amount cat-txs))]))]
              [month {:stats stats
                      :transactions-count (count txs)
                      :by-category category-totals
                      :top-5 (take 5 (sort-by :amount > txs))}])))))

(defn analyze-by-category
  "Analisa transações agrupadas por categoria"
  [transactions]
  (let [by-category (group-by :categoria transactions)
        total-amount (reduce + (map :amount transactions))]
    (into (sorted-map)
          (for [[category txs] by-category]
            (let [amounts (map :amount txs)
                  stats (calculate-basic-stats amounts)
                  percentage (* 100 (/ (:total stats) total-amount))]
              [category {:stats (assoc stats :percentage percentage)
                        :transactions-count (count txs)
                        :top-expenses (take 5 (sort-by :amount > txs))
                        :merchants (frequencies (map :description txs))}])))))

;; ============================================================================
;; Detecção de Duplicatas
;; ============================================================================

(defn generate-duplicate-key
  "Gera chave para detecção de duplicatas"
  [transaction]
  [(extract-month-year (:date transaction))
   (Math/round (* 100 (:amount transaction)))
   (-> (:description transaction "") str/lower-case (subs 0 (min 20 (count (:description transaction "")))))])

(defn detect-duplicates
  "Detecta possíveis transações duplicadas"
  [transactions]
  (log/info "Detectando duplicatas...")
  
  (let [grouped (group-by generate-duplicate-key transactions)
        duplicates (->> grouped
                       (filter #(> (count (val %)) 1))
                       (map val)
                       vec)]
    
    (log/info "Encontrados %d grupos de possíveis duplicatas" (count duplicates))
    duplicates))

;; ============================================================================
;; Análise de Padrões
;; ============================================================================

(defn find-recurring-transactions
  "Identifica transações recorrentes (assinaturas, etc)"
  [transactions]
  (log/info "Buscando transações recorrentes...")
  
  (let [;; Agrupar por descrição similar e valor próximo
        by-desc (group-by (fn [tx]
                           [(-> (:description tx) str/lower-case (subs 0 (min 15 (count (:description tx)))))
                            (Math/round (:amount tx))])
                         transactions)
        recurring (->> by-desc
                      (filter (fn [[k v]] (>= (count v) 2)))
                      (map (fn [[k v]]
                            {:description (-> v first :description)
                             :amount (-> v first :amount)
                             :occurrences (count v)
                             :months (set (map :month v))
                             :transactions v}))
                      (sort-by :occurrences >))]
    
    (log/info "Encontradas %d transações recorrentes" (count recurring))
    recurring))

(defn analyze-spending-trends
  "Analisa tendências de gastos ao longo do tempo"
  [transactions]
  (let [by-month (group-by :month transactions)
        monthly-totals (into (sorted-map)
                            (for [[month txs] by-month]
                              [month (reduce + (map :amount txs))]))
        months (keys monthly-totals)
        totals (vals monthly-totals)
        stats (calculate-basic-stats totals)]
    
    {:monthly-totals monthly-totals
     :stats stats
     :trend (if (>= (count totals) 2)
             (let [first-half (take (quot (count totals) 2) totals)
                   second-half (drop (quot (count totals) 2) totals)
                   first-avg (/ (reduce + first-half) (count first-half))
                   second-avg (/ (reduce + second-half) (count second-half))
                   change-pct (* 100 (/ (- second-avg first-avg) first-avg))]
               {:direction (if (pos? change-pct) :increasing :decreasing)
                :change-percentage change-pct})
             {:direction :unknown :change-percentage 0})}))

;; ============================================================================
;; Top Gastos e Insights
;; ============================================================================

(defn find-top-expenses
  "Encontra maiores gastos com contexto"
  [transactions n]
  (->> transactions
       (sort-by :amount >)
       (take n)
       (map (fn [tx]
             (assoc tx :percentage-of-total
                    (* 100 (/ (:amount tx)
                            (reduce + (map :amount transactions)))))))))

(defn analyze-merchants
  "Analisa estabelecimentos mais frequentes"
  [transactions]
  (let [merchant-groups (group-by :description transactions)
        merchant-stats (for [[merchant txs] merchant-groups]
                        {:merchant merchant
                         :total (reduce + (map :amount txs))
                         :count (count txs)
                         :average (/ (reduce + (map :amount txs)) (count txs))
                         :category (:categoria (first txs))})]
    
    {:by-total (take 10 (sort-by :total > merchant-stats))
     :by-frequency (take 10 (sort-by :count > merchant-stats))}))

;; ============================================================================
;; Análise Completa
;; ============================================================================

(defn perform-complete-analysis
  "Executa análise completa das transações"
  [transactions config]
  (log/info "Iniciando análise completa de %d transações..." (count transactions))
  
  (log/with-timing "Análise completa"
    (let [;; Adicionar campos temporais e categorias
          enriched-txs (-> transactions
                          (add-temporal-fields)
                          (categorize-transactions config))
          
          ;; Estatísticas gerais
          all-amounts (map :amount enriched-txs)
          general-stats (calculate-basic-stats all-amounts)
          
          ;; Análises específicas
          by-month (analyze-by-month enriched-txs)
          by-category (analyze-by-category enriched-txs)
          duplicates (detect-duplicates enriched-txs)
          recurring (find-recurring-transactions enriched-txs)
          trends (analyze-spending-trends enriched-txs)
          top-expenses (find-top-expenses enriched-txs 20)
          merchants (analyze-merchants enriched-txs)]
      
      (log/info "Análise concluída com sucesso")
      
      {:general {:stats general-stats
                 :total-transactions (count enriched-txs)}
       :by-month by-month
       :by-category by-category
       :duplicates duplicates
       :recurring recurring
       :trends trends
       :top-expenses top-expenses
       :merchants merchants
       :transactions enriched-txs})))

;; ============================================================================
;; Filtros e Queries
;; ============================================================================

(defn filter-transactions
  "Filtra transações com múltiplos critérios"
  [transactions {:keys [category min-amount max-amount month year description]}]
  (cond->> transactions
    category (filter #(= (:categoria %) category))
    min-amount (filter #(>= (:amount %) min-amount))
    max-amount (filter #(<= (:amount %) max-amount))
    month (filter #(= (:month %) month))
    year (filter #(= (:year %) year))
    description (filter #(str/includes? (str/lower-case (:description %)) 
                                       (str/lower-case description)))))

(comment
  ;; Análise completa
  (def config (config/default-config))
  (def analysis (perform-complete-analysis transactions config))
  
  ;; Filtrar transações
  (filter-transactions transactions {:category "Alimentação" :min-amount 50})
  
  ;; Top gastos
  (find-top-expenses transactions 10)
  )
