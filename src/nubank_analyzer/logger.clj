(ns nubank-analyzer.logger
  "Structured logging system with configurable levels and output formats"
  (:require [clojure.java.io :as io])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))


(def ^:private log-levels
  {:debug 0
   :info 1
   :warn 2
   :error 3})

(def ^:private current-level (atom :info))
(def ^:private log-file (atom nil))
(def ^:private log-to-console (atom true))


(defn- timestamp []
  (.format (LocalDateTime/now)
           (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")))

(defn- level-prefix [level]
  (case level
    :debug "[DEBUG]"
    :info "[INFO] "
    :warn "[WARN] "
    :error "[ERROR]"
    "[LOG]  "))

(defn- colorize [level text]
  (let [colors {:debug "\u001B[36m"  ; Cyan
                :info "\u001B[32m"   ; Green
                :warn "\u001B[33m"   ; Yellow
                :error "\u001B[31m"  ; Red
                :reset "\u001B[0m"}]
    (str (colors level) text (colors :reset))))

(defn- format-message [level message]
  (format "[%s] %s %s"
          (timestamp)
          (level-prefix level)
          message))


(defn configure!
  "Configure the logging system.
   Options:
   - :level - Minimum log level (:debug :info :warn :error)
   - :file - Path to log file
   - :console - Enable/disable console output (true/false)"
  [opts]
  (when-let [level (:level opts)]
    (reset! current-level level))
  (when-let [file (:file opts)]
    (io/make-parents file)
    (reset! log-file file))
  (when (contains? opts :console)
    (reset! log-to-console (:console opts))))


(defn- should-log? [level]
  (>= (get log-levels level 0)
      (get log-levels @current-level 0)))

(defn- write-log [level message]
  (when (should-log? level)
    (let [formatted (format-message level message)]
      ;; Console output
      (when @log-to-console
        (if (#{:warn :error} level)
          (binding [*out* *err*]
            (println (colorize level formatted)))
          (println (colorize level formatted))))

      ;; File output
      (when @log-file
        (try
          (spit @log-file (str formatted "\n") :append true)
          (catch Exception e
            (binding [*out* *err*]
              (println "Error writing to log file:" (.getMessage e)))))))))

(defn debug [message & args]
  (write-log :debug (apply format message args)))

(defn info [message & args]
  (write-log :info (apply format message args)))

(defn warn [message & args]
  (write-log :warn (apply format message args)))

(defn error [message & args]
  (write-log :error (apply format message args)))

(defn exception
  "Log exception with stack trace"
  [message throwable]
  (error "%s: %s" message (.getMessage throwable))
  (when (should-log? :debug)
    (error "Stack trace:")
    (doseq [line (.getStackTrace throwable)]
      (error "  at %s" line))))


(defmacro with-timing
  "Execute code and log execution time"
  [description & body]
  `(let [start# (System/nanoTime)
         result# (do ~@body)
         elapsed# (/ (- (System/nanoTime) start#) 1000000.0)]
     (info "%s completed in %.2fms" ~description elapsed#)
     result#))

(defmacro log-errors
  "Execute code and automatically log exceptions"
  [& body]
  `(try
     ~@body
     (catch Exception e#
       (exception "Error during execution" e#)
       (throw e#))))


(defn clear-log-file!
  "Clear the log file contents"
  []
  (when @log-file
    (try
      (spit @log-file "")
      (info "Log file cleared")
      (catch Exception e
        (error "Error clearing log: %s" (.getMessage e))))))

(defn get-log-stats
  "Return current logger statistics"
  []
  {:level @current-level
   :file @log-file
   :console @log-to-console
   :file-exists (and @log-file (.exists (io/file @log-file)))
   :file-size (when (and @log-file (.exists (io/file @log-file)))
                (.length (io/file @log-file)))})

(comment
  ;; Configure logger
  (configure! {:level :debug
               :file "logs/app.log"
               :console true})

  ;; Usage examples
  (debug "Debug message: %d" 42)
  (info "Processing %s..." "file.csv")
  (warn "Suspicious value: %f" 99999.99)
  (error "Processing failed")

  ;; Timing example
  (with-timing "CSV processing"
    (Thread/sleep 1000)
    "result")

  ;; Get statistics
  (get-log-stats))
