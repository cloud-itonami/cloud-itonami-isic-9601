(ns laundry.registry
  "Pure-function cleaning-process-application + garment-return record
  construction -- an append-only garment-care book-of-record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a cleaning-process or
  garment-return reference number -- every laundry/dry-cleaning
  operator/jurisdiction assigns its own reference format. This
  namespace does NOT invent one; it builds a jurisdiction-scoped
  sequence number and validates the record's required fields, the
  same honest, non-fabricating discipline `laundry.facts` uses.

  `cleaning-process-forbidden-by-care-label?` is a GENUINELY NEW check
  concept in this fleet (grep-verified absent from every prior
  sibling's check names before this claim was finalized -- no 'care-
  label'/'garment-care'/'cleaning-process' concept exists anywhere
  else in this fleet). It reuses the set-membership/conflict SHAPE
  `clinic.registry/treatment-contraindicated?` established (single
  item vs. a set, no arithmetic comparison), with the SAME
  'presence-in-forbidden-set' polarity `contraindicated?` uses -- the
  SIXTH instance of this fleet's set-membership/conflict check family
  overall (`clinic`/`veterinary`/`entertainment`/`nursing`/
  `alliedhealth` established the first five; `alliedhealth`'s
  `treatment-outside-scope-of-practice?` was the first 'absence-from-
  allowed-set' polarity instance -- this check returns to the
  original 'presence-in-forbidden-set' polarity for a genuinely new
  domain concept) -- a direct, natural mapping onto real garment-care
  practice (a garment's own care label names cleaning processes that
  must NOT be applied, e.g. 'do not bleach'/'do not tumble dry'; a
  laundry operator applying a forbidden process is exactly the
  failure mode this actor must not let an advisor wave through).

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real garment-care-management system. It builds the
  RECORD a laundry/dry-cleaning operator would keep, not the act of
  applying the cleaning process or returning the garment itself (that
  is `laundry.operation`'s `:actuation/apply-cleaning-process`/
  `:actuation/return-garment`, always human-gated -- see README
  `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  laundry/dry-cleaning operator's own act, not this actor's. See
  README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn cleaning-process-forbidden-by-care-label?
  "Does `garment`'s own `:proposed-cleaning-process` appear in its own
  recorded `:care-label-forbidden-processes` set? A pure ground-truth
  check against the garment's own permanent fields -- no upstream
  comparison needed. The SIXTH instance of this fleet's set-
  membership/conflict check family (see ns docstring)."
  [{:keys [proposed-cleaning-process care-label-forbidden-processes]}]
  (contains? (set care-label-forbidden-processes) proposed-cleaning-process))

(defn register-cleaning-application
  "Validate + construct the CLEANING-PROCESS-APPLICATION registration
  DRAFT -- the laundry/dry-cleaning operator's own act of applying a
  real cleaning process to a garment. Pure function -- does not touch
  any real garment-care-management system; it builds the RECORD an
  operator would keep. `laundry.governor` independently re-verifies
  the garment's own care-label ground truth and blocks a double-
  application for the same garment, before this is ever allowed to
  commit."
  [garment-id jurisdiction sequence]
  (when-not (and garment-id (not= garment-id ""))
    (throw (ex-info "cleaning-application: garment_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "cleaning-application: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "cleaning-application: sequence must be >= 0" {})))
  (let [cleaning-number (str (str/upper-case jurisdiction) "-CLN-" (zero-pad sequence 6))
        record {"record_id" cleaning-number
                "kind" "cleaning-application-draft"
                "garment_id" garment-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "cleaning_number" cleaning-number
     "certificate" (unsigned-certificate "CleaningApplication" cleaning-number cleaning-number)}))

(defn register-garment-return
  "Validate + construct the GARMENT-RETURN registration DRAFT -- the
  laundry/dry-cleaning operator's own act of returning a real garment
  to its customer. Pure function -- does not touch any real garment-
  care-management system; it builds the RECORD an operator would
  keep. `laundry.governor` independently re-verifies the garment's own
  evidence checklist and blocks a double-return for the same garment,
  before this is ever allowed to commit."
  [garment-id jurisdiction sequence]
  (when-not (and garment-id (not= garment-id ""))
    (throw (ex-info "garment-return: garment_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "garment-return: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "garment-return: sequence must be >= 0" {})))
  (let [return-number (str (str/upper-case jurisdiction) "-RTN-" (zero-pad sequence 6))
        record {"record_id" return-number
                "kind" "garment-return-draft"
                "garment_id" garment-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "return_number" return-number
     "certificate" (unsigned-certificate "GarmentReturn" return-number return-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
