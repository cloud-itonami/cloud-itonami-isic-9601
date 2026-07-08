(ns laundry.registry-test
  (:require [clojure.test :refer [deftest is]]
            [laundry.registry :as r]))

;; ----------------------------- cleaning-process-forbidden-by-care-label? -----------------------------

(deftest not-forbidden-when-process-not-on-label
  (is (not (r/cleaning-process-forbidden-by-care-label?
            {:proposed-cleaning-process :dry-clean
             :care-label-forbidden-processes #{:bleach :tumble-dry}}))))

(deftest forbidden-when-process-is-on-label
  (is (r/cleaning-process-forbidden-by-care-label?
       {:proposed-cleaning-process :bleach
        :care-label-forbidden-processes #{:bleach :tumble-dry}})))

(deftest not-forbidden-on-missing-fields
  (is (not (r/cleaning-process-forbidden-by-care-label? {})))
  (is (not (r/cleaning-process-forbidden-by-care-label? {:proposed-cleaning-process :dry-clean}))))

;; ----------------------------- register-cleaning-application -----------------------------

(deftest cleaning-is-a-draft-not-a-real-application
  (let [result (r/register-cleaning-application "garment-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest cleaning-assigns-cleaning-number
  (let [result (r/register-cleaning-application "garment-1" "JPN" 7)]
    (is (= (get result "cleaning_number") "JPN-CLN-000007"))
    (is (= (get-in result ["record" "garment_id"]) "garment-1"))
    (is (= (get-in result ["record" "kind"]) "cleaning-application-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest cleaning-validation-rules
  (is (thrown? Exception (r/register-cleaning-application "" "JPN" 0)))
  (is (thrown? Exception (r/register-cleaning-application "garment-1" "" 0)))
  (is (thrown? Exception (r/register-cleaning-application "garment-1" "JPN" -1))))

;; ----------------------------- register-garment-return -----------------------------

(deftest return-is-a-draft-not-a-real-return
  (let [result (r/register-garment-return "garment-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest return-assigns-return-number
  (let [result (r/register-garment-return "garment-1" "JPN" 3)]
    (is (= (get result "return_number") "JPN-RTN-000003"))
    (is (= (get-in result ["record" "garment_id"]) "garment-1"))
    (is (= (get-in result ["record" "kind"]) "garment-return-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest return-validation-rules
  (is (thrown? Exception (r/register-garment-return "" "JPN" 0)))
  (is (thrown? Exception (r/register-garment-return "garment-1" "" 0)))
  (is (thrown? Exception (r/register-garment-return "garment-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-cleaning-application "garment-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-cleaning-application "garment-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-CLN-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-CLN-000001" (get-in hist2 [1 "record_id"])))))
