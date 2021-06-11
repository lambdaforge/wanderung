(ns wanderung.datom-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as spec]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [wanderung.datom :as datom]))

(def testdata (-> "testdatoms.edn"
                  io/resource
                  slurp
                  edn/read-string))

(deftest valid-tuples-test
  (is (spec/valid? :datom/tuples testdata)))

(deftest entity-attribute-map-test
  (let [m (datom/datoms->entity-attribute-map testdata)]
    (-> m
        (get-in [17592186045426 :db/ident])
        (= :green)
        is)
    (is (= #{:db.install/attribute :inv/color :inv/type :inv/size}
           (datom/ref-attrib-set m)))))

(defn parameterized-datoms [eid-a eid-b tid]
  [[eid-a :person/name "Rudolf" tid true]
   [eid-b :person/name "Vera" tid true]])

(deftest normalized-equal-test
  (is (datom/similar-datoms? (parameterized-datoms 3 5 9)
                             (parameterized-datoms 4 9 20)))
  (is (not (datom/similar-datoms? (parameterized-datoms 3 5 9)
                                  (assoc-in (parameterized-datoms 4 9 20)
                                            [1 2] 119)))))
