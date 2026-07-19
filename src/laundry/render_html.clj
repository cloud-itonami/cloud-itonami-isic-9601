(ns laundry.render-html
  "Build-time HTML renderer for docs/samples/operator-console.html.
  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300).
  Drives the REAL actor stack (laundry.operation -> laundry.governor -> laundry.store).
  No invented numbers, no timestamps, byte-identical across reruns."
  (:require [clojure.string :as str]
            [laundry.store :as store]
            [laundry.operation :as op]
            [laundry.phase :as phase]
            [laundry.governor :as governor]
            [langgraph.graph :as g]))

(def ^:private operator {:actor-id "op-1" :actor-role :laundry-staff :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn run-demo!
  "Drives the real OperationActor StateGraph through a scenario built
  directly from `laundry.store/demo-data` and `laundry.governor`'s
  actual rules (this repo's `laundry.sim` was checked and found
  trustworthy -- its ids/ops match the real seed data and rules, so
  this mirrors the same scenario rather than reusing sim.cljc's -main
  directly, to keep this namespace's demo self-contained):

    1. `:garment/intake` garment-1 -- clean, phase-3 auto-commit
       (`laundry.phase`'s only auto-eligible op).
    2. `:careplan/verify` garment-1 (JPN has a real spec-basis in
       `laundry.facts`) -- escalates (not yet auto-eligible at any
       phase) -> human approval -> commit.
    3. `:actuation/apply-cleaning-process` garment-1 -- a
       `governor/high-stakes` op, ALWAYS escalates even when clean
       -> human approval -> commit (drafts a real cleaning-application
       record via `laundry.registry`).
    4. `:actuation/return-garment` garment-1 -- likewise always
       escalates -> human approval -> commit (drafts a real
       garment-return record).
    5. `:careplan/verify` garment-2 with `:no-spec?` -- garment-2's
       own seeded jurisdiction is \"ATL\", which has NO entry in
       `laundry.facts/catalog` -- HARD hold, rule `:no-spec-basis`.
    6. `:careplan/verify` garment-3 (JPN, has spec-basis) -> commit,
       then `:actuation/apply-cleaning-process` garment-3 -- garment-3
       is seeded with `:proposed-cleaning-process :bleach` and
       `:care-label-forbidden-processes #{:bleach :tumble-dry}` --
       HARD hold, rule `:cleaning-process-forbidden-by-care-label`.
    7. `:certification/screen` garment-4 -- garment-4 is seeded with
       `:certification-not-current? true` -- HARD hold, rule
       `:certification-not-current`.

  Returns the seeded `db` (a `laundry.store/MemStore`) after the run,
  so `render` can read every value straight off it."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "t1" {:op :garment/intake :subject "garment-1"
                       :patch {:id "garment-1" :garment-description "Wool Suit Jacket"}})

    (exec! actor "t2" {:op :careplan/verify :subject "garment-1"})
    (approve! actor "t2")

    (exec! actor "t3" {:op :actuation/apply-cleaning-process :subject "garment-1"})
    (approve! actor "t3")

    (exec! actor "t4" {:op :actuation/return-garment :subject "garment-1"})
    (approve! actor "t4")

    (exec! actor "t5" {:op :careplan/verify :subject "garment-2" :no-spec? true})

    (exec! actor "t6" {:op :careplan/verify :subject "garment-3"})
    (approve! actor "t6")
    (exec! actor "t7" {:op :actuation/apply-cleaning-process :subject "garment-3"})

    (exec! actor "t8" {:op :certification/screen :subject "garment-4"})

    db))

;; ----------------------------- render helpers -----------------------------

