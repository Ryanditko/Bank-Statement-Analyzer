(ns nubank-analyzer.parser-test
  (:require [clojure.test :refer [deftest is testing]]
            [nubank-analyzer.parser :as parser]))

(deftest test-parse-amount
  (testing "Parsing Brazilian monetary values"
    (is (= -1234.56 (parser/parse-amount "R$ -1.234,56")))
    (is (= 1234.56 (parser/parse-amount "R$ 1.234,56")))
    (is (= -45.50 (parser/parse-amount "-45,50")))
    (is (= 100.0 (parser/parse-amount "100"))))

  (testing "Parsing international values"
    (is (= -1234.56 (parser/parse-amount "-1,234.56")))
    (is (= 1234.56 (parser/parse-amount "1234.56"))))

  (testing "Invalid values"
    (is (nil? (parser/parse-amount "")))
    (is (nil? (parser/parse-amount nil)))
    (is (nil? (parser/parse-amount "abc")))))

(deftest test-parse-date
  (testing "Date parsing"
    (let [formats ["dd/MM/yyyy" "yyyy-MM-dd"]]
      (is (= "2025-10-15" (parser/parse-date "15/10/2025" formats)))
      (is (= "2025-10-15" (parser/parse-date "2025-10-15" formats)))
      (is (nil? (parser/parse-date "" formats)))
      (is (nil? (parser/parse-date nil formats))))))

(deftest test-normalize-header
  (testing "Header normalization"
    (is (= "data" (parser/normalize-header "Data")))
    (is (= "descricao" (parser/normalize-header "Descrição")))
    (is (= "valor-total" (parser/normalize-header "Valor Total")))))
