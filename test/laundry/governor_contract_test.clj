(ns laundry.governor-contract-test
  "The governor contract as executable tests -- the garment-care
  analog of `cloud-itonami-isic-6512`'s `casualty.governor-contract-
  test`. The single invariant under test:

    LaundryOps-LLM never applies a cleaning process or returns a
    garment the Garment Care Governor would reject, `:actuation/
    apply-cleaning-process`/`:actuation/return-garment` NEVER auto-
    commit at any phase, `:garment/intake` (no direct capital risk)
    MAY auto-commit when clean, and every decision (commit OR hold)
    leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [laundry.store :as store]
            [laundry.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :laundry-staff :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving a careplan on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :careplan/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- screen!
  "Walks `subject` through solvent-handling-certification screening ->
  approve, leaving a screening on file. Only safe to call for a
  garment whose certification is already current -- a not-current
  certification HARD-holds the screen itself (see
  `certification-not-current-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :certification/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :garment/intake :subject "garment-1"
                   :patch {:id "garment-1" :garment-description "Wool Suit Jacket"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Wool Suit Jacket" (:garment-description (store/garment db "garment-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest careplan-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :careplan/verify :subject "garment-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/careplan-of db "garment-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a careplan/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :careplan/verify :subject "garment-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/careplan-of db "garment-1")) "no careplan written"))))

(deftest apply-cleaning-process-without-careplan-is-held
  (testing "actuation/apply-cleaning-process before any careplan verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/apply-cleaning-process :subject "garment-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest cleaning-process-forbidden-by-care-label-is-held
  (testing "a garment whose own proposed cleaning process appears on its own care-label forbidden list -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "garment-3")
          res (exec-op actor "t5" {:op :actuation/apply-cleaning-process :subject "garment-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:cleaning-process-forbidden-by-care-label} (-> (store/ledger db) last :basis)))
      (is (empty? (store/cleaning-history db))))))

(deftest certification-not-current-is-held-and-unoverridable
  (testing "a not-current solvent-handling certification on a garment -> HOLD, and never reaches request-approval -- exercised via :certification/screen DIRECTLY, not via the actuation op against an unscreened garment (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's, association's, leasing's, behavioral's, secondary's, card's, water's, telecom's, aerospace's, recovery's, consulting's, union's, congregation's, fab's, energy's, care's, navigator's, learning's, banking's, advertising's, polling's, research's, design's, nursing's, sports's and alliedhealth's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :certification/screen :subject "garment-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:certification-not-current} (-> (store/ledger db) first :basis)))
      (is (nil? (store/certification-of db "garment-4")) "no clearance written"))))

(deftest apply-cleaning-process-always-escalates-then-human-decides
  (testing "a clean, fully-assessed garment still ALWAYS interrupts for human approval -- actuation/apply-cleaning-process is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "garment-1")
          r1 (exec-op actor "t7" {:op :actuation/apply-cleaning-process :subject "garment-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, cleaning-application record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:cleaning-applied? (store/garment db "garment-1"))))
          (is (= 1 (count (store/cleaning-history db))) "one draft cleaning-application record"))))))

(deftest return-garment-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, certification-current garment still ALWAYS interrupts for human approval -- actuation/return-garment is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "garment-1")
          _ (screen! actor "t8pre2" "garment-1")
          r1 (exec-op actor "t8" {:op :actuation/return-garment :subject "garment-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, return record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:garment-returned? (store/garment db "garment-1"))))
          (is (= 1 (count (store/return-history db))) "one draft return record"))))))

(deftest apply-cleaning-process-double-application-is-held
  (testing "applying a cleaning process to the same garment twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "garment-1")
          _ (exec-op actor "t9a" {:op :actuation/apply-cleaning-process :subject "garment-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :actuation/apply-cleaning-process :subject "garment-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-cleaned} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/cleaning-history db))) "still only the one earlier application"))))

(deftest return-garment-double-return-is-held
  (testing "returning the same garment twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "garment-1")
          _ (screen! actor "t10pre2" "garment-1")
          _ (exec-op actor "t10a" {:op :actuation/return-garment :subject "garment-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :actuation/return-garment :subject "garment-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-returned} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/return-history db))) "still only the one earlier return"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :garment/intake :subject "garment-1"
                          :patch {:id "garment-1" :garment-description "Wool Suit Jacket"}} operator)
      (exec-op actor "b" {:op :careplan/verify :subject "garment-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
