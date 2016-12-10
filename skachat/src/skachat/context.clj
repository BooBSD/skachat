(ns skachat.context
  (:require [datomic.api :only [q db] :as d]
            [config.core :refer [env]]
            [skachat.layout :as layout]
            [skachat.db.core :refer [conn]]
            [skachat.utils :refer [del-ns]]))


(defn- all-apps []
  (let [db (d/db conn)
        apps (d/q '[:find [(pull ?e [:app/name :app/slug :app/logo]) ...]
                    :in $ ?a ?v
                    :where [?e ?a ?v]]
                db :app/published true)]
    (del-ns apps)))


(defn- context [request]
  {:all-apps (all-apps)
   :uri (:uri request)
   :dev (:dev env)})


(defn render [request template & [params]]
  (let [params-with-context (merge (context request) params)]
    (layout/render template params-with-context)))
