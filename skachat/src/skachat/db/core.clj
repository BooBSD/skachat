(ns skachat.db.core
  (:require [datomic.api :as d]
            [config.core :refer [env]]
            [mount.core :refer [defstate]]))


(def db-url (:database-url env))

(defn connect! []
  (d/connect db-url))

(defstate ^:dynamic conn
  :start (connect!))
