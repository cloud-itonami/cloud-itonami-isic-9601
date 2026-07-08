(ns laundry.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [laundry.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Wool Suit Jacket" (:garment-description (store/garment s "garment-1"))))
      (is (= "JPN" (:jurisdiction (store/garment s "garment-1"))))
      (is (= :dry-clean (:proposed-cleaning-process (store/garment s "garment-1"))))
      (is (= #{:bleach :tumble-dry} (:care-label-forbidden-processes (store/garment s "garment-1"))))
      (is (false? (:certification-not-current? (store/garment s "garment-1"))))
      (is (= :bleach (:proposed-cleaning-process (store/garment s "garment-3"))))
      (is (true? (:certification-not-current? (store/garment s "garment-4"))))
      (is (false? (:cleaning-applied? (store/garment s "garment-1"))))
      (is (false? (:garment-returned? (store/garment s "garment-1"))))
      (is (= ["garment-1" "garment-2" "garment-3" "garment-4"]
             (mapv :id (store/all-garments s))))
      (is (nil? (store/certification-of s "garment-1")))
      (is (nil? (store/careplan-of s "garment-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/cleaning-history s)))
      (is (= [] (store/return-history s)))
      (is (zero? (store/next-cleaning-sequence s "JPN")))
      (is (zero? (store/next-return-sequence s "JPN")))
      (is (false? (store/garment-already-cleaned? s "garment-1")))
      (is (false? (store/garment-already-returned? s "garment-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :garment/upsert
                                 :value {:id "garment-1" :garment-description "Wool Suit Jacket"}})
        (is (= "Wool Suit Jacket" (:garment-description (store/garment s "garment-1"))))
        (is (= #{:bleach :tumble-dry} (:care-label-forbidden-processes (store/garment s "garment-1"))) "unrelated field preserved"))
      (testing "careplan / certification payloads commit and read back"
        (store/commit-record! s {:effect :careplan/set :path ["garment-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/careplan-of s "garment-1")))
        (store/commit-record! s {:effect :certification/set :path ["garment-1"]
                                 :payload {:garment-id "garment-1" :certification-not-current? false}})
        (is (= {:garment-id "garment-1" :certification-not-current? false} (store/certification-of s "garment-1"))))
      (testing "cleaning-process application drafts a record and advances the sequence"
        (store/commit-record! s {:effect :garment/mark-cleaned :path ["garment-1"]})
        (is (= "JPN-CLN-000000" (get (first (store/cleaning-history s)) "record_id")))
        (is (= "cleaning-application-draft" (get (first (store/cleaning-history s)) "kind")))
        (is (true? (:cleaning-applied? (store/garment s "garment-1"))))
        (is (= 1 (count (store/cleaning-history s))))
        (is (= 1 (store/next-cleaning-sequence s "JPN")))
        (is (true? (store/garment-already-cleaned? s "garment-1")))
        (is (false? (store/garment-already-cleaned? s "garment-2"))))
      (testing "garment return drafts a record and advances the sequence"
        (store/commit-record! s {:effect :garment/mark-returned :path ["garment-1"]})
        (is (= "JPN-RTN-000000" (get (first (store/return-history s)) "record_id")))
        (is (= "garment-return-draft" (get (first (store/return-history s)) "kind")))
        (is (true? (:garment-returned? (store/garment s "garment-1"))))
        (is (= 1 (count (store/return-history s))))
        (is (= 1 (store/next-return-sequence s "JPN")))
        (is (true? (store/garment-already-returned? s "garment-1")))
        (is (false? (store/garment-already-returned? s "garment-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/garment s "nope")))
    (is (= [] (store/all-garments s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/cleaning-history s)))
    (is (= [] (store/return-history s)))
    (is (zero? (store/next-cleaning-sequence s "JPN")))
    (is (zero? (store/next-return-sequence s "JPN")))
    (store/with-garments s {"x" {:id "x" :garment-description "n"
                                :proposed-cleaning-process :dry-clean
                                :care-label-forbidden-processes #{:bleach}
                                :certification-not-current? false
                                :cleaning-applied? false :garment-returned? false
                                :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:garment-description (store/garment s "x"))))))
