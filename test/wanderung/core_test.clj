(ns wanderung.core-test
  (:require [clojure.test :refer :all]
            [wanderung.core :as wanderung]
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



