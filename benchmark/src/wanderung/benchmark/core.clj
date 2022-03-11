(ns wanderung.benchmark.core
  (:require [wanderung.core :as w]
            [taoensso.timbre :as log]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :as io]
            [datahike.api :as d])
  (:import [java.util Date])
  (:gen-class))

(def valid-chars
  (map char (concat (range 48 58)
                    (range 66 91)
                    (range 97 123))))

(defn random-char []
  (nth valid-chars (rand (count valid-chars))))

(defn random-str [length]
  (apply str (take length (repeatedly random-char))))

(defmacro timed
  "Evaluates expr. Returns the value of expr and the time in a map."
  [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr]
     {:res ret# :t (/ (double (- (. System (nanoTime)) start#)) 1000000.0)}))

(def cli-options
  [["-p" "--sample SAMPLE" "Sample size"
    :default 5
    :parse-fn (fn [x] (Long/parseLong x))
    :validate [#(<= % 1000) "Too many samples. Please use a sample size between 0 and 1000"]]
   ["-n" "--tx-count TX_COUNT" "transaction count"
    :default 1000
    :parse-fn (fn [x] (Long/parseLong x))
    :validate [#(<= % 1000000) "Too many Transactions. Please use a sample size between 0 and 1000000"]]
   ["-u" "--upsert-count UPSERT_COUNT" "upsert count"
    :default 1
    :parse-fn (fn [x] (Long/parseLong x))
    :validate [#(<= 1 % 100) "Wrong upsert count. Please use an upsert size between 1 and 100"]]
   ["-e" "--entities-count ENTITIES_COUNT" "entities count"
    :default 1
    :parse-fn (fn [x] (Long/parseLong x))
    :validate [#(<= 1 % 10000) "Too many entities. Please use an entity size between 1 and 10000"]]
   ["-s" "--source SOURCE" "Source EDN configuration file"
    :parse-fn identity
    :validate [#(.exists (io/file %)) "Source configuration does not exist."]]
   ["-o" "--output OUTPUT" "Output file. Appends to existing files."
    :parse-fn identity]
   ["-t" "--target TARGET" "Target EDN configuration file"
    :parse-fn identity
    :validate [#(.exists (io/file %)) "Target configuration does not exist."]]
   ["-h" "--help"]])

(defn init-source [source tx-count upsert-count entities-count]
  (d/delete-database source)
  (d/create-database source)
  (let [schema [{:db/ident       :issue/id
                 :db/cardinality :db.cardinality/one
                 :db/valueType   :db.type/string}]
        conn (d/connect source)
        _ (d/transact conn schema)
        upsert-tx (/ (* tx-count entities-count) upsert-count)
        entities-range (range entities-count)]
    (doseq [n (range tx-count)]
      (let [tx-data (mapv (fn [i] {:db/id (+ 1000 (mod (+ (* n entities-count) i) upsert-tx))
                                  :issue/id (str "i" (+ (* n entities-count) i))}) entities-range)]
        (d/transact conn {:tx-data tx-data})))))

(defn avg-variance [results]
  (let [n (count results)
        avg (/ (reduce (fn [result {:keys [t]}] (+ result t)) 0.0 results) n)
        variance (/ (reduce (fn [result {:keys [t]}] (+ result  (Math/pow (- t avg) 2.0))) 0.0 results) n)]
    {:avg avg
     :variance variance}))

(defn print-results
  ([results]
   (print-results results nil))
  ([results output-file]
   (if (some? output-file)
     (spit output-file results :append true)
     (println results))))

(defn -main [& args]
  (log/set-level! :info)
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
        (let [{:keys [sample tx-count upsert-count entities-count source target output]} options]
          (println "Used options:" options)
          (if (some? source)
            (if (some? target)
              (let [{:keys [t]} (timed (w/migrate source target))]
                (print-results {:t t} output))
              (let [target-name (str "wanderung_" (random-str 8))
                    target (-> source
                               (assoc-in [:store :path]  (str (System/getProperty "java.io.tmpdir") "/" target-name))
                               (assoc :name target-name))
                    _ (println "Target config" target)
                    {:keys [t]} (timed (w/migrate source target))]
                (print-results {:t t
                                :date (Date.)
                                :options options} output)))
            (let [_ (println "No source given. Using random databases...")
                  source-name (str "wanderung_s_" (random-str 8))
                  source {:wanderung/type :datahike
                          :store {:backend :file
                                  :path (str (System/getProperty "java.io.tmpdir") "/" source-name)}
                          :keep-history? true
                          :name source-name
                          :schema-flexibility :write
                          :attribute-refs? false}
                  _ (println "Source config" source)
                  _ (init-source source tx-count upsert-count entities-count)
                  target-name (str "wanderung_t_" (random-str 8))
                  target (-> source
                             (assoc-in [:store :path]  (str (System/getProperty "java.io.tmpdir") "/" target-name))
                             (assoc :name target-name))
                  _ (println "Target config" target)
                  results (vec (repeatedly sample (fn []
                                        (d/delete-database target)
                                        (d/create-database target)
                                        (timed (w/migrate source target)))))]
              (println "Cleaning up random databases...")
              (d/delete-database source)
              (d/delete-database target)
              (println "Done")
              (print-results {:t (avg-variance results)
                              :date (Date.)
                              :options options} output))))))))
