(ns nubank-analyzer.core
  "Módulo principal - orquestra todos os componentes"
  (:gen-class)
  (:require [nubank-analyzer.config :as config]
            [nubank-analyzer.logger :as log]
            [nubank-analyzer.parser :as parser]
            [nubank-analyzer.analyzer :as analyzer]
            [nubank-analyzer.validation :as validation]
            [nubank-analyzer.reports :as reports]
            [nubank-analyzer.cli :as cli]
            [clojure.string :as str]))

;; ============================================================================
;; Pipeline Principal
;; ============================================================================

(defn initialize-system
  "Inicializa sistema com configurações"
  [cli-options]
  (let [;; Carregar configuração
        config-file (:config cli-options)
        config (if config-file
                (do
                  (log/info "Carregando configuração: %s" config-file)
                  (config/load-config config-file))
                (do
                  (log/debug "Usando configuração padrão")
                  config/default-config))
        
        ;; Configurar logging
        log-level (cond
                   (:debug cli-options) :debug
                   (:verbose cli-options) :debug
                   :else :info)]
    
    (log/configure! {:level log-level
                    :console true
                    :file (get-in config [:logging :file])})
    
    (log/info "Nubank Analyzer v%s iniciado" (config/get-app-version config))
    config))

(defn process-transactions
  "Pipeline completo de processamento"
  [input-file cli-options config]
  (try
    ;; 1. PARSING
    (log/info "═══════════════════════════════════════════════════════════════")
    (log/info "ETAPA 1: LEITURA E PARSING DO CSV")
    (log/info "═══════════════════════════════════════════════════════════════")
    
    (let [parse-result (parser/parse-transactions input-file config)
          transactions (:transactions parse-result)
          parse-stats (:stats parse-result)]
      
      (when (empty? transactions)
        (throw (ex-info "Nenhuma transação válida encontrada no arquivo" 
                       {:file input-file})))
      
      ;; 2. VALIDAÇÃO
      (log/info "═══════════════════════════════════════════════════════════════")
      (log/info "ETAPA 2: VALIDAÇÃO DE DADOS")
      (log/info "═══════════════════════════════════════════════════════════════")
      
      (let [validation-report (validation/generate-validation-report transactions config)]
        
        (when (:validate-only cli-options)
          (println (cli/format-validation-message (:validation validation-report)))
          (when (:verbose cli-options)
            (doseq [invalid (get-in validation-report [:validation :invalid-transactions])]
              (println (format "Linha %d: %s" 
                             (:index invalid)
                             (str/join ", " (:errors invalid))))))
          (System/exit 0))
        
        ;; 3. FILTRAGEM
        (let [filters (cli/build-filters cli-options)
              filtered-txs (if (seq filters)
                            (do
                              (log/info "Aplicando filtros: %s" filters)
                              (analyzer/filter-transactions transactions filters))
                            transactions)]
          
          (when (empty? filtered-txs)
            (log/warn "Nenhuma transação encontrada após aplicar filtros")
            (println "\n⚠️  Nenhuma transação corresponde aos filtros especificados.\n")
            (System/exit 0))
          
          (log/info "%d transações após filtragem" (count filtered-txs))
          
          ;; 4. ANÁLISE
          (log/info "═══════════════════════════════════════════════════════════════")
          (log/info "ETAPA 3: ANÁLISE DE TRANSAÇÕES")
          (log/info "═══════════════════════════════════════════════════════════════")
          
          (let [analysis (analyzer/perform-complete-analysis filtered-txs config)]
            
            ;; 5. GERAÇÃO DE RELATÓRIO
            (log/info "═══════════════════════════════════════════════════════════════")
            (log/info "ETAPA 4: GERAÇÃO DE RELATÓRIO")
            (log/info "═══════════════════════════════════════════════════════════════")
            
            {:analysis analysis
             :stats {:parse parse-stats
                    :validation validation-report
                    :filtered-count (count filtered-txs)}}))))
    
    (catch Exception e
      (log/exception "Erro durante processamento" e)
      (throw e))))

