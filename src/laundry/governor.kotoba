(ns laundry.governor
  "Garment Care Governor -- the independent compliance layer that
  earns the LaundryOps-LLM the right to commit. The LLM has no notion
  of jurisdictional garment-care/solvent-handling law, whether a
  proposed cleaning process actually appears on a garment's own
  recorded care-label-forbidden-processes list, whether the operating
  staff's own solvent-handling certification is actually current, or
  when an act stops being a draft and becomes a real-world cleaning-
  process application or garment return, so this MUST be a separate
  system able to *reject* a proposal and fall back to HOLD -- the
  garment-care analog of `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated jurisdiction spec-basis, incomplete evidence, a cleaning
  process forbidden by the garment's own care label, a not-current
  solvent-handling certification, or a double application/return). The
  confidence/actuation gate is SOFT: it asks a human to look (low
  confidence / actuation), and the human may approve -- but see
  `laundry.phase`: for `:stake :actuation/apply-cleaning-process`/
  `:actuation/return-garment` (a real cleaning-process application or
  a real garment return) NO phase ever allows auto-commit either. Two
  independent layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the care-plan proposal cite
                                       an OFFICIAL source (`laundry.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:actuation/apply-cleaning-
                                       process`/`:actuation/return-
                                       garment`, has the garment
                                       actually been assessed with a
                                       full customer-consent-record/
                                       care-plan-record/garment-care-
                                       label-verification-record/
                                       cleaning-process-record evidence
                                       checklist on file?
    3. Cleaning process forbidden
       by care label                  -- for `:actuation/apply-
                                       cleaning-process`,
                                       INDEPENDENTLY recompute whether
                                       the garment's own proposed
                                       cleaning process appears in its
                                       own recorded care-label-
                                       forbidden-processes set
                                       (`laundry.registry/cleaning-
                                       process-forbidden-by-care-
                                       label?`) -- needs no proposal
                                       inspection at all. A GENUINELY
                                       NEW concept in this fleet,
                                       grep-verified absent from every
                                       prior sibling's check names --
                                       the SIXTH instance of this
                                       fleet's set-membership/conflict
                                       check family (`clinic`/
                                       `veterinary`/`entertainment`/
                                       `nursing`/`alliedhealth`
                                       established the first five),
                                       returning to the original
                                       'presence-in-forbidden-set'
                                       polarity for a new domain
                                       concept.
    4. Certification not current   -- reported by THIS proposal itself
                                       (a `:certification/screen` that
                                       just found a lapsed solvent-
                                       handling certification), or
                                       already on file for the garment
                                       (`:certification/screen`/
                                       either actuation op). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME
                                       discipline `casualty.governor/
                                       sanctions-violations`/...(forty-
                                       four prior siblings, most
                                       recently `alliedhealth.governor/
                                       credential-not-current-
                                       violations`)...established -- a
                                       LITERAL-CONCEPT reuse of the
                                       widely-established credential/
                                       certification-currency shape
                                       (renamed for this domain), the
                                       45th distinct application of
                                       this discipline overall.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/apply-
                                       cleaning-process`/`:actuation/
                                       return-garment` (REAL garment-
                                       care acts) -> escalate.

  Two more guards, double-application/double-return prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-cleaned-violations`/
  `already-returned-violations` refuse to apply a cleaning process/
  return a garment for the SAME garment twice, off dedicated
  `:cleaning-applied?`/`:garment-returned?` facts (never a `:status`
  value) -- the SAME 'check a dedicated boolean, not status'
  discipline every prior sibling governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [laundry.facts :as facts]
            [laundry.registry :as registry]
            [laundry.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Applying a real cleaning process and returning a real garment are
  the two real-world actuation events this actor performs -- a
  two-member set, matching every prior dual-actuation sibling's
  shape. Both are POSITIVE actuations (applying/finalizing a real
  record), matching this fleet's majority actuation shape (3600/6190
  remain the only negative-actuation exceptions)."
  #{:actuation/apply-cleaning-process :actuation/return-garment})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:careplan/verify` (or actuation) proposal with no spec-basis
  citation is a HARD violation -- never invent a jurisdiction's
  garment-care requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:careplan/verify :actuation/apply-cleaning-process :actuation/return-garment} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案はクリーニング業運営基準として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/apply-cleaning-process`/`:actuation/return-
  garment`, the jurisdiction's required customer-consent-record/care-
  plan-record/garment-care-label-verification-record/cleaning-process-
  record evidence must actually be satisfied -- do not trust the
  advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/apply-cleaning-process :actuation/return-garment} op)
    (let [g (store/garment st subject)
          careplan (store/careplan-of st subject)]
      (when-not (and careplan
                     (facts/required-evidence-satisfied?
                      (:jurisdiction g) (:checklist careplan)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(顧客同意記録/取扱方法記録/洗濯表示確認記録/洗濯処理記録等)が充足していない状態での提案"}]))))

(defn- cleaning-process-forbidden-by-care-label-violations
  "For `:actuation/apply-cleaning-process`, INDEPENDENTLY recompute
  whether the garment's own proposed cleaning process appears in its
  own recorded care-label-forbidden-processes set via `laundry.
  registry/cleaning-process-forbidden-by-care-label?` -- needs no
  proposal inspection at all, since its inputs are permanent ground-
  truth fields already on the garment."
  [{:keys [op subject]} st]
  (when (= op :actuation/apply-cleaning-process)
    (let [g (store/garment st subject)]
      (when (registry/cleaning-process-forbidden-by-care-label? g)
        [{:rule :cleaning-process-forbidden-by-care-label
          :detail (str subject " の提案処理方法(" (:proposed-cleaning-process g)
                      ")が洗濯表示禁止リスト" (:care-label-forbidden-processes g) "に含まれている")}]))))

(defn- certification-not-current-violations
  "A not-current solvent-handling certification -- reported by THIS
  proposal (e.g. a `:certification/screen` that itself just found a
  lapsed certification), or already on file in the store for the
  garment (`:certification/screen`/either actuation op) -- is a HARD,
  un-overridable hold. Evaluated UNCONDITIONALLY (not scoped to a
  specific op) so the screening op itself can HARD-hold on its own
  finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (true? (get-in proposal [:value :certification-not-current?]))
        garment-id (when (contains? #{:certification/screen :actuation/apply-cleaning-process :actuation/return-garment} op) subject)
        hit-on-file? (and garment-id (true? (:certification-not-current? (store/certification-of st garment-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :certification-not-current
        :detail "溶剤取扱資格が最新でない状態での提案は進められない"}])))

(defn- already-cleaned-violations
  "For `:actuation/apply-cleaning-process`, refuses to apply a
  cleaning process to the SAME garment twice, off a dedicated
  `:cleaning-applied?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/apply-cleaning-process)
    (when (store/garment-already-cleaned? st subject)
      [{:rule :already-cleaned
        :detail (str subject " は既に洗濯処理済み")}])))

(defn- already-returned-violations
  "For `:actuation/return-garment`, refuses to return the SAME garment
  twice, off a dedicated `:garment-returned?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/return-garment)
    (when (store/garment-already-returned? st subject)
      [{:rule :already-returned
        :detail (str subject " は既に返却済み")}])))

(defn check
  "Censors a LaundryOps-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (cleaning-process-forbidden-by-care-label-violations request st)
                           (certification-not-current-violations request proposal st)
                           (already-cleaned-violations request st)
                           (already-returned-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
