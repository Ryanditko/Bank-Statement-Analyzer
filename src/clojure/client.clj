(ns clojure.client
  "Script para análise de transações do Nubank
   - Lê CSV de transações exportado do app Nubank
   - Categoriza automaticamente os gastos
   - Gera resumo mensal e por categoria
   - Detecta possíveis duplicatas
   - Calcula estatísticas de gastos"
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.string :as str]))

;; ============================================================================
;; Funções de Parsing
;; ============================================================================

(defn parse-amount
  "Converte string de valor monetário para número.
   Aceita formatos: 'R$ -1.234,56', '-1234.56', '1,234.56'"
  [s]
  (when (and s (not (str/blank? s)))
    (try
      (let [clean (-> s
                      (str/replace #"R\$\s*" "")           ; Remove R$
                      (str/replace #"\s+" "")              ; Remove espaços
                      (str/replace #"\.(?=\d{3})" "")      ; Remove separador de milhar (ponto)
                      (str/replace #"," "."))]             ; Vírgula decimal vira ponto
        (Double/parseDouble clean))
      (catch Exception _ nil))))

(defn parse-date
  "Converte string de data para formato yyyy-MM-dd"
  [date-str]
  (when-not (str/blank? date-str)
    (let [s (str/trim date-str)]
      (cond
        ;; dd/MM/yyyy -> yyyy-MM-dd
        (re-matches #"\d{2}/\d{2}/\d{4}" s)
        (let [[d m y] (str/split s #"/")] 
          (str y "-" m "-" d))
        
        ;; yyyy-MM-dd (já no formato)
        (re-matches #"\d{4}-\d{2}-\d{2}" s)
        s
        
        :else
        date-str))))

(defn parse-row
  "Mapeia uma linha CSV para mapa de transação.
   Espera colunas: date/data, description/descrição, amount/valor"
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
;; Categorização Automática
;; ============================================================================

(def categorias-nubank
  "Mapa de categorias com palavras-chave para classificação automática"
  {"Alimentação" ["restaurante" "lanchonete" "padaria" "ifood" "uber eats" 
                  "rappi" "mcdonalds" "burger" "pizza" "açai" "acai"
                  "cafe" "café" "bar" "pub"]
   
   "Transporte" ["uber" "99" "taxi" "metrô" "metro" "onibus" "ônibus"
                 "bus" "passagem" "combustivel" "combustível" "gasolina"
                 "posto" "ipiranga" "shell" "estacionamento"]
   
   "Assinaturas" ["spotify" "netflix" "amazon prime" "disney" "hbo"
                  "youtube" "apple music" "deezer" "globoplay"
                  "paramount" "crunchyroll" "prime video"]
   
   "Supermercado" ["carrefour" "pão de açucar" "pao de acucar" "extra"
                   "walmart" "mercado" "supermercado" "atacadão" "atacadao"
                   "zaffari" "dia%" "sam's club"]
   
   "Saúde" ["drogaria" "farmacia" "farmácia" "clinica" "clínica"
            "hospital" "laboratorio" "laboratório" "consulta"
            "drogasil" "pacheco" "ultrafarma"]
   
   "Educação" ["curso" "livro" "livraria" "udemy" "coursera"
               "faculdade" "escola" "material escolar"]
   
   "Lazer" ["cinema" "teatro" "show" "ingresso" "parque"
            "viagem" "hotel" "airbnb" "booking"]
   
   "Compras Online" ["amazon" "mercado livre" "americanas" "magazine luiza"
                     "shopee" "aliexpress" "shein"]
   
   "Serviços" ["internet" "telefone" "celular" "luz" "energia"
               "água" "agua" "condominio" "condomínio" "aluguel"]
   
   "Transferências" ["pix" "transferencia" "transferência" "ted" "doc"]})

(defn categorizar
  "Determina categoria baseado em palavras-chave na descrição"
  [descricao]
  (let [desc-lower (-> (or descricao "") str/lower-case)]
    (or (some (fn [[categoria keywords]]
                (when (some #(str/includes? desc-lower %) keywords)
                  categoria))
              categorias-nubank)
        "Outros")))

;; ============================================================================
;; Análise de Transações
;; ============================================================================

(defn month-key
  "Extrai mês/ano da data (formato: MM/yyyy)"
  [date-str]
  (when date-str
    (if-let [[_ y m] (re-find #"(\d{4})-(\d{2})" date-str)]
      (str m "/" y)
      "Desconhecido")))

(defn read-transactions
  "Lê arquivo CSV e retorna vetor de transações"
  [file-path]
  (with-open [reader (io/reader file-path)]
    (let [all-lines (csv/read-csv reader)
          headers (first all-lines)
          rows (rest all-lines)]
      (->> rows
           (map #(parse-row headers %))
           (filter :amount)  ; Remove linhas inválidas
           (map #(assoc % 
                   :month (month-key (:date %))
                   :categoria (categorizar (:description %))))
           vec))))

(defn analyze-transactions
  "Analisa transações e gera estatísticas completas"
  [transactions]
  (let [;; Totais gerais
        total-gasto (reduce + 0 (map :amount transactions))
        total-count (count transactions)
        
        ;; Por mês
        by-month (group-by :month transactions)
        month-summary (into (sorted-map)
                           (for [[mes txs] by-month]
                             [mes {:total (reduce + 0 (map :amount txs))
                                   :count (count txs)
                                   :media (/ (reduce + 0 (map :amount txs)) (count txs))}]))
        
        ;; Por categoria
        by-category (group-by :categoria transactions)
        category-summary (into (sorted-map)
                              (for [[cat txs] by-category]
                                [cat {:total (reduce + 0 (map :amount txs))
                                      :count (count txs)
                                      :percent (* 100 (/ (reduce + 0 (map :amount txs)) 
                                                        total-gasto))}]))
        
        ;; Detectar duplicatas (mesma data + valor similar)
        dup-key (fn [t] 
                  [(:date t) 
                   (Math/round (* 100 (:amount t)))])
        groups (group-by dup-key transactions)
        duplicates (->> groups 
                       (filter #(> (count (val %)) 1)) 
                       (map val) 
                       vec)
        
        ;; Top 10 maiores gastos
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
;; Relatórios
;; ============================================================================

(defn format-currency
  "Formata número como moeda brasileira"
  [n]
  (format "R$ %.2f" n))

(defn print-separator []
  (println (str/join (repeat 70 "="))))

(defn print-report
  "Imprime relatório completo no console"
  [analysis]
  (println "\n")
  (print-separator)
  (println "          ANÁLISE DE TRANSAÇÕES NUBANK")
  (print-separator)
  
  ;; Resumo Geral
  (println "\n📊 RESUMO GERAL")
  (println "  Total gasto:" (format-currency (get-in analysis [:resumo-geral :total])))
  (println "  Quantidade de transações:" (get-in analysis [:resumo-geral :quantidade]))
  (println "  Média por transação:" (format-currency (get-in analysis [:resumo-geral :media])))
  
  ;; Por Mês
  (println "\n📅 GASTOS POR MÊS")
  (doseq [[mes dados] (:por-mes analysis)]
    (println (format "  %s → %s (%d transações, média: %s)"
                    mes
                    (format-currency (:total dados))
                    (:count dados)
                    (format-currency (:media dados)))))
  
  ;; Por Categoria
  (println "\n🏷️  GASTOS POR CATEGORIA")
  (doseq [[cat dados] (reverse (sort-by #(get-in % [1 :total]) (:por-categoria analysis)))]
    (println (format "  %-20s %s (%d transações, %.1f%%)"
                    cat
                    (format-currency (:total dados))
                    (:count dados)
                    (:percent dados))))
  
  ;; Top 10 Gastos
  (println "\n💰 TOP 10 MAIORES GASTOS")
  (doseq [[idx tx] (map-indexed vector (:top-10-gastos analysis))]
    (println (format "  %2d. %s | %s | %s"
                    (inc idx)
                    (:date tx)
                    (format-currency (:amount tx))
                    (or (:description tx) "Sem descrição"))))
  
  ;; Duplicatas
  (println "\n⚠️  POSSÍVEIS DUPLICATAS")
  (if (empty? (:duplicatas analysis))
    (println "  ✓ Nenhuma duplicata encontrada")
    (doseq [[idx group] (map-indexed vector (:duplicatas analysis))]
      (println (format "  Grupo %d:" (inc idx)))
      (doseq [tx group]
        (println (format "    - %s | %s | %s"
                        (:date tx)
                        (format-currency (:amount tx))
                        (:description tx))))))
  
  (println "\n")
  (print-separator))

(defn save-report
  "Salva relatório em arquivo de texto"
  [analysis output-file]
  (with-open [w (io/writer output-file)]
    (binding [*out* w]
      (print-report analysis)))
  (println (str "✓ Relatório salvo em: " output-file)))

;; ============================================================================
;; Função Principal
;; ============================================================================

(defn -main
  "Função principal do script
   Uso: clojure -M -m clojure.client <arquivo.csv> [saida.txt]"
  [& args]
  (try
    (cond
      (empty? args)
      (do
        (println "❌ Erro: Informe o caminho do arquivo CSV")
        (println "\nUso:")
        (println "  clojure -M -m clojure.client transacoes.csv")
        (println "  clojure -M -m clojure.client transacoes.csv relatorio.txt"))
      
      :else
      (let [csv-file (first args)
            output-file (second args)]
        (println "📂 Lendo arquivo:" csv-file)
        (let [transactions (read-transactions csv-file)
              analysis (analyze-transactions transactions)]
          (println (format "✓ %d transações carregadas" (count transactions)))
          
          (if output-file
            (do
              (save-report analysis output-file)
              (println "\n✓ Análise concluída!"))
            (print-report analysis)))))
    
    (catch java.io.FileNotFoundException e
      (println "❌ Erro: Arquivo não encontrado -" (.getMessage e)))
    (catch Exception e
      (println "❌ Erro ao processar:" (.getMessage e))
      (.printStackTrace e))))

(comment
  ;; Exemplos de uso no REPL:
  
  ;; Ler transações
  (def txs (read-transactions "transacoes.csv"))
  
  ;; Analisar
  (def analise (analyze-transactions txs))
  
  ;; Imprimir relatório
  (print-report analise)
  
  ;; Salvar em arquivo
  (save-report analise "relatorio.txt")
  
  ;; Filtrar por categoria
  (filter #(= (:categoria %) "Alimentação") txs)
  
  ;; Transações acima de R$ 100
  (filter #(> (:amount %) 100) txs)
  )
