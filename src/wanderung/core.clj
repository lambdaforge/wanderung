(ns wanderung.core
  (:require [datahike.api :as d]
            [datomic.client.api :as dt]
            [wanderung.datomic-cloud :as wdc]
            [wanderung.datahike :as wd]
            [wanderung.datom :as datom]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :refer [split]]
            [clojure.java.io :as io]
            [taoensso.nippy :as nippy])
  (:gen-class))


;;;------- Basic datoms interface -------

;; Two elementary methods for moving datoms to and from a database.
(defmulti datoms-from-storage (fn [config] (:wanderung/type config)))
(defmulti datoms-to-storage (fn [config datoms] (:wanderung/type config)))

;;; Datomic
(defn datomic-connect [datomic-config]
  (println "➜ Connecting to Datomic...")
  (let [result (dt/connect (dt/client (dissoc datomic-config :name))
                           {:db-name (:name datomic-config)})]
    (println "  ✓ Done")
    result))

(defmethod datoms-from-storage :datomic [storage]
  (-> storage
      datomic-connect
      wdc/extract-datomic-cloud-data))

(defmethod datoms-to-storage :datomic [storage datoms]
  (wdc/transact-datoms-to-datomic (datomic-connect storage) datoms))

;;; Nippy
(defmethod datoms-from-storage :nippy [storage]
  (nippy/thaw-from-file (:filename storage)))

(defmethod datoms-to-storage :nippy [storage datoms]
  (let [filename (:filename storage)]
    (io/make-parents filename)
    (nippy/freeze-to-file filename datoms)))

;;; Datahike
(defn datahike-maybe-create-and-connect [config]
  (when (not (d/database-exists? config))
       (println "➜ Datahike database does not exist.")
       (println "➜ Creating database...")
       (d/create-database config)
       (println "  ✓ Done"))
  (println "➜ Connecting to Datahike...")
  (let [result (d/connect config)]
    (println "  ✓ Done")
    result))

(defmethod datoms-from-storage :datahike [storage]
  (-> storage
      d/connect
      wd/extract-datahike-data))

(defmethod datoms-to-storage :datahike [storage datoms]
  @(d/load-entities (datahike-maybe-create-and-connect storage) datoms)
  true)

;;;------- Migrations -------

(defmulti migrate (fn [source-configuration target-configuration]
                    (mapv :wanderung/type [source-configuration
                                           target-configuration])))

#_(defmethod migrate [:datahike :datahike] [src-cfg tgt-cfg]
    ... optimized implementation for specific migration goes here ...)

(defmethod migrate :default [src tgt]
  (->> (datoms-from-storage src) (datoms-to-storage tgt)))

;;;------- CLI -------

(defn multimethod-for-dispatch-value? [method x]
  (-> (methods method) keys set (contains? x)))

(def cli-options
  [["-s" "--source SOURCE" "Source EDN configuration file"
    :parse-fn identity
    :validate [#(.exists (io/file %)) "Source configuration does not exist."]]
   ["-t" "--target TARGET" "Target EDN configuration file"
    :parse-fn identity
    :validate [#(.exists (io/file %)) "Target configuration does not exist."]]
   ["-h" "--help"]
   ["-c" "--check"]])

(defn load-config [filename]
  (-> filename
      slurp
      read-string))

(defn execute-migration [options]
  (let [{:keys [source target help check]} options
        src-cfg (load-config source)
        tgt-cfg (load-config target)
        src-type (:wanderung/type src-cfg)
        tgt-type (:wanderung/type tgt-cfg)]
    (cond
      (not (multimethod-for-dispatch-value? datoms-from-storage src-type))
      (println "Cannot use" src-type "as source database.")

      (not (multimethod-for-dispatch-value? datoms-to-storage tgt-type))
      (println "Cannot use" tgt-type "as target database.")

      (:wanderung/read-only? tgt-cfg)
      (println "Cannot migrate to read-only database.")

      :default (do
                 (println "➜ Start migrating from" src-type "to" tgt-type "...")
                 (migrate src-cfg tgt-cfg)
                 (println "  ✓ Done")
                 (when check
                   (if (multimethod-for-dispatch-value? datoms-from-storage tgt-type)
                     (do
                       (println "➜ Comparing datoms between source and target...")
                       (if (datom/similar-datoms? (datoms-from-storage src-cfg)
                                                  (datoms-from-storage tgt-cfg))
                         (println "  ✓ Success: Datoms look the same.")
                         (println "ERROR: The datoms differ between source and target.")))
                     (println "ERROR: The target does not support reading datoms")))))))


(defn -main [& args]
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
        (execute-migration options)))))
