(ns skachat.routes.home
  (:require [compojure.core :refer [defroutes context GET]]
            [compojure.coercions :refer [as-int]]
            [ring.util.http-response :refer [ok]]
            [clojure.java.io :as io]
            [datomic.api :only [q db] :as d]
            [skachat.db.core :refer [conn]]
            [skachat.routes.admin :refer [admin-routes]]
            [skachat.context :refer [render]]
            [skachat.utils :refer [del-ns assoc-created-updated]]
            [skachat.sitemap :refer [sitemap]]
            [skachat.rss :refer [rss apps-channel news-channel]]))


(defn home [request]
  (let [db (d/db conn)]
    (if-let [apps (not-empty (d/q '[:find [(pull ?e [:db/id
                                                     :app/name
                                                     :app/slug
                                                     :app/logo
                                                     :app/teaser
                                                     :app/version
                                                     :app/size
                                                     :app/downloadUrl]) ...]
                                    :in $ ?a ?v
                                    :where [?e ?a ?v]]
                                db :app/published true))]
      (render request "home.html" {:apps (del-ns apps)}))))


(defn app [request slug]
  (let [db (d/db conn)]
    (if-let [entity (d/q '[:find (pull ?e [*]) .
                           :in $ ?slug
                           :where [?e :app/slug ?slug]]
                      db slug)]
      (let [app (assoc-created-updated db entity)
            news (d/q '[:find [(pull ?e [:db/id
                                         :app.news/title
                                         :app.news/slug
                                         :app.news/teaser]) ...]
                        :in $ ?app
                        :where [?e :app.news/app ?app]]
                    db (:db/id app))
            last-news (->> news (sort-by :db/id >) (take 3))
            parts (d/q '[:find [(pull ?e [* {:app.part/template [:db/ident]}]) ...]
                         :in $ ?app
                         :where [?e :app.part/app ?app]]
                    db (:db/id app))
            parts (sort-by :db/id < parts)]
        (render request "app.html" {:app (del-ns app)
                                    :news (del-ns last-news)
                                    :parts (del-ns parts)})))))


(defn news [request page]
  (let [db (d/db conn)]
    (if-let [entities (not-empty (d/q '[:find [(pull ?e [*]) ...]
                                        :in $
                                        :where [?e :app.news/title]]
                                    db))]
      (let [news (map (partial assoc-created-updated db) entities)
            sorted-news (sort-by :db/id > news)]
        (render request "news.html" {:news (del-ns sorted-news)
                                     :cur-page (str page)})))))


(defn news-entity [request slug]
  (let [db (d/db conn)]
    (if-let [entity (d/q '[:find (pull ?e [* {:app.news/app [:app/name]}]) .
                           :in $ ?slug
                           :where [?e :app.news/slug ?slug]]
                      db slug)]
      (let [entity (assoc-created-updated db entity)
            related (d/q '[:find [(pull ?e [:app.news/slug :app.news/title :app.news/teaser]) ...]
                           :in $ ?eid ?search
                           :where [(fulltext $ :app.news/text ?search) [[?e]]]
                                  (not [(= ?e ?eid)])]
                      db (:db/id entity) (:app.news/title entity))
            related (take 3 related)]
        (render request "news-entity.html" {:entity (del-ns entity)
                                            :related (del-ns related)})))))


(defn about [request]
  (render request "about.html"))


(defn contacts [request]
  (render request "contacts.html"))


(defn tos [request]
  (render request "tos.html"))


(defroutes home-routes
  (GET "/" [:as request] (home request))
  (GET "/:slug" [slug :as request] (app request slug))
  (GET "/news" [:as request] (news request 1))
  (GET "/news/page/:page" [page :<< as-int :as request] (news request page))
  (GET "/news/:slug" [slug :as request] (news-entity request slug))
  (GET "/about" [:as request] (about request))
  (GET "/tos" [:as request] (tos request))
  (GET "/sitemap.xml" [] (sitemap))
  (GET "/rss" [] (rss apps-channel))
  (GET "/news/rss" [] (rss news-channel))
  (context "/admin" [] (admin-routes)))
