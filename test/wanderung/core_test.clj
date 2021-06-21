(ns wanderung.core-test
  (:require [clojure.test :refer :all]
            [wanderung.core :as wanderung]
            [datahike.api :as dh]
            [datomic.client.api :as dt]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import [java.io File]))

(def testdata (-> "testdatoms.edn"
                  io/resource
                  slurp
                  edn/read-string))

(deftest test-nippy-migration
  (let [a {:wanderung/type :nippy
           :filename (str (File/createTempFile "a_file" ".nippy"))}
        b {:wanderung/type :nippy
           :filename (str (File/createTempFile "b_file" ".nippy"))}]
    (wanderung/datoms-to-storage a testdata)
    (wanderung/migrate a b)
    (is (= testdata (wanderung/datoms-from-storage b)))))



(deftest test-datomic->datahike
  (let [source-client-cfg {:wanderung/type :datomic
                           :server-type :dev-local
                           :storage-dir :mem
                           :name "wanderung-source-test"
                           :system "CI"}
        source-db-cfg {:db-name "wanderung-source-test"}
        target-cfg {:wanderung/type :datahike
                    :store {:backend :mem
                            :id "wanderung-target-test"}}
        dt-client (dt/client source-client-cfg)
        _ (dt/delete-database dt-client source-db-cfg)
        _ (dt/create-database dt-client source-db-cfg)
        dt-conn (dt/connect dt-client source-db-cfg)
        _ (dh/delete-database target-cfg)
        _ (dh/create-database target-cfg)]
    (dt/transact dt-conn {:tx-data [{:db/ident :person/name
                                     :db/valueType :db.type/string
                                     :db/unique :db.unique/identity
                                     :db/cardinality :db.cardinality/one}
                                    {:db/ident :person/age
                                     :db/valueType :db.type/long
                                     :db/cardinality :db.cardinality/one}
                                    {:db/ident :person/siblings
                                     :db/valueType :db.type/ref
                                     :db/cardinality :db.cardinality/many}]})
    (dt/transact dt-conn {:tx-data [{:db/id -1
                                     :person/name "Alice"
                                     :person/age 25}
                                    {:db/id -2
                                     :person/name "Bob"
                                     :person/age 35}
                                    {:person/name "Charlie"
                                     :person/age 45
                                     :person/siblings [-1 -2]}]})
    (wanderung/migrate source-client-cfg target-cfg)
    (testing "test basic data"
      (letfn [(coerce-result [result]
                (->> result
                     (map #(update (first %) :person/siblings set))
                     set))]
        (let [dh-conn (dh/connect target-cfg)
              query '[:find (pull ?e [:person/name :person/age {:person/siblings [:person/name]}])
                      :where [?e :person/name _]]
              dt-result (->> (dt/db dt-conn)
                             (dt/q query)
                             coerce-result)
              dh-result (->> (dh/db dh-conn)
                             (dh/q query)
                             coerce-result)]
          (is (= dt-result
                 dh-result)))))))

