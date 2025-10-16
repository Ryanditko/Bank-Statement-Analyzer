(ns nubank-analyzer.cli
  "Interface de linha de comando avançada
   Gerencia argumentos, opções e comandos"
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]))

;; ============================================================================
;; Definição de Opções CLI
;; ============================================================================

(def cli-options
  [["-i" "--input FILE" "Arquivo CSV de transações (obrigatório)"
    :required "FILE"]
   
   ["-o" "--output PATH" "Caminho para salvar relatório"
    :default nil]
   
   ["-f" "--format FORMAT" "Formato do relatório: txt, json, edn, csv, html, all"
    :default "txt"
    :parse-fn str/lower-case
    :validate [#(contains? #{"txt" "json" "edn" "csv" "html" "all"} %)
               "Formato deve ser: txt, json, edn, csv, html ou all"]]
   
   ["-c" "--config FILE" "Arquivo de configuração customizado (EDN)"
    :default nil]
   
   [nil "--category CATEGORY" "Filtrar por categoria específica"
    :default nil]
   
   [nil "--min-amount AMOUNT" "Valor mínimo para filtrar transações"
    :parse-fn #(Double/parseDouble %)
    :default nil]
   
   [nil "--max-amount AMOUNT" "Valor máximo para filtrar transações"
    :parse-fn #(Double/parseDouble %)
    :default nil]
   
   [nil "--month MONTH" "Filtrar por mês (formato: MM/YYYY)"
    :default nil]
   
   ["-v" "--verbose" "Modo verbose (log detalhado)"
    :default false]
   
   [nil "--debug" "Modo debug (log completo)"
    :default false]
   
   [nil "--no-duplicates" "Não detectar duplicatas"
    :default false]
   
   [nil "--validate-only" "Apenas validar CSV sem gerar relatório"
    :default false]
   
   [nil "--export-config PATH" "Exportar configuração padrão para arquivo"
    :default nil]
   
   ["-h" "--help" "Exibir esta mensagem de ajuda"
    :default false]])

;; ============================================================================
;; Mensagens de Ajuda
;; ============================================================================

(defn usage-banner []
  (str/join "\n"
    ["╔════════════════════════════════════════════════════════════════════╗"
     "║        NUBANK ANALYZER v2.0 - Professional Edition                 ║"
     "║        Análise Avançada de Transações Bancárias                    ║"
     "╚════════════════════════════════════════════════════════════════════╝"
     ""]))

(defn usage [options-summary]
  (str/join "\n"
    [(usage-banner)
     "MODO DE USO:"
     "  clojure -M -m nubank-analyzer.core [opções]"
     ""
     "OPÇÕES:"
     options-summary
     ""
     "EXEMPLOS:"
     ""
     "  Análise básica:"
     "    clojure -M -m nubank-analyzer.core -i transacoes.csv"
     ""
     "  Salvar relatório em formato específico:"
     "    clojure -M -m nubank-analyzer.core -i transacoes.csv -o relatorio.html -f html"
     ""
     "  Exportar todos os formatos:"
     "    clojure -M -m nubank-analyzer.core -i transacoes.csv -o relatorio -f all"
     ""
     "  Filtrar por categoria:"
     "    clojure -M -m nubank-analyzer.core -i transacoes.csv --category Alimentação"
     ""
     "  Filtrar por valor:"
     "    clojure -M -m nubank-analyzer.core -i transacoes.csv --min-amount 100"
     ""
     "  Usar configuração customizada:"
     "    clojure -M -m nubank-analyzer.core -i transacoes.csv -c config.edn"
     ""
     "  Exportar configuração padrão:"
     "    clojure -M -m nubank-analyzer.core --export-config my-config.edn"
     ""
     "  Modo verbose:"
     "    clojure -M -m nubank-analyzer.core -i transacoes.csv --verbose"
     ""
     "  Apenas validar CSV:"
     "    clojure -M -m nubank-analyzer.core -i transacoes.csv --validate-only"
     ""
     "FORMATOS SUPORTADOS:"
     "  txt  - Relatório texto formatado (padrão)"
     "  json - Formato JSON para integração"
     "  edn  - Formato EDN Clojure"
     "  csv  - Transações em CSV"
     "  html - Relatório HTML interativo"
     "  all  - Exporta em todos os formatos"
     ""
     "MAIS INFORMAÇÕES:"
     "  https://github.com/seu-usuario/nubank-analyzer"
     ""]))

