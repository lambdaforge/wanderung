(ns wanderung.core-test
  (:require [clojure.test :refer :all]
            [wanderung.core :refer [datomic-cloud->datahike]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [datahike.api :as d]
            [datomic.api :as dt]))

(s/def ::name (s/and string? #(< 10 (count %) 100)))

;; TODO: use cloud datomic for testing ...
#_(deftest datomic->datahike-test
  (testing "Migrate data from Datomic to Datahike"
    (let [schema [{:db/ident       :name
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one}
                  {:db/ident       :sibling
                   :db/valueType   :db.type/ref
                   :db/cardinality :db.cardinality/many}]
          datomic-uri "datomic:mem://migration-source"
          datahike-uri "datahike:mem://migration-target"]

      (dt/delete-database datomic-uri)
      (dt/create-database datomic-uri)

      (d/delete-database datahike-uri)
      (d/create-database datahike-uri)

      (let [datomic-conn (dt/connect datomic-uri)]

        ;; init schema in Datomic
        @(dt/transact datomic-conn schema)

        ;; generate data in Datomic
        (dotimes [n 100]
          (let [possible-siblings (->> (dt/db datomic-conn)
                                       (dt/q '[:find ?e :where [?e :name _]])
                                       (take 10)
                                       vec)
                new-entities (->> (gen/sample (s/gen ::name) 100)
                                  (map (fn [entity]
                                         (if (empty? possible-siblings)
                                           {:name entity}
                                           {:name entity :sibling (rand-nth possible-siblings)}))))]
            @(dt/transact datomic-conn (vec new-entities))))

        ;; migrate to Datahike
        (datomic-cloud->datahike datomic-uri datahike-uri)

        (let [datahike-conn (d/connect datahike-uri)
              q1 '[:find (count ?e)
                   :where [?e :name _]]
              datomic-db (dt/db datomic-conn)]
          (is (= (dt/q q1 datomic-db) (d/q q1 @datahike-conn)))
          (is (= (mapv  :db/ident schema) (-> @datahike-conn :rschema :db/ident vec)))
          )))))
