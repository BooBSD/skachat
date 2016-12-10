(ns skachat.sitemap
  (:require [datomic.api :as d]
            [cemerick.url :refer [url]]
            [config.core :refer [env]]
            [sitemap.core :refer [generate-sitemap]]
            [skachat.utils :refer [base-url]]
            [skachat.db.core :refer [conn]]))


(defn- item-map [url-format priority [updated & url-args]]
  {:loc (str (url base-url (apply (partial format url-format) url-args)))
   :lastmod (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") updated)
   :priority priority})


(defn- get-sitemap [db query url-format priority]
  (let [items (d/q query (d/history db))
        data (map (partial item-map url-format priority) items)
        sorted (sort-by :lastmod #(compare %2 %1) data)]
    sorted))


(defn- apps-sitemap [db]
  (let [priority 0.9
        url-format "/%s"
        query '[:find (max ?inst) ?slug
                :in $
                :where [?e :app/published true]
                       [?e :app/slug ?slug]
                       [?e _ _ ?tx]
                       [?tx :db/txInstant ?inst]]]
    (get-sitemap db query url-format priority)))


(defn- news-sitemap [db]
  (let [priority 0.8
        url-format "/news/%s"
        query '[:find (max ?inst) ?slug
                :in $
                :where [?e :app.news/slug ?slug]
                       [?e _ _ ?tx]
                       [?tx :db/txInstant ?inst]]]
    (get-sitemap db query url-format priority)))


(defn sitemap []
  (let [db (d/db conn)
        apps (apps-sitemap db)
        news (news-sitemap db)
        data (concat apps news)
        xml (generate-sitemap data)]
    {:status 200
     :headers {"Content-Type" "application/xml; charset=utf-8"}
     :body xml}))
