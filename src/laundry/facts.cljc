(ns laundry.facts
  "Per-jurisdiction garment-care/dry-cleaning regulatory catalog -- the
  G2-style spec-basis table the Garment Care Governor checks every
  `:careplan/verify` proposal against ('did the advisor cite an
  OFFICIAL public source for this jurisdiction's garment-care/solvent-
  handling framework, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official cleaning-
  business-licensing or dry-cleaning-solvent-handling/environmental
  authority (see `:provenance`); they are a STARTING catalog, not a
  from-scratch survey of all ~194 jurisdictions. Extending coverage is
  additive: add one map to `catalog`, cite a real source, done --
  never invent a jurisdiction's requirements to make coverage look
  bigger.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the customer-
  consent/care-plan/garment-care-label-verification/cleaning-process
  evidence set this blueprint's own Offer names; `:legal-basis` /
  `:owner-authority` / `:provenance` are the G2 citation the governor
  requires before any `:actuation/apply-cleaning-process`/`:actuation/
  return-garment` proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "厚生労働省 (Ministry of Health, Labour and Welfare)"
          :legal-basis "クリーニング業法 (Cleaning Business Act, Act No. 207 of 1950)"
          :national-spec "クリーニング所の衛生管理要件および利用者への処理方法説明義務"
          :provenance "https://www.mhlw.go.jp/stf/seisakunitsuite/bunya/kenkou_iryou/seikatsu-eisei/cleaning/index.html"
          :required-evidence ["顧客同意記録 (customer-consent-record)"
                              "取扱方法記録 (care-plan-record)"
                              "洗濯表示確認記録 (garment-care-label-verification-record)"
                              "洗濯処理記録 (cleaning-process-record)"]}
   "USA" {:name "United States"
          :owner-authority "U.S. Environmental Protection Agency (EPA)"
          :legal-basis "Perchloroethylene Dry Cleaning NESHAP, 40 CFR Part 63 Subpart M"
          :national-spec "Dry-cleaning solvent-handling and emissions requirements; state consumer-protection garment-care disclosure laws"
          :provenance "https://www.epa.gov/stationary-sources-air-pollution/perchloroethylene-dry-cleaning-facilities-national-emission"
          :required-evidence ["Customer consent record"
                              "Care-plan record"
                              "Garment-care-label verification record"
                              "Cleaning-process record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Environment Agency"
          :legal-basis "Environmental Permitting (England and Wales) Regulations 2016"
          :national-spec "Dry-cleaning solvent-use permitting and Textile Services Association garment-care standards"
          :provenance "https://www.gov.uk/guidance/environmental-permit-standard-rules-sr2015-no1-dry-cleaning"
          :required-evidence ["Customer consent record"
                              "Care-plan record"
                              "Garment-care-label verification record"
                              "Cleaning-process record"]}
   "DEU" {:name "Germany"
          :owner-authority "Umweltbundesamt (UBA)"
          :legal-basis "Verordnung über Anlagen zur Reinigung von Textilien und ähnlichen Gegenständen mit halogenierten organischen Lösungsmitteln (2. BImSchV)"
          :national-spec "Zulassungs- und Emissionsanforderungen an Chemischreinigungsanlagen"
          :provenance "https://www.umweltbundesamt.de/themen/luft/emissionen-von-luftschadstoffen"
          :required-evidence ["Einwilligungsprotokoll (customer-consent-record)"
                              "Pflegeplanprotokoll (care-plan-record)"
                              "Pflegeetikettnachweis (garment-care-label-verification-record)"
                              "Reinigungsprotokoll (cleaning-process-record)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to apply a
  cleaning process or return a garment on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-9601 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `laundry.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