(defn- esc
  "Minimal HTML-escape -- every rendered string passes through this."
  [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- last-fact-for
  "The most recent ledger fact for `subject-id`, off the real
  subject-key field this repo's `commit-fact`/`hold-fact` records use:
  `:subject` (see `laundry.operation/commit-fact` and
  `laundry.governor/hold-fact`)."
  [ledger subject-id]
  (last (filter #(= subject-id (:subject %)) ledger)))

(defn- status-cell
  "[css-class label] for the last known ledger fact of a subject --
  the same cond pattern used fleet-wide."
  [fact]
  (cond
    (nil? fact)                                 ["muted" "in progress"]
    (= :committed (:t fact))                    ["ok" "committed"]
    (= :approval-granted (:t fact))              ["ok" "approval-granted"]
    (= :governor-hold (:t fact))                 ["err" (str "governor-hold: " (str/join "," (map name (:basis fact))))]
    (= :approval-rejected (:t fact))             ["err" "approval-rejected"]
    (= :approval-requested (:t fact))            ["warn" "approval-requested"]
    :else                                        ["muted" "in progress"]))

(defn- garments-table [db]
  (let [garments (store/all-garments db)]
    (str
     "<table>\n<thead><tr>\n"
     "<th>id</th><th>description</th><th>proposed process</th><th>care-label forbidden</th>\n"
     "<th>jurisdiction</th><th>cert not current?</th><th>cleaning applied?</th><th>returned?</th><th>status</th>\n"
     "</tr></thead>\n<tbody>\n"
     (str/join
      "\n"
      (for [gm garments
            :let [ledger (store/ledger db)
                  fact (last-fact-for ledger (:id gm))
                  [cls label] (status-cell fact)]]
        (str "<tr>"
             "<td><code>" (esc (:id gm)) "</code></td>"
             "<td>" (esc (:garment-description gm)) "</td>"
             "<td><code>" (esc (:proposed-cleaning-process gm)) "</code></td>"
             "<td><code>" (esc (:care-label-forbidden-processes gm)) "</code></td>"
             "<td>" (esc (:jurisdiction gm)) "</td>"
             "<td>" (if (:certification-not-current? gm) "<span class=\"critical\">yes</span>" "no") "</td>"
             "<td>" (if (:cleaning-applied? gm) "yes" "no") "</td>"
             "<td>" (if (:garment-returned? gm) "yes" "no") "</td>"
             "<td class=\"" cls "\">" (esc label) "</td>"
             "</tr>")))
     "\n</tbody></table>")))

(defn- committed-records-table [db]
  (let [cleanings (store/cleaning-history db)
        returns (store/return-history db)]
    (str
     "<table>\n<thead><tr>\n"
     "<th>record_id</th><th>kind</th><th>garment_id</th><th>jurisdiction</th>\n"
     "</tr></thead>\n<tbody>\n"
     (str/join
      "\n"
      (for [r (concat cleanings returns)]
        (str "<tr>"
             "<td><code>" (esc (get r "record_id")) "</code></td>"
             "<td>" (esc (get r "kind")) "</td>"
             "<td><code>" (esc (get r "garment_id")) "</code></td>"
             "<td>" (esc (get r "jurisdiction")) "</td>"
             "</tr>")))
     "\n</tbody></table>")))

(defn- action-gate-table
  "Static op-contract description, sourced from the real
  `laundry.phase/phases` (phase 3, this actor's `default-phase`) and
  `laundry.governor/high-stakes` -- not invented, just rendered."
  []
  (let [ph (get phase/phases phase/default-phase)]
    (str
     "<table>\n<thead><tr>\n"
     "<th>op</th><th>phase-" phase/default-phase " write allowed?</th><th>auto-eligible?</th><th>always escalates (high-stakes)?</th>\n"
     "</tr></thead>\n<tbody>\n"
     (str/join
      "\n"
      (for [op (sort phase/write-ops)]
        (str "<tr>"
             "<td><code>" (esc op) "</code></td>"
             "<td>" (if (contains? (:writes ph) op) "yes" "<span class=\"warn\">no</span>") "</td>"
             "<td>" (if (contains? (:auto ph) op) "<span class=\"ok\">yes</span>" "no") "</td>"
             "<td>" (if (contains? governor/high-stakes op) "<span class=\"critical\">yes</span>" "no") "</td>"
             "</tr>")))
     "\n</tbody></table>")))

(defn- audit-ledger-table [db]
  (str
   "<table>\n<thead><tr>\n"
   "<th>t</th><th>op</th><th>subject</th><th>disposition</th><th>basis / rule</th>\n"
   "</tr></thead>\n<tbody>\n"
   (str/join
    "\n"
    (for [f (store/ledger db)]
      (str "<tr>"
           "<td>" (esc (:t f)) "</td>"
           "<td><code>" (esc (:op f)) "</code></td>"
           "<td><code>" (esc (:subject f)) "</code></td>"
           "<td class=\""
           (case (:disposition f) :commit "ok" :hold "err" "muted")
           "\">" (esc (:disposition f)) "</td>"
           "<td>" (if (seq (:basis f))
                    (str/join ", " (map (comp esc name) (:basis f)))
                    "&mdash;")
           "</td>"
           "</tr>")))
   "\n</tbody></table>"))

(def ^:private css
  "table { width: 100%; border-collapse: collapse; font-size: 14px; }
.ok { color: #137a3f; }
body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }
header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }
th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }
h2 { margin-top: 0; font-size: 15px; }
.warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }
main { max-width: 980px; margin: 24px auto; padding: 0 20px; }
header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }
.muted { color: #888; font-size: 13px; }
.critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }
.card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
.err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }
th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }
header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }
code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }")

(defn render [db]
  (str
   "<!doctype html>\n"
   "<html lang=\"ja\">\n<head>\n<meta charset=\"utf-8\">\n"
   "<title>laundry.render-html -- Garment Care Governor operator console</title>\n"
   "<style>\n" css "\n</style>\n"
   "</head>\n<body>\n"
   "<header class=\"bar\"><h1>Garment Care Governor -- Operator Console</h1>"
   "<span class=\"badge\">ISIC 9601 &middot; phase " phase/default-phase " (" (:label (get phase/phases phase/default-phase)) ")</span>"
   "</header>\n"
   "<main>\n"
   "<div class=\"card\">\n<h2>Garments</h2>\n" (garments-table db) "\n</div>\n"
   "<div class=\"card\">\n<h2>Committed records (cleaning-application / garment-return drafts)</h2>\n" (committed-records-table db) "\n</div>\n"
   "<div class=\"card\">\n<h2>Action gate (laundry.phase &middot; laundry.governor/high-stakes)</h2>\n" (action-gate-table) "\n</div>\n"
   "<div class=\"card\">\n<h2>Audit ledger</h2>\n" (audit-ledger-table db) "\n</div>\n"
   "</main>\n"
   "</body></html>\n"))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out)))
