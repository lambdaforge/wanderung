(ns wanderung.cli
  (:require [wanderung.core :as wc]
            [clojure.set :refer [rename-keys]]
            [environ.core :refer [env]])
  (:import [clojure.lang IExceptionInfo]))

(defn- run-it! [f]
  (try
    f
    (catch Throwable t
      (println (.getMessage t))
      (when-not (instance? IExceptionInfo t)
        (.printStackTrace t))
      (System/exit 1))
    (finally
      (shutdown-agents))))

(defn migrate [opts]
  (let [merged-opts (-> (select-keys env [:wanderung-source :wanderung-target])
                        (rename-keys  {:wanderung-source :source
                                       :wanderung-target :target})
                        (merge opts))]
    (println "Running with opts: " merged-opts)
    (run-it! (wc/migration merged-opts))))

(def migration migrate)

(def m migrate)

(defn help
  ([]
   (help {}))
  ([_]
   (try
     (println "WANDERUNG")
     (println "---------")
     (println "Run migrations with Datahike to and from various sources")
     (println "USAGE:")
     (println "clj -Twanderung [function] [function args]")
     (println "FUNCTIONS:")
     (println "---------")
     (println "migrate/migration/m :source SOURCE :target TARGET")
     (println "Description: Migrates from given source file to a target file. Source and target must be either file path or environment variable. Use WANDERUNG_SOURCE and WANDERUNG_TARGET environment variables if you don't want to use any input.")
     (println "Example: clj -Twanderung migrate :source '\"./source-cfg.edn\"' :target '\"target-cfg.edn\"'")
     (println "Example: clj -Twanderung m :source 'SOURCE_CFG' :target 'TARGET_CFG'")
     (println "---------")
     (println "help/h")
     (println "Description: Prints this lovely help.")
     (println "Example: clj -Twanderung help")
     (catch Throwable t
       (println (.getMessage t))
       (when-not (instance? IExceptionInfo t)
         (.printStackTrace t))
       (System/exit 1))
     (finally
       (shutdown-agents)))))

(def h help)
