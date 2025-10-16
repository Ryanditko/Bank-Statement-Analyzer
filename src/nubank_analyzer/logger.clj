(ns nubank-analyzer.logger
  "Sistema de logging profissional com níveis e formatação"
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

;; ============================================================================
;; Estado do Logger
;; ============================================================================

(def ^:private log-levels
  {:debug 0
   :info 1
   :warn 2
   :error 3})

(def ^:private current-level (atom :info))
(def ^:private log-file (atom nil))
(def ^:private log-to-console (atom true))

;; ============================================================================
;; Formatação
;; ============================================================================

(defn- timestamp []
  (.format (LocalDateTime/now) 
           (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")))

(defn- level-icon [level]
  (case level
    :debug "🔍"
    :info "ℹ️ "
    :warn "⚠️ "
    :error "❌"
    "  "))

(defn- colorize [level text]
  ;; ANSI color codes (funciona em terminais compatíveis)
  (let [colors {:debug "\u001B[36m"  ; Cyan
                :info "\u001B[32m"   ; Green
                :warn "\u001B[33m"   ; Yellow
                :error "\u001B[31m"  ; Red
                :reset "\u001B[0m"}]
    (str (colors level) text (colors :reset))))

(defn- format-message [level message]
  (format "[%s] %s %s: %s"
          (timestamp)
          (level-icon level)
          (str/upper-case (name level))
          message))

;; ============================================================================
;; Configuração
;; ============================================================================

(defn configure!
  "Configura o sistema de logging
   Options:
   - :level - Nível mínimo (:debug :info :warn :error)
   - :file - Caminho para arquivo de log
   - :console - true/false para log no console"
  [opts]
  (when-let [level (:level opts)]
    (reset! current-level level))
  (when-let [file (:file opts)]
    (io/make-parents file)
    (reset! log-file file))
  (when (contains? opts :console)
    (reset! log-to-console (:console opts))))

;; ============================================================================
;; Funções de Log
;; ============================================================================

(defn- should-log? [level]
  (>= (get log-levels level 0)
      (get log-levels @current-level 0)))

(defn- write-log [level message]
  (when (should-log? level)
    (let [formatted (format-message level message)]
      ;; Console
      (when @log-to-console
        (if (#{:warn :error} level)
          (binding [*out* *err*]
            (println (colorize level formatted)))
          (println (colorize level formatted))))
      
      ;; Arquivo
      (when @log-file
        (try
          (spit @log-file (str formatted "\n") :append true)
          (catch Exception e
            (binding [*out* *err*]
              (println "Erro ao escrever no log:" (.getMessage e)))))))))

(defn debug [message & args]
  (write-log :debug (apply format message args)))

(defn info [message & args]
  (write-log :info (apply format message args)))

(defn warn [message & args]
  (write-log :warn (apply format message args)))

(defn error [message & args]
  (write-log :error (apply format message args)))

(defn exception
  "Log de exceção com stack trace"
  [message throwable]
  (error "%s: %s" message (.getMessage throwable))
  (when (should-log? :debug)
    (error "Stack trace:")
    (doseq [line (.getStackTrace throwable)]
      (error "  at %s" line))))

;; ============================================================================
;; Macros para Performance
;; ============================================================================

(defmacro with-timing
  "Executa código e loga o tempo de execução"
  [description & body]
  `(let [start# (System/nanoTime)
         result# (do ~@body)
         elapsed# (/ (- (System/nanoTime) start#) 1000000.0)]
     (info "%s concluído em %.2fms" ~description elapsed#)
     result#))

(defmacro log-errors
  "Executa código e loga exceções automaticamente"
  [& body]
  `(try
     ~@body
     (catch Exception e#
       (exception "Erro durante execução" e#)
       (throw e#))))

;; ============================================================================
;; Utilitários
;; ============================================================================

(defn clear-log-file!
  "Limpa o arquivo de log"
  []
  (when @log-file
    (try
      (spit @log-file "")
      (info "Arquivo de log limpo")
      (catch Exception e
        (error "Erro ao limpar log: %s" (.getMessage e))))))

(defn get-log-stats
  "Retorna estatísticas do log atual"
  []
  {:level @current-level
   :file @log-file
   :console @log-to-console
   :file-exists (and @log-file (.exists (io/file @log-file)))
   :file-size (when (and @log-file (.exists (io/file @log-file)))
                (.length (io/file @log-file)))})

(comment
  ;; Configurar
  (configure! {:level :debug
               :file "logs/app.log"
               :console true})
  
  ;; Usar
  (debug "Mensagem de debug: %d" 42)
  (info "Processando %s..." "arquivo.csv")
  (warn "Valor suspeito: %f" 99999.99)
  (error "Falha ao processar")
  
  ;; Com timing
  (with-timing "Processamento CSV"
    (Thread/sleep 1000)
    "resultado")
  
  ;; Stats
  (get-log-stats)
  )