(defn error-msg [errors]
  (str "❌ ERROS NA LINHA DE COMANDO:\n\n"
       (str/join "\n" (map #(str "  • " %) errors))
       "\n\nUse -h ou --help para ver as opções disponíveis.\n"))

;; ============================================================================
;; Validação de Argumentos
;; ============================================================================

(defn validate-args
  "Valida argumentos da linha de comando"
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      ;; Ajuda solicitada
      (:help options)
      {:exit-message (usage summary) :ok? true}
      
      ;; Exportar config
      (:export-config options)
      {:action :export-config
       :export-path (:export-config options)
       :ok? true}
      
      ;; Erros de parsing
      errors
      {:exit-message (error-msg errors)}
      
      ;; Input obrigatório (exceto para export-config)
      (and (not (:export-config options))
           (not (:input options)))
      {:exit-message (error-msg ["Arquivo de entrada (-i/--input) é obrigatório"])}
      
      ;; Tudo ok
      :else
      {:options options :ok? true})))

;; ============================================================================
;; Construção de Filtros
;; ============================================================================

(defn build-filters
  "Constrói mapa de filtros baseado nas opções CLI"
  [options]
  (cond-> {}
    (:category options) (assoc :category (:category options))
    (:min-amount options) (assoc :min-amount (:min-amount options))
    (:max-amount options) (assoc :max-amount (:max-amount options))
    (:month options) (assoc :month (:month options))))

;; ============================================================================
;; Determinação de Formato de Saída
;; ============================================================================

(defn determine-output-format
  "Determina formato de saída baseado nas opções"
  [options]
  (let [format-str (:format options)
        output-path (:output options)]
    
    (cond
      ;; Formato explícito
      (not= format-str "txt")
      (keyword format-str)
      
      ;; Inferir da extensão do arquivo
      (and output-path (re-find #"\.(json|edn|csv|html)$" output-path))
      (keyword (second (re-find #"\.(\w+)$" output-path)))
      
      ;; Padrão
      :else
      :txt)))

(defn determine-output-path
  "Determina caminho de saída baseado nas opções"
  [options input-path]
  (or (:output options)
      (let [base-name (str/replace input-path #"\.\w+$" "")
            format (determine-output-format options)
            ext (name format)]
        (str base-name "-relatorio." ext))))

;; ============================================================================
;; Formatação de Mensagens
;; ============================================================================

(defn format-success-message
  "Formata mensagem de sucesso"
  [output-path elapsed-ms]
  (str "\n"
       "╔════════════════════════════════════════════════════════════════════╗\n"
       "║                        ✓ SUCESSO                                   ║\n"
       "╚════════════════════════════════════════════════════════════════════╝\n"
       "\n"
       (format "  Relatório gerado: %s\n" output-path)
       (format "  Tempo de execução: %.2f segundos\n" (/ elapsed-ms 1000.0))
       "\n"))

(defn format-validation-message
  "Formata mensagem de validação"
  [validation-result]
  (let [valid (:valid-count validation-result)
        invalid (:invalid-count validation-result)
        total (+ valid invalid)
        success-rate (if (pos? total)
                      (* 100 (/ valid total))
                      0)]
    (str "\n"
         "╔════════════════════════════════════════════════════════════════════╗\n"
         "║                    RESULTADO DA VALIDAÇÃO                          ║\n"
         "╚════════════════════════════════════════════════════════════════════╝\n"
         "\n"
         (format "  Total de linhas:       %d\n" total)
         (format "  Linhas válidas:        %d (%.1f%%)\n" valid success-rate)
         (format "  Linhas inválidas:      %d\n" invalid)
         "\n"
         (if (pos? invalid)
           (str "  ⚠️  Transações inválidas detectadas. Use --verbose para mais detalhes.\n\n")
           "  ✓ Todas as transações são válidas!\n\n"))))

(comment
  ;; Testar parsing
  (validate-args ["-i" "test.csv"])
  (validate-args ["-i" "test.csv" "-o" "out.json" "-f" "json"])
  (validate-args ["--help"])
  (validate-args ["-i" "test.csv" "--min-amount" "100" "--category" "Alimentação"])
  )
