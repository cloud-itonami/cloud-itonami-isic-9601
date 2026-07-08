(ns laundry.laundryadvisor
  "LaundryOps-LLM client -- the *contained intelligence node* for the
  garment-care actor (README: \"Garment Care Advisor\").

  It normalizes garment intake, drafts a per-jurisdiction garment-care
  evidence checklist, screens garments for a lapsed solvent-handling
  certification, drafts the cleaning-process-application action, and
  drafts the garment-return action. CRITICAL: it is a smart-but-
  untrusted advisor. It returns a *proposal* (with a rationale + the
  fields it cited), never a committed record or a real cleaning-
  process application/garment return. Every output is censored
  downstream by `laundry.governor` before anything touches the SSoT,
  and `:actuation/apply-cleaning-process`/`:actuation/return-garment`
  proposals NEVER auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/apply-cleaning-process | :actuation/return-garment | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [laundry.facts :as facts]
            [laundry.registry :as registry]
            [laundry.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the garment, jurisdiction or care label. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "衣類記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :garment/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-careplan
  "Per-jurisdiction garment-care evidence checklist draft. `:no-spec?`
  injects the failure mode we must defend against: proposing a
  checklist for a jurisdiction with NO official spec-basis in
  `laundry.facts` -- the Garment Care Governor must reject this (never
  invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [g (store/garment db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction g))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "laundry.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :careplan/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :careplan/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-certification
  "Solvent-handling-certification screening draft. `:certification-
  not-current?` on the garment record injects the failure mode: the
  Garment Care Governor must HOLD, un-overridably, on any lapsed
  certification."
  [db {:keys [subject]}]
  (let [g (store/garment db subject)]
    (cond
      (nil? g)
      {:summary "対象衣類記録が見つかりません" :rationale "no garment record"
       :cites [] :effect :certification/set :value {:garment-id subject :certification-not-current? nil}
       :stake nil :confidence 0.0}

      (true? (:certification-not-current? g))
      {:summary    (str (:garment-description g) ": 溶剤取扱資格の失効を検出")
       :rationale  "スクリーニングが資格失効を検出。人手確認とホールドが必須。"
       :cites      [:certification-check]
       :effect     :certification/set
       :value      {:garment-id subject :certification-not-current? true}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:garment-description g) ": 溶剤取扱資格は有効")
       :rationale  "資格スクリーニング完了。"
       :cites      [:certification-check]
       :effect     :certification/set
       :value      {:garment-id subject :certification-not-current? false}
       :stake      nil
       :confidence 0.9})))

(defn- propose-cleaning-application
  "Draft the actual CLEANING-PROCESS-APPLICATION action -- applying a
  real cleaning process to a garment. ALWAYS `:stake :actuation/apply-
  cleaning-process` -- this is a REAL-WORLD act (garment damage risk
  depends on it), never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`laundry.phase`); the governor also always escalates on
  `:actuation/apply-cleaning-process`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [g (store/garment db subject)]
    {:summary    (str subject " 向け洗濯処理提案"
                      (when g (str " (garment=" (:garment-description g) ")")))
     :rationale  (if g
                   (str "proposed-cleaning-process=" (:proposed-cleaning-process g)
                        " care-label-forbidden-processes=" (:care-label-forbidden-processes g))
                   "衣類記録が見つかりません")
     :cites      (if g [subject] [])
     :effect     :garment/mark-cleaned
     :value      {:garment-id subject}
     :stake      :actuation/apply-cleaning-process
     :confidence (if (and g (not (registry/cleaning-process-forbidden-by-care-label? g))) 0.9 0.3)}))

(defn- propose-garment-return
  "Draft the actual GARMENT-RETURN action -- returning a real garment
  to its customer. ALWAYS `:stake :actuation/return-garment` -- this
  is a REAL-WORLD act, never a draft the actor may auto-run. See
  README `Actuation`: no phase ever adds this op to a phase's `:auto`
  set (`laundry.phase`); the governor also always escalates on
  `:actuation/return-garment`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [g (store/garment db subject)]
    {:summary    (str subject " 向け返却提案"
                      (when g (str " (garment=" (:garment-description g) ")")))
     :rationale  (if g
                   (str "certification-not-current?=" (:certification-not-current? g))
                   "衣類記録が見つかりません")
     :cites      (if g [subject] [])
     :effect     :garment/mark-returned
     :value      {:garment-id subject}
     :stake      :actuation/return-garment
     :confidence (if (and g (not (:certification-not-current? g))) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :garment/intake                     (normalize-intake db request)
    :careplan/verify                    (verify-careplan db request)
    :certification/screen               (screen-certification db request)
    :actuation/apply-cleaning-process   (propose-cleaning-application db request)
    :actuation/return-garment           (propose-garment-return db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたはクリーニング業(洗濯・ドライクリーニング)事業の洗濯処理・"
       "返却エージェントの助言者です。与えられた事実のみに基づき、提案を1つだけ"
       "EDNマップで返します。説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:garment/upsert|:careplan/set|:certification/set|"
       ":garment/mark-cleaned|:garment/mark-returned) "
       ":stake(:actuation/apply-cleaning-process か :actuation/return-garment か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :careplan/verify                    {:garment (store/garment st subject)}
    :certification/screen               {:garment (store/garment st subject)}
    :actuation/apply-cleaning-process   {:garment (store/garment st subject)}
    :actuation/return-garment           {:garment (store/garment st subject)}
    {:garment (store/garment st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Garment Care Governor
  escalates/holds -- an LLM hiccup can never auto-apply a cleaning
  process or auto-return a garment."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :laundryadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
