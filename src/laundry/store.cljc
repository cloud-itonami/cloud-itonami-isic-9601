(ns laundry.store
  "SSoT for the laundry actor, behind a `Store` protocol so the backend
  is a swap, not a rewrite -- the same seam every prior `cloud-itonami-
  isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/laundry/store_contract_test.clj), which is the whole point:
  the actor, the Garment Care Governor and the audit ledger never
  know which SSoT they run on.

  Like every prior dual-actuation sibling, this actor has TWO
  actuation events (applying a cleaning process, returning a garment)
  acting on the SAME entity (a `garment`), each with its OWN history
  collection, sequence counter and dedicated double-actuation-guard
  boolean (`:cleaning-applied?`/`:garment-returned?`, never a
  `:status` value) -- the same discipline every prior sibling
  governor's guards establish, informed by `cloud-itonami-isic-6492`'s
  status-lifecycle bug (ADR-2607071320).

  The ledger stays append-only on every backend: 'which garment was
  screened for a current solvent-handling certification, which
  cleaning process was applied, which garment was returned, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a customer trusting a laundry/dry-
  cleaning operator needs, and the evidence an operator needs if an
  application or return decision is later disputed."
  (:require [laundry.registry :as registry]
            [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (garment [s id])
  (all-garments [s])
  (certification-of [s garment-id] "committed solvent-handling-certification screening verdict for a garment, or nil")
  (careplan-of [s garment-id] "committed care-plan evidence assessment, or nil")
  (ledger [s])
  (cleaning-history [s] "the append-only cleaning-process-application history (laundry.registry drafts)")
  (return-history [s] "the append-only garment-return history (laundry.registry drafts)")
  (next-cleaning-sequence [s jurisdiction] "next cleaning-number sequence for a jurisdiction")
  (next-return-sequence [s jurisdiction] "next return-number sequence for a jurisdiction")
  (garment-already-cleaned? [s garment-id] "has this garment's cleaning process already been applied?")
  (garment-already-returned? [s garment-id] "has this garment already been returned?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-garments [s garments] "replace/seed the garment directory (map id->garment)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained garment set covering both actuation
  lifecycles (applying a cleaning process, returning a garment) so the
  actor + tests run offline."
  []
  {:garments
   {"garment-1" {:id "garment-1" :garment-description "Wool Suit Jacket"
                :proposed-cleaning-process :dry-clean :care-label-forbidden-processes #{:bleach :tumble-dry}
                :certification-not-current? false
                :cleaning-applied? false :garment-returned? false
                :jurisdiction "JPN" :status :intake}
    "garment-2" {:id "garment-2" :garment-description "Atlantis Shirt"
                :proposed-cleaning-process :dry-clean :care-label-forbidden-processes #{:bleach :tumble-dry}
                :certification-not-current? false
                :cleaning-applied? false :garment-returned? false
                :jurisdiction "ATL" :status :intake}
    "garment-3" {:id "garment-3" :garment-description "鈴木花子のシルクブラウス"
                :proposed-cleaning-process :bleach :care-label-forbidden-processes #{:bleach :tumble-dry}
                :certification-not-current? false
                :cleaning-applied? false :garment-returned? false
                :jurisdiction "JPN" :status :intake}
    "garment-4" {:id "garment-4" :garment-description "田中一郎のコート"
                :proposed-cleaning-process :dry-clean :care-label-forbidden-processes #{:bleach :tumble-dry}
                :certification-not-current? true
                :cleaning-applied? false :garment-returned? false
                :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- apply-cleaning-process!
  "Backend-agnostic `:garment/mark-cleaned` -- looks up the garment via
  the protocol and drafts the cleaning-process-application record, and
  returns {:result .. :garment-patch ..} for the caller to persist."
  [s garment-id]
  (let [g (garment s garment-id)
        seq-n (next-cleaning-sequence s (:jurisdiction g))
        result (registry/register-cleaning-application garment-id (:jurisdiction g) seq-n)]
    {:result result
     :garment-patch {:cleaning-applied? true
                     :cleaning-number (get result "cleaning_number")}}))

(defn- return-garment!
  "Backend-agnostic `:garment/mark-returned` -- looks up the garment
  via the protocol and drafts the garment-return record, and returns
  {:result .. :garment-patch ..} for the caller to persist."
  [s garment-id]
  (let [g (garment s garment-id)
        seq-n (next-return-sequence s (:jurisdiction g))
        result (registry/register-garment-return garment-id (:jurisdiction g) seq-n)]
    {:result result
     :garment-patch {:garment-returned? true
                     :return-number (get result "return_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (garment [_ id] (get-in @a [:garments id]))
  (all-garments [_] (sort-by :id (vals (:garments @a))))
  (certification-of [_ id] (get-in @a [:certifications id]))
  (careplan-of [_ garment-id] (get-in @a [:careplans garment-id]))
  (ledger [_] (:ledger @a))
  (cleaning-history [_] (:cleanings @a))
  (return-history [_] (:returns @a))
  (next-cleaning-sequence [_ jurisdiction] (get-in @a [:cleaning-sequences jurisdiction] 0))
  (next-return-sequence [_ jurisdiction] (get-in @a [:return-sequences jurisdiction] 0))
  (garment-already-cleaned? [_ garment-id] (boolean (get-in @a [:garments garment-id :cleaning-applied?])))
  (garment-already-returned? [_ garment-id] (boolean (get-in @a [:garments garment-id :garment-returned?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :garment/upsert
      (swap! a update-in [:garments (:id value)] merge value)

      :careplan/set
      (swap! a assoc-in [:careplans (first path)] payload)

      :certification/set
      (swap! a assoc-in [:certifications (first path)] payload)

      :garment/mark-cleaned
      (let [garment-id (first path)
            {:keys [result garment-patch]} (apply-cleaning-process! s garment-id)
            jurisdiction (:jurisdiction (garment s garment-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:cleaning-sequences jurisdiction] (fnil inc 0))
                       (update-in [:garments garment-id] merge garment-patch)
                       (update :cleanings registry/append result))))
        result)

      :garment/mark-returned
      (let [garment-id (first path)
            {:keys [result garment-patch]} (return-garment! s garment-id)
            jurisdiction (:jurisdiction (garment s garment-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:return-sequences jurisdiction] (fnil inc 0))
                       (update-in [:garments garment-id] merge garment-patch)
                       (update :returns registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-garments [s garments] (when (seq garments) (swap! a assoc :garments garments)) s))

(defn seed-db
  "A MemStore seeded with the demo garment set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :careplans {} :certifications {} :ledger [] :cleaning-sequences {}
                           :cleanings [] :return-sequences {} :returns []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Compound values (careplan/certification payloads, ledger facts,
  cleaning/return records) are stored as EDN strings so `langchain.db`
  doesn't expand them into sub-entities -- the same convention every
  sibling actor's store uses."
  {:garment/id                       {:db/unique :db.unique/identity}
   :careplan/garment-id              {:db/unique :db.unique/identity}
   :certification/garment-id         {:db/unique :db.unique/identity}
   :ledger/seq                       {:db/unique :db.unique/identity}
   :cleaning/seq                     {:db/unique :db.unique/identity}
   :return/seq                       {:db/unique :db.unique/identity}
   :cleaning-sequence/jurisdiction   {:db/unique :db.unique/identity}
   :return-sequence/jurisdiction     {:db/unique :db.unique/identity}})

(defn- garment->tx [{:keys [id garment-description proposed-cleaning-process care-label-forbidden-processes
                            certification-not-current?
                            cleaning-applied? garment-returned?
                            jurisdiction status cleaning-number return-number]}]
  (cond-> {:garment/id id}
    garment-description                      (assoc :garment/garment-description garment-description)
    proposed-cleaning-process                 (assoc :garment/proposed-cleaning-process (ls/enc proposed-cleaning-process))
    care-label-forbidden-processes             (assoc :garment/care-label-forbidden-processes (ls/enc care-label-forbidden-processes))
    (some? certification-not-current?)          (assoc :garment/certification-not-current? certification-not-current?)
    (some? cleaning-applied?)                    (assoc :garment/cleaning-applied? cleaning-applied?)
    (some? garment-returned?)                     (assoc :garment/garment-returned? garment-returned?)
    jurisdiction                                   (assoc :garment/jurisdiction jurisdiction)
    status                                          (assoc :garment/status status)
    cleaning-number                                  (assoc :garment/cleaning-number cleaning-number)
    return-number                                     (assoc :garment/return-number return-number)))

(def ^:private garment-pull
  [:garment/id :garment/garment-description :garment/proposed-cleaning-process
   :garment/care-label-forbidden-processes :garment/certification-not-current?
   :garment/cleaning-applied? :garment/garment-returned?
   :garment/jurisdiction :garment/status :garment/cleaning-number :garment/return-number])

(defn- pull->garment [m]
  (when (:garment/id m)
    {:id (:garment/id m) :garment-description (:garment/garment-description m)
     :proposed-cleaning-process (ls/dec* (:garment/proposed-cleaning-process m))
     :care-label-forbidden-processes (or (ls/dec* (:garment/care-label-forbidden-processes m)) #{})
     :certification-not-current? (boolean (:garment/certification-not-current? m))
     :cleaning-applied? (boolean (:garment/cleaning-applied? m))
     :garment-returned? (boolean (:garment/garment-returned? m))
     :jurisdiction (:garment/jurisdiction m) :status (:garment/status m)
     :cleaning-number (:garment/cleaning-number m) :return-number (:garment/return-number m)}))

(defrecord DatomicStore [conn]
  Store
  (garment [_ id]
    (pull->garment (d/pull (d/db conn) garment-pull [:garment/id id])))
  (all-garments [_]
    (->> (d/q '[:find [?id ...] :where [?e :garment/id ?id]] (d/db conn))
         (map #(pull->garment (d/pull (d/db conn) garment-pull [:garment/id %])))
         (sort-by :id)))
  (certification-of [_ id]
    (ls/dec* (d/q '[:find ?p . :in $ ?gid
                :where [?k :certification/garment-id ?gid] [?k :certification/payload ?p]]
              (d/db conn) id)))
  (careplan-of [_ garment-id]
    (ls/dec* (d/q '[:find ?p . :in $ ?gid
                :where [?a :careplan/garment-id ?gid] [?a :careplan/payload ?p]]
              (d/db conn) garment-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp ls/dec* second))))
  (cleaning-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :cleaning/seq ?s] [?e :cleaning/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp ls/dec* second))))
  (return-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :return/seq ?s] [?e :return/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp ls/dec* second))))
  (next-cleaning-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :cleaning-sequence/jurisdiction ?j] [?e :cleaning-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-return-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :return-sequence/jurisdiction ?j] [?e :return-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (garment-already-cleaned? [s garment-id]
    (boolean (:cleaning-applied? (garment s garment-id))))
  (garment-already-returned? [s garment-id]
    (boolean (:garment-returned? (garment s garment-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :garment/upsert
      (d/transact! conn [(garment->tx value)])

      :careplan/set
      (d/transact! conn [{:careplan/garment-id (first path) :careplan/payload (ls/enc payload)}])

      :certification/set
      (d/transact! conn [{:certification/garment-id (first path) :certification/payload (ls/enc payload)}])

      :garment/mark-cleaned
      (let [garment-id (first path)
            {:keys [result garment-patch]} (apply-cleaning-process! s garment-id)
            jurisdiction (:jurisdiction (garment s garment-id))
            next-n (inc (next-cleaning-sequence s jurisdiction))]
        (d/transact! conn
                     [(garment->tx (assoc garment-patch :id garment-id))
                      {:cleaning-sequence/jurisdiction jurisdiction :cleaning-sequence/next next-n}
                      {:cleaning/seq (count (cleaning-history s)) :cleaning/record (ls/enc (get result "record"))}])
        result)

      :garment/mark-returned
      (let [garment-id (first path)
            {:keys [result garment-patch]} (return-garment! s garment-id)
            jurisdiction (:jurisdiction (garment s garment-id))
            next-n (inc (next-return-sequence s jurisdiction))]
        (d/transact! conn
                     [(garment->tx (assoc garment-patch :id garment-id))
                      {:return-sequence/jurisdiction jurisdiction :return-sequence/next next-n}
                      {:return/seq (count (return-history s)) :return/record (ls/enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (ls/enc fact)}])
    fact)
  (with-garments [s garments]
    (when (seq garments) (d/transact! conn (mapv garment->tx (vals garments)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:garments ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [garments]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-garments s garments))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo garment set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
