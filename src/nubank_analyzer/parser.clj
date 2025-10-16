(ns nubank-analyzer.parser
  "Módulo de parsing robusto para CSV e transações
   Suporta múltiplos formatos de data e moeda"
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [nubank-analyzer.logger :as log]
            [nubank-analyzer.validation :as v])
  (:import [java.time LocalDate]
           [java.time.format DateTimeFormatter DateTimeParseException]))

;; ============================================================================
;; Parsing de Valores Monetários
;; ============================================================================

(defn parse-amount
  "Converte string de valor monetário para número double.
   Suporta formatos brasileiros e internacionais:
   - R$ -1.234,56
   - -1234.56
   - (1,234.56) para valores negativos
   - 1.234,56"
  [s]
  (when (and s (not (str/blank? s)))
    (try
      (let [;; Detectar se é negativo por parênteses
            is-negative-parens? (and (str/starts-with? s "(") 
                                     (str/ends-with? s ")"))
            ;; Limpar string
            clean (-> s
                      (str/replace #"[R\$\s\(\)]" "")     ; Remove R$, espaços e parênteses
                      (str/trim))
            ;; Detectar formato (BR usa vírgula decimal, US/INT usa ponto)
            has-comma-decimal? (re-find #",\d{2}$" clean)
            ;; Processar baseado no formato
            normalized (if has-comma-decimal?
                        ;; Formato brasileiro: 1.234,56
                        (-> clean
                            (str/replace #"\." "")       ; Remove separador milhar
                            (str/replace #"," "."))      ; Vírgula vira ponto
                        ;; Formato internacional: 1,234.56
                        (str/replace #"," ""))           ; Remove separador milhar
            value (Double/parseDouble normalized)]
        
        ;; Aplicar sinal negativo se necessário
        (if (or is-negative-parens? 
                (and (not is-negative-parens?) (< value 0)))
          (- (Math/abs value))
          (Math/abs value)))
      
      (catch Exception e
        (log/warn "Erro ao parsear valor '%s': %s" s (.getMessage e))
        nil))))

;; ============================================================================
;; Parsing de Datas
;; ============================================================================

(defn try-parse-date
  "Tenta parsear data com um formato específico"
  [date-str format-str]
  (try
    (let [formatter (DateTimeFormatter/ofPattern format-str)
          date (LocalDate/parse date-str formatter)]
      (.format date (DateTimeFormatter/ISO_LOCAL_DATE)))
    (catch DateTimeParseException _ nil)))

(defn parse-date
  "Converte string de data para formato ISO (yyyy-MM-dd).
   Tenta múltiplos formatos automaticamente"
  [date-str formats]
  (when-not (str/blank? date-str)
    (let [trimmed (str/trim date-str)
          ;; Tentar cada formato configurado
          parsed (some #(try-parse-date trimmed %) formats)]
      (or parsed
          (do
            (log/debug "Data '%s' não pôde ser parseada com formatos conhecidos" trimmed)
            trimmed)))))

;; ============================================================================
;; Parsing de Headers CSV
;; ============================================================================

(defn normalize-header
  "Normaliza nome de coluna do CSV"
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
  "Mapeia header do CSV para campo interno"
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
;; Parsing de Linhas CSV
;; ============================================================================

(defn parse-csv-row
  "Parseia uma linha do CSV usando mapeamento de campos"
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
  "Lê arquivo CSV e retorna [headers rows]"
  [file-path encoding]
  (log/info "Lendo CSV: %s (encoding: %s)" file-path encoding)
  
  (try
    (with-open [reader (io/reader file-path :encoding encoding)]
      (let [data (csv/read-csv reader)
            headers (first data)
            rows (rest data)]
        
        (log/debug "Headers encontrados: %s" (str/join ", " headers))
        (log/debug "Total de linhas: %d" (count rows))
        
        {:headers headers
         :rows rows
         :line-count (count rows)}))
    
    (catch java.io.FileNotFoundException e
      (log/error "Arquivo não encontrado: %s" file-path)
      (throw e))
    
    (catch Exception e
      (log/error "Erro ao ler CSV: %s" (.getMessage e))
      (throw e))))

;; ============================================================================
;; Pipeline Completo de Parsing
;; ============================================================================

(defn parse-transactions
  "Pipeline completo: lê CSV e parseia transações
   Retorna {:transactions [...] :stats {...} :errors [...]}"
  [file-path config]
  (log/with-timing (str "Parsing de " file-path)
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
          
          ;; Criar mapeamento de campos
          field-mapping (build-field-mapping headers)
          _ (log/debug "Mapeamento de campos: %s" field-mapping)
          
          ;; Parsear linhas
          parsed-transactions (keep #(parse-csv-row % field-mapping date-formats) rows)
          
          ;; Filtrar transações válidas
          valid-transactions (filter #(and (:date %) (:amount %)) parsed-transactions)
          
          ;; Estatísticas
          total-rows (count rows)
          parsed-count (count parsed-transactions)
          valid-count (count valid-transactions)
          failed-count (- total-rows valid-count)]
      
      (log/info "Parsing concluído: %d/%d transações válidas (%.1f%%)"
                valid-count total-rows 
                (* 100.0 (/ valid-count (max total-rows 1))))
      
      (when (> failed-count 0)
        (log/warn "%d linhas falharam no parsing" failed-count))
      
      {:transactions valid-transactions
       :stats {:total-rows total-rows
               :parsed-count parsed-count
               :valid-count valid-count
               :failed-count failed-count
               :success-rate (* 100.0 (/ valid-count (max total-rows 1)))}
       :field-mapping field-mapping})))

;; ============================================================================
;; Parsing de Múltiplos Arquivos
;; ============================================================================

(defn parse-multiple-files
  "Parseia múltiplos arquivos CSV e combina resultados"
  [file-paths config]
  (log/info "Parseando %d arquivos..." (count file-paths))
  
  (let [results (map #(parse-transactions % config) file-paths)
        all-transactions (vec (mapcat :transactions results))
        combined-stats {:total-files (count file-paths)
                       :total-transactions (count all-transactions)
                       :files (map (fn [path result]
                                    {:path path
                                     :stats (:stats result)})
                                  file-paths results)}]
    
    (log/info "Total de %d transações de %d arquivos" 
              (count all-transactions) (count file-paths))
    
    {:transactions all-transactions
     :stats combined-stats}))

;; ============================================================================
;; Utilitários de Export
;; ============================================================================

(defn transactions-to-csv
  "Exporta transações para formato CSV"
  [transactions output-path]
  (log/info "Exportando %d transações para CSV: %s" (count transactions) output-path)
  
  (try
    (with-open [writer (io/writer output-path)]
      (csv/write-csv writer
                    (cons ["Data" "Descrição" "Valor" "Categoria" "Mês"]
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
      (log/error "Erro ao exportar CSV: %s" (.getMessage e))
      {:success false :error (.getMessage e)})))

(comment
  ;; Testar parsing de valores
  (parse-amount "R$ -1.234,56")  ; => -1234.56
  (parse-amount "(1,234.56)")    ; => -1234.56
  (parse-amount "1234.56")       ; => 1234.56
  
  ;; Testar parsing de datas
  (parse-date "15/10/2025" ["dd/MM/yyyy" "yyyy-MM-dd"])  ; => "2025-10-15"
  
  ;; Parsear arquivo
  (def config {:csv {:encoding "UTF-8"}
               :parser {:date-formats ["dd/MM/yyyy" "yyyy-MM-dd"]}})
  (def result (parse-transactions "exemplo-transacoes.csv" config))
  )
