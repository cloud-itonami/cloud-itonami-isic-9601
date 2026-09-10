(ns laundry.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean garment through
  intake -> care-plan verification -> solvent-handling-certification
  screening -> cleaning-process-application proposal (always
  escalates) -> human approval -> commit, then through garment-return
  proposal (always escalates) -> human approval -> commit, then shows
  five HARD holds (a jurisdiction with no spec-basis, a cleaning
  process forbidden by the garment's own care label, a not-current
  solvent-handling certification screened directly via
  `:certification/screen` [never via an actuation op against an
  unscreened garment -- see this actor's own governor ns docstring /
  the lesson `parksafety`'s ADR-2607071922 Decision 5, `eldercare`'s,
  `museum`'s, `conservation`'s, `salon`'s, `entertainment`'s,
  `casework`'s, `hospital`'s, `facility`'s, `school`'s, `association`'s,
  `leasing`'s, `behavioral`'s, `secondary`'s, `card`'s, `water`'s,
  `telecom`'s, `aerospace`'s, `recovery`'s, `consulting`'s, `union`'s,
  `congregation`'s, `fab`'s, `energy`'s, `care`'s, `navigator`'s,
  `learning`'s, `banking`'s, `advertising`'s, `polling`'s, `research`'s,
  `design`'s, `nursing`'s, `sports`'s and `alliedhealth`'s ADR-0001s
  already recorded], and a double cleaning-process-application/
  garment-return of an already-processed garment) that never reach a
  human at all, and prints the audit ledger + the draft cleaning-
  application and garment-return records."
  (:require [langgraph.graph :as g]
            [laundry.store :as store]
            [laundry.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :laundry-staff :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== garment/intake garment-1 (JPN, clean; dry-clean not forbidden, certification current) ==")
    (println (exec! actor "t1" {:op :garment/intake :subject "garment-1"
                                :patch {:id "garment-1" :garment-description "Wool Suit Jacket"}} operator))

    (println "== careplan/verify garment-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :careplan/verify :subject "garment-1"} operator))
    (println (approve! actor "t2"))

    (println "== certification/screen garment-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :certification/screen :subject "garment-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/apply-cleaning-process garment-1 (always escalates -- actuation/apply-cleaning-process) ==")
    (let [r (exec! actor "t4" {:op :actuation/apply-cleaning-process :subject "garment-1"} operator)]
      (println r)
      (println "-- human laundry staff approves --")
      (println (approve! actor "t4")))

    (println "== actuation/return-garment garment-1 (always escalates -- actuation/return-garment) ==")
    (let [r (exec! actor "t5" {:op :actuation/return-garment :subject "garment-1"} operator)]
      (println r)
      (println "-- human laundry staff approves --")
      (println (approve! actor "t5")))

    (println "== careplan/verify garment-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :careplan/verify :subject "garment-2" :no-spec? true} operator))

    (println "== careplan/verify garment-3 (escalates -- human approves; sets up the care-label test) ==")
    (println (exec! actor "t7" {:op :careplan/verify :subject "garment-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/apply-cleaning-process garment-3 (bleach forbidden by care label -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/apply-cleaning-process :subject "garment-3"} operator))

    (println "== certification/screen garment-4 (not-current -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :certification/screen :subject "garment-4"} operator))

    (println "== actuation/apply-cleaning-process garment-1 AGAIN (double-application -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/apply-cleaning-process :subject "garment-1"} operator))

    (println "== actuation/return-garment garment-1 AGAIN (double-return -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/return-garment :subject "garment-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft cleaning-application records ==")
    (doseq [r (store/cleaning-history db)] (println r))

    (println "== draft garment-return records ==")
    (doseq [r (store/return-history db)] (println r))))
