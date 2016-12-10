(ns skachat.templatetags
  (:require [selmer.filters :refer [add-filter!]]
            [selmer.parser :refer [add-tag!]]
            [config.core :refer [env]]
            [cemerick.url :refer [url]]
            [skachat.utils :refer [join-path thumbnail dimension]]))


(add-filter! :partition
  (fn [col n]
    (let [n (Integer/parseInt n)]
      (partition n n nil col))))


(add-filter! :media
  (fn [path]
    (if (string? path)
      (join-path (:media-url env) path)
      path)))


(add-tag! :url
  (fn [_ _ content]
    (let [path (-> content :url :content)
          base-url (:base-url env)]
      (url base-url path)))
  :endurl)


(add-filter! :url
  (fn [path]
    (if (nil? path)
      path
      (url (:base-url env) path))))


(add-filter! :thumbnail
  (fn [path size]
    (let [size (Integer/parseInt size)]
      (thumbnail path size))))


(add-filter! :width
  (fn [path]
    (dimension path :width)))

(add-filter! :height
  (fn [path]
    (dimension path :height)))


(add-filter! :get
  (fn [m k]
    (get m (keyword k))))


(def per-page (:items-per-page env))

(add-filter! :page
  (fn [coll page]
    (let [page (Integer/parseInt page)
          start-from (* per-page (- page 1))]
      (take per-page (drop start-from coll)))))

(add-filter! :prev-page
  (fn [coll page]
    (let [page (Integer/parseInt page)]
      (if (> page 1)
        (- page 1)
        false))))

(add-filter! :next-page
  (fn [coll page]
    (let [page (Integer/parseInt page)]
      (if (>= (count coll) (* page per-page))
            (+ page 1)
            false))))
