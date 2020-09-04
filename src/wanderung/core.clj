(ns wanderung.core
  (:require [datahike.api :as d]
            [datomic.client.api :as dt]
            [wanderung.datomic-cloud :as wdc]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :refer [split]]
            [clojure.java.io :as io])
  (:gen-class))

(defmulti migrate (fn [direction source-configuration target-configuration] direction))

(defmethod migrate [:datomic-cloud :datahike] [_ datomic-config datahike-config]
  (let [datomic-conn (dt/connect (dt/client (dissoc datomic-config :name)) {:db-name (:name datomic-config)})
        datomic-data (wdc/extract-datomic-cloud-data datomic-conn)
        datahike-conn (if (d/database-exists? datahike-config)
                        (do
                          (println "➜ Connecting to Datahike...")
                          (d/connect datahike-config))
                        (do
                          (println "➜ Datahike database does not exist.")
                          (println "➜ Creating database...")
                          (d/create-database datahike-config)
                          (println "  ✓ Done")
                          (println "➜ Connecting to Datahike...")
                          (d/connect datahike-config)))]
    (println "  ✓ Done")
    @(d/load-entities datahike-conn datomic-data)
    true))

(defmethod migrate :default [direction _ _]
  (throw (IllegalArgumentException. (str "Direction " direction " not supported."))))

(def cli-options
  [["-s" "--source SOURCE" "Source EDN configuration file"
    :parse-fn identity
    :validate [#(.exists (io/file %)) "Source configuration does not exist."]]
   ["-t" "--target TARGET" "Target EDN configuration file"
    :parse-fn identity
    :validate [#(.exists (io/file %)) "Target configuration does not exist."]]
   ["-d" "--direction SOURCE_SYSTEM:TARGET_SYSTEM" "Migration directions separated by colon: e.g. datomic-cloud:datahike"
    :parse-fn #(->> (split % #":") (mapv keyword))
    :validate [#(-> (methods migrate) keys set (contains? %)) "Direction not allowed."]]
   ["-h" "--help"]])

(defn -main [& args]
  (let [{{:keys [source target direction help]} :options summary :summary errors :errors} (parse-opts args cli-options)]
    (if errors
      (->> errors (map println) doall)
      (if help
        (do
          (println "Run migrations to datahike from various sources")
          (println "USAGE:")
          (println summary))
        (do
          (println "➜ Start migrating from " (first direction) "to" (second direction) "...")
          (migrate direction (-> source slurp read-string) (-> target slurp read-string))
          (println "  ✓ Done"))))))

(comment

  (def cfg (-> "datahike-file.edn" slurp read-string))

  (def conn (d/connect cfg))

  (d/datoms @conn :eavt nil)


  )
