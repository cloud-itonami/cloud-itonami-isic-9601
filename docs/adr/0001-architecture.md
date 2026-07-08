# ADR-0001: LaundryOps-LLM ⊣ Garment Care Governor architecture

## Status

Accepted. `cloud-itonami-isic-9601` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-9601` publishes an OSS business blueprint for
washing and (dry-)cleaning of textile and fur products: laundry and
dry-cleaning services for customers. Like every prior actor in this
fleet, the blueprint alone is not an implementation: this ADR records
the governed-actor architecture that promotes it to real, tested code,
following the same langgraph-clj StateGraph + independent Governor +
Phase 0→3 rollout pattern established by `cloud-itonami-isic-6511`
(life insurance) and applied across sixty prior siblings, most
recently `cloud-itonami-isic-8690` (other human health activities /
allied health).

## Decision

### Decision 1: entity and op shape

The primary entity is a `garment` (matching the blueprint's own Offer
language -- "garment intake", "cleaning-completion/return proposal").
Five ops: `:garment/intake` (directory upsert, no capital risk),
`:careplan/verify` (per-jurisdiction garment-care evidence checklist,
never auto), `:certification/screen` (solvent-handling-certification
screening, unconditional-evaluation discipline, never auto),
`:actuation/apply-cleaning-process` (POSITIVE, high-stakes -- applying
a real cleaning process), and `:actuation/return-garment` (POSITIVE,
high-stakes -- returning a real garment).

### Decision 2: dual-actuation shape on one entity

This blueprint's own text names TWO real-world acts ("applying a
cleaning process or returning a garment"), both acting on the SAME
entity (the garment). Matching `6512`/`6622`/`6520`/`6530`/`6820`/
`6920`/`6611`/`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/`8610`/
`8510`/`9412`/`8720`/`8521`/`6619`/`3600`/`6190`/`3030`/`3830`/`9420`/
`9491`/`2610`/`3512`/`8810`/`8691`/`8569`/`6419`'s dual-actuation-on-
one-entity shape, `high-stakes` is the two-member set `#{:actuation/
apply-cleaning-process :actuation/return-garment}`, each with its own
history collection, sequence counter, and dedicated double-actuation-
guard boolean (`:cleaning-applied?`/`:garment-returned?`, never a
single `:status` value).

### Decision 3: `cleaning-process-forbidden-by-care-label?` -- a genuinely new check, the 6th set-membership/conflict instance

Before writing this check, every prior sibling's governor/registry
namespaces were grepped for "care-label", "garment-care" and
"cleaning-process" -- zero hits, confirming this is a genuinely new
concept, avoiding the false-precedent-claim risk `leasing`'s ADR-0001
documents. It reuses `clinic.registry/treatment-contraindicated?`'s
set-membership/conflict SHAPE (single item vs. a set) with the SAME
'presence-in-forbidden-set' polarity `contraindicated?` uses --
returning to the original polarity (after `alliedhealth.registry/
treatment-outside-scope-of-practice?` established the fleet's first
inverse 'absence-from-allowed-set' polarity instance) for a new
domain concept: a garment's own care label names cleaning processes
that must NOT be applied. The SIXTH instance of the family overall
(`clinic`/`veterinary`/`entertainment`/`nursing`/`alliedhealth`
established the first five). Gates only `:actuation/apply-cleaning-
process`.

### Decision 4: `certification-not-current-violations` -- a concept reuse renamed for this domain, the 45th unconditional-evaluation grounding

