(ns nubank-analyzer.cli-test
  (:require [clojure.test :refer [deftest is testing]]
            [nubank-analyzer.cli :as cli]
            [clojure.string :as str]))

(deftest test-validate-args
  (testing "Argument validation - help"
    (let [result (cli/validate-args ["--help"])]
      (is (:ok? result))
      (is (some? (:exit-message result)))))

  (testing "Argument validation - missing input"
    (let [result (cli/validate-args [])]
      (is (not (:ok? result)))
      (is (some? (:exit-message result)))))

  (testing "Argument validation - valid input"
    (let [result (cli/validate-args ["-i" "test.csv"])]
      (is (:ok? result))
      (is (map? (:options result)))
      (is (= "test.csv" (get-in result [:options :input])))))

  (testing "Argument validation - export config"
    (let [result (cli/validate-args ["--export-config" "config.edn"])]
      (is (:ok? result))
      (is (= :export-config (:action result)))
      (is (= "config.edn" (:export-path result))))))

(deftest test-build-filters
  (testing "Filter construction"
    (let [options {:category "Food" :min-amount 50.0}
          filters (cli/build-filters options)]
      (is (= "Food" (:category filters)))
      (is (= 50.0 (:min-amount filters))))))

(deftest test-determine-output-format
  (testing "Output format determination"
    (is (= :json (cli/determine-output-format {:format "json"})))
    (is (= :html (cli/determine-output-format {:format "txt" :output "report.html"})))
    (is (= :txt (cli/determine-output-format {:format "txt"})))))

(deftest test-format-success-message
  (testing "Success message formatting"
    (let [message (cli/format-success-message "/path/to/report.txt" 5000)]
      (is (string? message))
      (is (str/includes? message "SUCCESS"))
      (is (str/includes? message "/path/to/report.txt"))
      (is (str/includes? message "5.00")))))

(deftest test-format-validation-message
  (testing "Validation message formatting"
    (let [validation {:valid-count 95 :invalid-count 5}
          message (cli/format-validation-message validation)]
      (is (string? message))
      (is (str/includes? message "VALIDATION RESULTS"))
      (is (str/includes? message "95"))
      (is (str/includes? message "5")))))
