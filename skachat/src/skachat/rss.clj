(ns skachat.rss
  (:require [datomic.api :as d]
            [clj-rss.core :as rss]
            [cemerick.url :refer [url]]
            [config.core :refer [env]]
            [skachat.utils :refer [base-url]]
            [skachat.db.core :refer [conn]]))


(defn- item-map [url-format [title description updated & url-args]]
  (let [link (str (url base-url (apply (partial format url-format) url-args)))]
    {:title title
     :description description
     :link link
     :guid link
     :pubDate updated}))


(defn- get-items [db query url-format]
  (let [items (d/q query (d/history db))
        data (map (partial item-map url-format) items)
        sorted (sort-by :pubDate #(compare %2 %1) data)]
    sorted))


(defn- get-channel [db title description link query url-format]
  (let [data {:title title
              :description description
              :link link}]
    (if-let [items (not-empty (get-items db query url-format))]
      (rss/channel-xml (assoc data :lastBuildDate (-> items first :pubDate)) items)
      (rss/channel-xml data))))


(defn apps-channel [db]
  (let [title "Новые программы для Windows - «example.com»"
        description "8 самых последних программ для Windows"
        link (str base-url "/")
        url-format "/%s"
        query '[:find ?title ?description (max ?inst) ?slug
                :in $
                :where [?e :app/published true]
                       [?e :app/name ?title]
                       [?e :app/teaser ?description]
                       [?e :app/slug ?slug]
                       [?e _ _ ?tx]
                       [?tx :db/txInstant ?inst]]]
    (get-channel db title description link query url-format)))


(defn news-channel [db]
  (let [title "Последние публикации на сайте «example.com»"
        description "Последние Компьютерные новости. Подборки программ для ПК и телефона. Обзоры игр и приложений."
        link (str base-url "/news")
        url-format "/news/%s"
        query '[:find ?title ?description (max ?inst) ?slug
                :in $
                :where [?e :app.news/title ?title]
                       [?e :app.news/teaser ?description]
                       [?e :app.news/slug ?slug]
                       [?e _ _ ?tx]
                       [?tx :db/txInstant ?inst]]]
    (get-channel db title description link query url-format)))


(defn rss [channel]
  (let [db (d/db conn)
        xml (channel db)]
    {:status 200
     :headers {"Content-Type" "application/rss+xml; charset=utf-8"}
     :body xml}))
