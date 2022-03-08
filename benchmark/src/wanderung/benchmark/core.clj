(ns wanderung.benchmark.core
  (:require [wanderung.core :as w]
            [taoensso.timbre :as log]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :as io]
            [datahike.api :as d]))

(defmacro timed
  "Evaluates expr. Returns the value of expr and the time in a map."
  [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr]
     {:res ret# :t (/ (double (- (. System (nanoTime)) start#)) 1000000.0)}))

(def cli-options
  [["-p" "--sample SAMPLE" "Sample size"
    :default 10
    :parse-fn (fn [x] (Long/parseLong x))
    :validate [#(<= % 1000) "Too many samples. Please use a sample size between 0 and 1000"]]
   ["-n" "--tx-count TX_COUNT" "transaction count"
    :default 1000
    :parse-fn (fn [x] (Long/parseLong x))
    :validate [#(<= % 1000) "Too many samples. Please use a sample size between 0 and 1000"]]
   ["-u" "--upersert-count UPSERT_COUNT" "upsert count"
    :default 0
    :parse-fn (fn [x] (Long/parseLong x))
    :validate [#(<= % 1000) "Too many samples. Please use a sample size between 0 and 1000"]]
   ["-s" "--source SOURCE" "Source EDN configuration file"
    :parse-fn identity
    :validate [#(.exists (io/file %)) "Source configuration does not exist."]]
   ["-t" "--target TARGET" "Target EDN configuration file"
    :parse-fn identity
    :validate [#(.exists (io/file %)) "Target configuration does not exist."]]

   ["-h" "--help"]])

(defn run! [& args]
  (let [{options :options
         summary :summary
         errors :errors} (parse-opts args cli-options)]
    (if errors
      (->> errors (map println) doall)
      (if (:help options)
        (do
          (println "Run migrations to datahike from various sources")
          (println "USAGE:")
          (println summary))
        (println options)))))

(comment

  (Long/parseLong "1000")

  (log/set-level! :info)

  (def source-cfg {:wanderung/type :datahike
                   :store {:backend :file
                           :path "/tmp/w-source"}
                   :keep-history? true
                   :name "w-source"
                   :schema-flexibility :write
                   :attribute-refs? false})

  (def schema [{:db/ident       :issue/id
                :db/cardinality :db.cardinality/one
                :db/valueType   :db.type/string}])

  (do (d/delete-database source-cfg)
      (d/create-database source-cfg))

  (def source-conn (d/connect source-cfg))

  (d/transact source-conn schema)

  (let [tx-count 1000
        upsert-count 10
        entities-per-tx 100
        upsert-tx (/ (* tx-count 100) upsert-count)]
    (doseq [n (range tx-count)]
      (let [tx-data (mapv (fn [i] {:db/id (+ 1000 (mod (+ (* n entities-per-tx) i) upsert-tx))
                                   :issue/id (str "i" (+ (* n entities-per-tx) i))}) (range entities-per-tx))]
        (d/transact source-conn {:tx-data tx-data}))))

  (def target-cfg (-> source-cfg
                      (assoc-in [:store :path] "/tmp/w-target")
                      (assoc :name "w-target")))

  (def results (vec (repeatedly 10 (fn []
                                     (d/delete-database target-cfg)
                                     (d/create-database target-cfg)
                                     (timed (w/migrate source-cfg target-cfg))))))

  (spit "upsert_results.edn" results)

  (defn avg [results])

  (defn avg-variance [results]
    (let [n (count results)
          avg (/ (reduce (fn [result {:keys [t]}] (+ result t)) 0.0 results) n)
          variance (/ (reduce (fn [result {:keys [t]}] (+ result  (Math/pow (- t avg) 2.0))) 0.0 results) n)]
      {:avg avg
       :variance variance}))


  (def base-results (-> "base_results.edn" slurp read-string))

  (def upsert-results (-> "upsert_results.edn" slurp read-string))

  (avg-variance upsert-results)

  (avg-variance base-results)


  (/
   (avg base-results)))
