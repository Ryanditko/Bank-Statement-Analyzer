(ns nubank-analyzer.validation-test
  (:require [clojure.test :refer [deftest is testing]]
            [nubank-analyzer.validation :as v]))

(deftest test-validate-amount
  (testing "Validação de valores"
    (is (:valid? (v/validate-amount 100.0)))
    (is (:valid? (v/validate-amount -45.50)))
    (is (not (:valid? (v/validate-amount nil))))
    (is (not (:valid? (v/validate-amount Double/NaN))))
    (is (not (:valid? (v/validate-amount Double/POSITIVE_INFINITY))))))

(deftest test-validate-date
  (testing "Validação de datas"
    (let [formats ["dd/MM/yyyy" "yyyy-MM-dd"]]
      (is (:valid? (v/validate-date "15/10/2025" formats)))
      (is (:valid? (v/validate-date "2025-10-15" formats)))
      (is (not (:valid? (v/validate-date "" formats))))
      (is (not (:valid? (v/validate-date "invalid" formats)))))))

(deftest test-validate-csv-headers
  (testing "Validação de headers CSV"
    (is (:valid? (v/validate-csv-headers ["date" "description" "amount"])))
    (is (:valid? (v/validate-csv-headers ["Data" "Descrição" "Valor"])))
    (is (not (:valid? (v/validate-csv-headers ["col1" "col2" "col3"]))))))

(deftest test-detect-outliers
  (testing "Detecção de outliers"
    (let [amounts [10 20 30 40 50 1000]
          result (v/detect-outliers amounts 3.0)]
      (is (seq (:outliers result)))
      (is (some #(= 1000 %) (:outliers result))))))
