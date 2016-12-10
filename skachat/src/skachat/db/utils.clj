(ns skachat.db.utils
  (:require [datomic.api :as d]
            [io.rkn.conformity :as c]
            [skachat.db.core :refer [connect! db-url]]))


(defn dbdrop []
  (d/delete-database db-url))


(defn ensure-conforms [edn]
  (d/create-database db-url)
  (let [norms-map (c/read-resource edn)]
    (c/ensure-conforms (connect!) norms-map)))


(defn ensure-migrations []
  (ensure-conforms "migrations.edn"))


(defn ensure-fixtures []
  (ensure-migrations)
  (ensure-conforms "fixtures.edn"))