(defn export-results
  "Exporta resultados da análise"
  [analysis cli-options input-file]
  (let [format-opt (keyword (:format cli-options))
        output-path (cli/determine-output-path cli-options input-file)]
    
    (if (= format-opt :all)
      ;; Exportar todos os formatos
      (do
        (log/info "Exportando relatório em todos os formatos...")
        (let [result (reports/export-all-formats analysis output-path)]
          (if (:success result)
            (do
              (println "\n✓ Relatórios gerados com sucesso:")
              (doseq [r (:results result)]
                (println (format "  - %s" (:path r)))))
            (println "\n❌ Erro ao gerar alguns relatórios"))))
      
      ;; Exportar formato específico
      (let [format (or format-opt 
                      (cli/determine-output-format cli-options))]
        (if output-path
          ;; Salvar em arquivo
          (let [result (reports/export-report analysis format output-path)]
            (when (:success result)
              output-path))
          
          ;; Imprimir no console
          (do
            (reports/generate-txt-report analysis *out*)
            nil))))))

;; ============================================================================
;; Comandos Especiais
;; ============================================================================

(defn export-default-config
  "Exporta configuração padrão para arquivo"
  [output-path]
  (log/info "Exportando configuração padrão para: %s" output-path)
  
  (try
    (config/generate-default-config-file output-path)
    (println (format "\n✓ Configuração padrão exportada para: %s\n" output-path))
    (println "Você pode editar este arquivo e usar com a opção -c/--config\n")
    0
    
    (catch Exception e
      (log/error "Erro ao exportar configuração: %s" (.getMessage e))
      (println (format "\n❌ Erro ao exportar configuração: %s\n" (.getMessage e)))
      1)))

;; ============================================================================
;; Função Principal
;; ============================================================================

(defn -main
  "Ponto de entrada da aplicação"
  [& args]
  (let [start-time (System/currentTimeMillis)]
    
    ;; Parse argumentos CLI
    (let [{:keys [options exit-message ok? action export-path]} (cli/validate-args args)]
      
      ;; Tratar mensagem de saída (ajuda ou erro)
      (when exit-message
        (println exit-message)
        (System/exit (if ok? 0 1)))
      
      ;; Tratar ação especial (export-config)
      (when (= action :export-config)
        (System/exit (export-default-config export-path)))
      
      ;; Processamento normal
      (try
        ;; Inicializar sistema
        (let [config (initialize-system options)
              input-file (:input options)]
          
          ;; Verificar se arquivo existe
          (when-not (.exists (clojure.java.io/file input-file))
            (log/error "Arquivo não encontrado: %s" input-file)
            (println (format "\n❌ Arquivo não encontrado: %s\n" input-file))
            (System/exit 1))
          
          (log/info "Processando arquivo: %s" input-file)
          
          ;; Processar transações
          (let [result (process-transactions input-file options config)
                analysis (:analysis result)]
            
            ;; Exportar resultados
            (let [output-path (export-results analysis options input-file)
                  elapsed (- (System/currentTimeMillis) start-time)]
              
              ;; Mensagem de sucesso
              (when output-path
                (println (cli/format-success-message output-path elapsed)))
              
              (log/info "Processamento concluído em %.2fs" (/ elapsed 1000.0))
              (System/exit 0))))
        
        (catch clojure.lang.ExceptionInfo e
          (log/error "Erro: %s" (.getMessage e))
          (println (format "\n❌ Erro: %s\n" (.getMessage e)))
          (when (:verbose options)
            (.printStackTrace e))
          (System/exit 1))
        
        (catch Exception e
          (log/exception "Erro inesperado" e)
          (println (format "\n❌ Erro inesperado: %s\n" (.getMessage e)))
          (when (or (:verbose options) (:debug options))
            (.printStackTrace e))
          (System/exit 1))))))

;; ============================================================================
;; Funções Auxiliares para REPL
;; ============================================================================

(defn analyze-file
  "Função auxiliar para usar no REPL
   Exemplo: (analyze-file \"transacoes.csv\")"
  [file-path]
  (let [config config/default-config]
    (log/configure! {:level :info :console true})
    (-> (parser/parse-transactions file-path config)
        :transactions
        (analyzer/perform-complete-analysis config))))

(defn quick-report
  "Gera relatório rápido no console
   Exemplo: (quick-report \"transacoes.csv\")"
  [file-path]
  (let [analysis (analyze-file file-path)]
    (reports/generate-txt-report analysis *out*)
    :done))

(comment
  ;; Usar no REPL
  
  ;; Análise simples
  (def analysis (analyze-file "exemplo-transacoes.csv"))
  
  ;; Ver relatório
  (quick-report "exemplo-transacoes.csv")
  
  ;; Exportar
  (reports/export-report analysis :html "relatorio.html")
  
  ;; Estatísticas
  (get-in analysis [:general :stats])
  
  ;; Por categoria
  (:by-category analysis)
  )
