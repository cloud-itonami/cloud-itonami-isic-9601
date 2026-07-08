(ns laundry.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/apply-cleaning-process`/`:actuation/return-
  garment` must NEVER be a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [laundry.phase :as phase]))

(deftest apply-cleaning-process-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real cleaning-process application"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/apply-cleaning-process))
          (str "phase " n " must not auto-commit :actuation/apply-cleaning-process")))))

(deftest return-garment-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real garment return"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/return-garment))
          (str "phase " n " must not auto-commit :actuation/return-garment")))))

(deftest certification-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :certification/screen))
          (str "phase " n " must not auto-commit :certification/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":garment/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:garment/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :garment/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/apply-cleaning-process} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/return-garment} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :garment/intake} :commit)))))
