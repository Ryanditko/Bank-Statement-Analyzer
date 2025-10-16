(ns nubank-analyzer.reports
  "Módulo de geração de relatórios em múltiplos formatos
   Suporta: TXT, JSON, EDN, CSV, HTML"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.data.csv :as csv]
            [clojure.data.json :as json]
            [clojure.pprint :refer [pprint]]
            [nubank-analyzer.logger :as log]))

;; ============================================================================
;; Formatação de Valores
;; ============================================================================

(defn format-currency
  "Formata número como moeda brasileira"
  [n]
  (format "R$ %,.2f" n))

(defn format-percentage
  "Formata número como percentual"
  [n]
  (format "%.1f%%" n))

(defn format-date-br
  "Formata data ISO para formato brasileiro"
  [date-str]
  (if-let [[_ y m d] (re-find #"(\d{4})-(\d{2})-(\d{2})" date-str)]
    (str d "/" m "/" y)
    date-str))

;; ============================================================================
;; Relatório TXT
;; ============================================================================

(defn print-separator
  [length char]
  (str/join (repeat length char)))

(defn print-section-header
  [title]
  (str "\n" (print-separator 80 "=") "\n"
       "  " title "\n"
       (print-separator 80 "=")))

(defn generate-txt-report
  "Gera relatório em formato texto"
  [analysis output-stream]
  (binding [*out* output-stream]
    (println (print-separator 80 "="))
    (println "              ANÁLISE TRANSAÇÕES NUBANK")
    (println "              Professional Edition v2.0")
    (println (print-separator 80 "="))
    
    ;; RESUMO GERAL
    (println (print-section-header "📊 RESUMO GERAL"))
    (let [stats (get-in analysis [:general :stats])]
      (println (format "  Total de Transações:      %d" (get-in analysis [:general :total-transactions])))
      (println (format "  Valor Total:              %s" (format-currency (:total stats))))
      (println (format "  Média por Transação:      %s" (format-currency (:mean stats))))
      (println (format "  Mediana:                  %s" (format-currency (:median stats))))
      (println (format "  Maior Transação:          %s" (format-currency (:max stats))))
      (println (format "  Menor Transação:          %s" (format-currency (:min stats))))
      (println (format "  Desvio Padrão:            %s" (format-currency (:std-dev stats)))))
    
    ;; ANÁLISE MENSAL
    (println (print-section-header "📅 ANÁLISE MENSAL"))
    (doseq [[month data] (:by-month analysis)]
      (let [stats (:stats data)]
        (println (format "\n  %s" month))
        (println (format "    Total:           %s (%d transações)" 
                        (format-currency (:total stats)) 
                        (:transactions-count data)))
        (println (format "    Média:           %s" (format-currency (:mean stats))))
        (println (format "    Maior gasto:     %s" (format-currency (:max stats))))
        (println "    Top 3 categorias:")
        (doseq [[cat amount] (take 3 (sort-by val > (:by-category data)))]
          (println (format "      %-20s %s" cat (format-currency amount))))))
    
    ;; ANÁLISE POR CATEGORIA
    (println (print-section-header "🏷️  ANÁLISE POR CATEGORIA"))
    (let [sorted-cats (sort-by #(get-in % [1 :stats :total]) > (:by-category analysis))]
      (doseq [[category data] sorted-cats]
        (let [stats (:stats data)]
          (println (format "\n  %s" category))
          (println (format "    Total:           %s (%s do total)" 
                          (format-currency (:total stats))
                          (format-percentage (:percentage stats))))
          (println (format "    Transações:      %d (média: %s)" 
                          (:transactions-count data)
                          (format-currency (:mean stats))))
          (println "    Top 3 estabelecimentos:")
          (doseq [[merchant count] (take 3 (sort-by val > (:merchants data)))]
            (println (format "      %-40s (%dx)" 
                            (subs merchant 0 (min 40 (count merchant))) 
                            count))))))
    
    ;; TOP 20 GASTOS
    (println (print-section-header "💰 TOP 20 MAIORES GASTOS"))
    (doseq [[idx tx] (map-indexed vector (:top-expenses analysis))]
      (println (format "  %2d. %s | %s | %s | %s (%.1f%% do total)"
                      (inc idx)
                      (format-date-br (:date tx))
                      (format-currency (:amount tx))
                      (:categoria tx)
                      (subs (:description tx) 0 (min 35 (count (:description tx))))
                      (:percentage-of-total tx))))
    
    ;; TRANSAÇÕES RECORRENTES
    (println (print-section-header "🔄 TRANSAÇÕES RECORRENTES"))
    (if (empty? (:recurring analysis))
      (println "  Nenhuma transação recorrente detectada.")
      (doseq [rec (take 10 (:recurring analysis))]
        (println (format "\n  %s" (:description rec)))
        (println (format "    Valor:           %s" (format-currency (:amount rec))))
        (println (format "    Ocorrências:     %d vezes" (:occurrences rec)))
        (println (format "    Meses:           %s" (str/join ", " (sort (:months rec)))))))
    
    ;; ESTABELECIMENTOS
    (println (print-section-header "🏪 TOP ESTABELECIMENTOS"))
    (println "\n  Por Volume Total:")
    (doseq [[idx merchant] (map-indexed vector (take 10 (get-in analysis [:merchants :by-total])))]
      (println (format "    %2d. %-40s %s (%d transações, %s)"
                      (inc idx)
                      (subs (:merchant merchant) 0 (min 40 (count (:merchant merchant))))
                      (format-currency (:total merchant))
                      (:count merchant)
                      (:category merchant))))
    
    (println "\n  Por Frequência:")
    (doseq [[idx merchant] (map-indexed vector (take 10 (get-in analysis [:merchants :by-frequency])))]
      (println (format "    %2d. %-40s %dx (%s total)"
                      (inc idx)
                      (subs (:merchant merchant) 0 (min 40 (count (:merchant merchant))))
                      (:count merchant)
                      (format-currency (:total merchant)))))
    
    ;; TENDÊNCIAS
    (println (print-section-header "📈 TENDÊNCIAS"))
    (let [trends (:trends analysis)
          trend-data (:trend trends)]
      (println (format "  Tendência geral:          %s" 
                      (case (:direction trend-data)
                        :increasing "📈 CRESCENTE"
                        :decreasing "📉 DECRESCENTE"
                        "➡️  ESTÁVEL")))
      (when (not= (:direction trend-data) :unknown)
        (println (format "  Variação:                 %s" 
                        (format-percentage (:change-percentage trend-data)))))
      (println "\n  Gastos Mensais:")
      (doseq [[month total] (:monthly-totals trends)]
        (println (format "    %s: %s" month (format-currency total)))))
    
    ;; DUPLICATAS
    (println (print-section-header "⚠️  POSSÍVEIS DUPLICATAS"))
    (if (empty? (:duplicates analysis))
      (println "  ✓ Nenhuma duplicata detectada")
      (do
        (println (format "  Encontrados %d grupos de possíveis duplicatas:\n" 
                        (count (:duplicates analysis))))
        (doseq [[idx group] (map-indexed vector (take 5 (:duplicates analysis)))]
          (println (format "  Grupo %d:" (inc idx)))
          (doseq [tx group]
            (println (format "    - %s | %s | %s"
                            (format-date-br (:date tx))
                            (format-currency (:amount tx))
                            (:description tx)))))))
    
    (println "\n" (print-separator 80 "="))
    (println "  Relatório gerado por Nubank Analyzer v2.0")
    (println (print-separator 80 "=") "\n")))

;; ============================================================================
;; Relatório JSON
;; ============================================================================

(defn prepare-for-json
  "Prepara dados para serialização JSON"
  [data]
  (clojure.walk/postwalk
    (fn [x]
      (cond
        (keyword? x) (name x)
        (set? x) (vec x)
        :else x))
    data))

(defn generate-json-report
  "Gera relatório em formato JSON"
  [analysis output-stream]
  (let [json-data (prepare-for-json analysis)]
    (json/write json-data output-stream :indent true)))

;; ============================================================================
;; Relatório EDN
;; ============================================================================

(defn generate-edn-report
  "Gera relatório em formato EDN"
  [analysis output-stream]
  (binding [*out* output-stream]
    (pprint analysis)))

;; ============================================================================
;; Relatório CSV
;; ============================================================================

(defn generate-csv-report
  "Gera relatório de transações em formato CSV"
  [analysis output-stream]
  (let [transactions (:transactions analysis)
        headers ["Data" "Descrição" "Valor" "Categoria" "Mês"]
        rows (map (fn [tx]
                   [(format-date-br (:date tx))
                    (:description tx)
                    (format "%.2f" (:amount tx))
                    (:categoria tx)
                    (:month tx)])
                 transactions)]
    (csv/write-csv output-stream (cons headers rows))))

;; ============================================================================
;; Relatório HTML
;; ============================================================================

(defn generate-html-report
  "Gera relatório em formato HTML com estilos"
  [analysis output-stream]
  (binding [*out* output-stream]
    (println "<!DOCTYPE html>")
    (println "<html lang='pt-BR'>")
    (println "<head>")
    (println "  <meta charset='UTF-8'>")
    (println "  <meta name='viewport' content='width=device-width, initial-scale=1.0'>")
    (println "  <title>Análise Nubank - Relatório</title>")
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
    (println "    <h1>💳 Análise de Transações Nubank</h1>")
    
    ;; Stats cards
    (let [stats (get-in analysis [:general :stats])]
      (println "    <div class='stat-grid'>")
      (println (format "      <div class='stat-card'><div class='stat-label'>Total de Transações</div><div class='stat-value'>%d</div></div>" 
                      (get-in analysis [:general :total-transactions])))
      (println (format "      <div class='stat-card'><div class='stat-label'>Valor Total</div><div class='stat-value'>%s</div></div>" 
                      (format-currency (:total stats))))
      (println (format "      <div class='stat-card'><div class='stat-label'>Média por Transação</div><div class='stat-value'>%s</div></div>" 
                      (format-currency (:mean stats))))
      (println (format "      <div class='stat-card'><div class='stat-label'>Maior Transação</div><div class='stat-value'>%s</div></div>" 
                      (format-currency (:max stats))))
      (println "    </div>"))
    
    ;; Por Categoria
    (println "    <h2>🏷️ Gastos por Categoria</h2>")
    (println "    <table>")
    (println "      <thead><tr><th>Categoria</th><th>Total</th><th>% do Total</th><th>Transações</th></tr></thead>")
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
    (println "    <h2>💰 Top 20 Maiores Gastos</h2>")
    (println "    <table>")
    (println "      <thead><tr><th>#</th><th>Data</th><th>Valor</th><th>Categoria</th><th>Descrição</th></tr></thead>")
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

;; ============================================================================
;; Exportação Multi-formato
;; ============================================================================

(defn export-report
  "Exporta relatório no formato especificado"
  [analysis format output-path]
  (log/info "Gerando relatório %s: %s" (name format) output-path)
  
  (try
    (io/make-parents output-path)
    (with-open [writer (io/writer output-path)]
      (case format
        :txt (generate-txt-report analysis writer)
        :json (generate-json-report analysis writer)
        :edn (generate-edn-report analysis writer)
        :csv (generate-csv-report analysis writer)
        :html (generate-html-report analysis writer)
        (throw (ex-info (str "Formato não suportado: " format) {:format format}))))
    
    (log/info "Relatório gerado com sucesso: %s" output-path)
    {:success true :path output-path :format format}
    
    (catch Exception e
      (log/error "Erro ao gerar relatório: %s" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn export-all-formats
  "Exporta relatório em todos os formatos"
  [analysis base-path]
  (log/info "Exportando relatório em todos os formatos...")
  
  (let [formats [:txt :json :edn :csv :html]
        results (doall
                  (for [fmt formats]
                    (let [ext (name fmt)
                          path (str/replace base-path #"\.\w+$" (str "." ext))]
                      (export-report analysis fmt path))))]
    
    {:results results
     :success (every? :success results)}))

(comment
  ;; Exportar em formato específico
  (export-report analysis :txt "relatorio.txt")
  (export-report analysis :json "relatorio.json")
  (export-report analysis :html "relatorio.html")
  
  ;; Exportar em todos os formatos
  (export-all-formats analysis "relatorios/nubank")
  )
