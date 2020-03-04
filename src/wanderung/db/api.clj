(ns wanderung.db.api
  (:require [datomic.client.api :as dc]
            [datahike.api :as dh]))

(defprotocol Historian
  (history [db])
  (as-of [db time-point]))

(defrecord DatahikeDb [state]
  Historian
  (history [db] (map->DatahikeDb {:state (dh/history state)}))
  (as-of [db time-point] (map->DatahikeDb {:state (dh/as-of state time-point)})))

(defrecord DatomicDb [state]
  Historian
  (history [db] (map->DatomicDb {:state (dc/history state)}))
  (as-of [db time-point] (map->DatomicDb {:state (dc/as-of state time-point)})))

(defprotocol Transactor
  (transact [conn arg-map])
  (db [conn]))

(defrecord DatahikeConnection [state]
  Transactor
  (transact [conn arg-map]
    (dh/transact! state arg-map))
  (db [conn] (map->DatahikeDb {:state @state})))

(defrecord DatomicConnection [state]
  Transactor
  (transact [conn arg-map]
    (dc/transact state arg-map))
  (db [conn] (map->DatomicDb {:state (dc/db state)})))

(defprotocol Connector
  (connect [client arg-map]))

(defrecord DatahikeClient [state]
  Connector
  (connect [client arg-map]
    (map->DatahikeConnection {:state (dh/connect state)})))

(defrecord DatomicClient [state]
  Connector
  (connect [client arg-map]
    (map->DatomicConnection {:state (dc/connect state arg-map)})))

(defn datahike-client [config]
  (map->DatahikeClient {:state config}))

(defn datomic-client [config]
  (map->DatomicClient {:state (dc/client config)}))

(defmulti -q-map (fn [{:keys [args] :as arg-map}] (-> args first class)))

(defmethod -q-map DatahikeDb [arg-map]
  (dh/q (update-in arg-map [:args] (fn [old] (into [(-> old first :state)] (rest old))))))

(defmethod -q-map DatomicDb [arg-map]
  (dc/q (update-in arg-map [:args] (fn [old] (into [(-> old first :state)] (rest old))))))

(defmulti -q (fn [query & args] (-> args first class)))

(defmethod -q DatahikeDb [query & args]
  (apply dh/q query (-> args first :state) (rest args)))

(defmethod -q DatomicDb [query & args]
  (apply dc/q query (-> args first :state) (rest args)))

(defn q
  ([arg-map] (-q-map arg-map))
  ([query & args] (apply -q query args)))
