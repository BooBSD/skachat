(ns skachat.utils
  (:require [datomic.api :as d]
            [clojure.string :as s]
            [config.core :refer [env]])
  (:import  [net.coobird.thumbnailator Thumbnails]))


(defn join-path [& paths]
  (java.net.URLDecoder/decode
    (str (java.nio.file.Paths/get "" (into-array paths)))
    "utf-8"))

(def base-url (:base-url env))

(def media-root (join-path (System/getProperty "user.dir") (:media-root env)))


(defn- delete-ns [m]
  (reduce (fn [acc k]
            (let [v (k m)
                  value (if (map? v) (delete-ns v) v)]
              (assoc acc (-> k name keyword) value))) {} (keys m)))


(defn del-ns [value]
  (if (map? value)
    (delete-ns value)
    (map delete-ns value)))


(defn assoc-created-updated [db entity]
  (let [[created updated] (d/q '[:find [(min ?inst) (max ?inst)]
                                 :in $ ?e
                                 :where [?e _ _ ?tx]
                                        [?tx :db/txInstant ?inst]]
                            (d/history db) (:db/id entity))]
    (merge entity {:created created :updated updated})))


(def ^:private thumbnails-cache (ref {}))

(defn- alter-thumbnails-cache [thumb-key thumb-file]
  (let [thumb-image (javax.imageio.ImageIO/read thumb-file)
        width (.getWidth thumb-image)
        height (.getHeight thumb-image)
        thumb-value {:thumbnail thumb-key :width width :height height}]
    (alter thumbnails-cache assoc thumb-key thumb-value)
    thumb-value))

(defn thumbnail [path max-size]
  (let [thumb-key (s/replace-first path #"(\.\w+)?$" (str "-thumb-" max-size "$1"))]
    (dosync
      (if-let [thumb (@thumbnails-cache thumb-key)]
        thumb
        (let [image-path (join-path media-root path)
              thumb-path (join-path media-root thumb-key)
              thumb-file (java.io.File. thumb-path)]
          (try
            (alter-thumbnails-cache thumb-key thumb-file)
            (catch javax.imageio.IIOException e
              (..
                (Thumbnails/fromFilenames [image-path])
                (size max-size max-size)
                (outputQuality (:thumbnail-quality env))
                (toFile thumb-path))
              (alter-thumbnails-cache thumb-key thumb-file))))))))


(def ^:private dimensions-cache (ref {}))

(defn dimension [path dim]
  (dosync
    (if-let [dimensions (@dimensions-cache path)]
      (dim dimensions)
      (let [image-path (join-path media-root path)
            image-file (java.io.File. image-path)
            image (javax.imageio.ImageIO/read image-file)
            dimensions {:width (.getWidth image)
                        :height (.getHeight image)}]
        (alter dimensions-cache assoc path dimensions)
        (dim dimensions)))))
