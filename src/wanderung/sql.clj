(ns wanderung.sql
  (:require [hugsql.core :as h]))

(def db
  {:subprotocol "h2"
   :subname (str (System/getProperty "java.io.tmpdir")
                 "/princess_bride.h2")})

(h/def-db-fns "sql/example.sql")
(h/def-sqlvec-fns "sql/example.sql")

(create-characters-table db)

(insert-character db {:name "Westley" :specialty "love"})