The unconditional-evaluation discipline (`casualty.governor/
sanctions-violations`'s original fix) is reused here for its 45th
distinct application overall (continuing the count from
`alliedhealth.governor/credential-not-current-violations` at 44th).
The underlying SHAPE (a credential/certification-currency check,
evaluated unconditionally so the screening op itself can HARD-hold on
its own finding) is the same widely-established pattern this fleet
uses for licensed-professional verticals, renamed `certification-not-
current` (rather than `credential-not-current`) since laundry/dry-
cleaning solvent-handling is typically governed by an operator-level
environmental/hazmat-handling certification rather than an individual
professional license -- a deliberate naming distinction, not a new
concept. Grounded in this blueprint's own operator-guide text
"verification/citation required before any customer-facing
determination." Gates `:certification/screen` and both actuation ops.

### Decision 5: dedicated double-actuation-guard booleans

`:cleaning-applied?`/`:garment-returned?` are dedicated booleans on
the `garment` record, never a single `:status` value -- the same
discipline every prior sibling governor's guards establish, informed
by `cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320).

### Decision 6: Store protocol, MemStore + DatomicStore parity

`laundry.store/Store` is implemented by both `MemStore` (atom-backed,
default for dev/tests/demo) and `DatomicStore` (`langchain.db`-
backed), proven to satisfy the same contract in `test/laundry/
store_contract_test.clj` -- the same seam every sibling actor uses so
swapping the SSoT backend is a configuration change, not a rewrite.
The protocol's per-entity accessor is named `garment` directly -- not
a Clojure special form, so no `-of` suffix workaround was needed.

### Decision 7: Phase 0→3 rollout

Phase 3's `:auto` set has exactly one member, `:garment/intake` (no
capital risk). `:careplan/verify` and `:certification/screen` are
never auto-eligible at any phase (matching every sibling's screening-
op posture), and both `:actuation/apply-cleaning-process`/`:actuation/
return-garment` are permanently excluded from every phase's `:auto`
set -- a structural fact, not a rollout milestone, enforced by BOTH
`laundry.phase` and `laundry.governor`'s `high-stakes` set
independently.

### Decision 8: no bespoke domain capability lib

This blueprint's own `:itonami.blueprint/required-technologies` names
no domain-specific capability beyond the generic robotics/identity/
forms/dmn/bpmn/audit-ledger stack -- there was no capability-lib
decision to make at all.

### Decision 9: mock + LLM advisor pair

`laundry.laundryadvisor` provides `mock-advisor` (deterministic,
default everywhere -- the actor graph and governor contract run
offline) and `llm-advisor` (backed by `langchain.model/ChatModel`,
with a defensive EDN-proposal parser so a malformed LLM response
degrades to a safe low-confidence noop rather than ever auto-applying
a cleaning process or auto-returning a garment).

### Decision 10: no `blueprint.edn` field-sync fixes needed

Matching `advertising`/7310's, `polling`/7320's, `research`/7210's,
`design`/7410's, `nursing`/8710's, `sports`/8541's and `alliedhealth`/
8690's own experience, this repo's `blueprint.edn` already had the
correct `isic-` prefixed `:id` and correctly populated `:required-
technologies`/`:optional-technologies` matching the `kotoba-lang/
industry` registry's own entry for `"9601"` exactly -- only the
`:maturity` field itself needed adding.

## Alternatives considered

- **A single-actuation shape.** Rejected: the blueprint's own text
  names BOTH acts explicitly ("applying a cleaning process or
  returning a garment") -- omitting either would understate the
  blueprint's own scope.
- **Reusing `credential-not-current` literally instead of renaming to
  `certification-not-current`.** Rejected: laundry/dry-cleaning
  solvent-handling compliance is typically an operator/facility-level
  environmental certification, not an individual professional
  license -- the domain-appropriate name keeps the distinction
  honest while still being an acknowledged concept reuse, not a new
  discipline.
- **Reusing `alliedhealth.registry/treatment-outside-scope-of-
  practice?`'s inverse polarity instead of the original 'presence-in-
  forbidden-set' polarity.** Rejected: garment care labels
  conventionally name FORBIDDEN processes (e.g. "do not bleach"), not
  an allowed-processes whitelist -- the original polarity is the more
  natural, honest mapping onto real care-label practice.

## Consequences

- Sixty-first actor in this fleet (60 implemented before this build).
- Confirms the set-membership/conflict check family generalizes to a
  6th instance, returning to the 'presence-in-forbidden-set' polarity
  for a new domain.
- Establishes a genuinely NEW check concept (care-label-forbidden
  cleaning process), grep-verified absent from every prior sibling.
- `MemStore` ‖ `DatomicStore` parity is proven by `test/laundry/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses.
- `blueprint.edn` required no field-sync fixes this time (already
  correct) -- only the `:maturity` flip itself.

## References

- `orgs/cloud-itonami/cloud-itonami-isic-9601/README.md`
- `orgs/cloud-itonami/cloud-itonami-isic-9601/docs/business-model.md`
- `orgs/kotoba-lang/industry/resources/kotoba/industry/registry.edn` (entry `"9601"`)
