(ns wanderung.tools
  (:require [wanderung.core :as w]))

(defn migrate [{:keys [src tgt]}]
  (w/migrate src tgt))
