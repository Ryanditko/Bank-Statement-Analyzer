(ns nubank-analyzer.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [nubank-analyzer.core :as core]
            [clojure.java.io :as io]))

(deftest test-analyze-file-helper
  (testing "REPL helper function analyze-file"
    ;; This tests the helper function structure
    ;; In a real scenario, you'd need a sample CSV file
    (is (fn? core/analyze-file))
    (is (fn? core/quick-report))))

(deftest test-initialize-system
  (testing "System initialization"
    (let [options {:verbose false :debug false}
          config (core/initialize-system options)]
      (is (map? config))
      (is (contains? config :app))
      (is (contains? config :categories)))))

(deftest test-export-default-config
  (testing "Export default configuration"
    (let [temp-file (str (System/getProperty "java.io.tmpdir")
                         "/test-config-" (System/currentTimeMillis) ".edn")
          result (core/export-default-config temp-file)]
      (is (= 0 result))
      (is (.exists (io/file temp-file)))
      ;; Cleanup
      (.delete (io/file temp-file)))))
