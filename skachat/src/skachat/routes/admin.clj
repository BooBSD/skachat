(ns skachat.routes.admin
  (:require [config.core :refer [env]]
            [compojure.core :refer [routes GET POST]]
            [compojure.coercions :refer [as-int]]
            [ring.util.http-response :refer [ok found]]
            [clojure.java.io :as io]
            [datomic.api :only [q db] :as d]
            [selmer.filters :refer [add-filter!]]
            [clojure.edn :as edn]
            [clojure.set :as s]
            [clojure.string :refer [trim]]
            [skachat.db.core :refer [conn]]
            [skachat.layout :as layout])
  (:import [java.io File FileInputStream FileOutputStream]))


(add-filter! :name name)


(def admin {:ns {:app {:attrs-display ["name" "slug" "version" "size" "published"]
                       :attrs-order [:db/id >]
                       :widgets {"teaser" ["textarea"]
                                 "description" ["textarea"]
                                 "logo" ["file" {:upload-to "app"}]
                                 "background" ["file" {:upload-to "app"}]
                                 }}
                 :app.part {:attrs-display ["title" "app" "template"]
                            :attrs-order [:db/id <]
                            :refs {:app.part/app :app/name
                                   :app.part/template {:app.part.template/leftImageDark "Картинка слева, темный фон"
                                                       :app.part.template/rightImageDark "Картинка справа, темный фон"
                                                       :app.part.template/leftImageGrey "Картинка слева, серый фон"
                                                       :app.part.template/rightImageGrey "Картинка справа, серый фон"
                                                       :app.part.template/leftImageLight "Картинка слева, светлый фон"
                                                       :app.part.template/rightImageLight "Картинка справа, светлый фон"}}
                            :widgets {"text" ["textarea"]
                                      "image" ["file" {:upload-to "part"}]}}
                 :app.news {:attrs-display ["title" "app"]
                            :attrs-order [:db/id >]
                            :refs {:app.news/app :app/name}
                            :widgets {"teaser" ["textarea"]
                                      "text" ["textarea"]
                                      "image" ["file" {:upload-to "news"}]}}}
            :empty-value "N/A"})


(defn update-vals [map vals f]
  (reduce #(update-in %1 [%2] f) map vals))


(defn home []
  (if-let [namespaces (d/q '[:find [?ns ...]
                             :in $
                             :where [?e :db/ident ?ident]
                                    [_ :db.install/attribute ?e]
                                    [(namespace ?ident) ?ns]
                                    (not [(.startsWith ?ns "db")])
                                    [(!= ?ns "fressian")]
                                    [(!= ?ns "confirmity")]
                             ]
                        (d/db conn))]
    (layout/render "admin/home.html" {:namespaces namespaces})))


(defn entities [ns]
  (let [db (d/db conn)]
    (if-let [attrs (not-empty (d/q '[:find [?ident ...]
                                     :in $ ?ns
                                     :where [?e :db/ident ?ident]
                                            [_ :db.install/attribute ?e]
                                            [(namespace ?ident) ?namespace]
                                            [(= ?namespace ?ns)]]
                                db ns))]
      (let [app-conf (get-in admin [:ns (keyword ns)])
            refs (:refs app-conf)
            pull-refs (reduce-kv #(if (keyword? %3) (assoc %1 %2 (vector %3)) %1) {} refs)
            entities (d/q (str "[:find [(pull ?e [* " pull-refs "]) ...]
                                 :in $ [?attrs ...]
                                 :where [?e ?attrs]]")
                        db attrs)
            empty-value (:empty-value admin "N/A")
            order (:attrs-order app-conf [:db/id >])
            sorted (sort-by #((first order) %) (second order) entities)
            attrs (or (:attrs-display app-conf) (map #(name %) attrs))
            display (map #(keyword ns %) attrs)
            values (map (fn [m]
                          (cons (:db/id m) (map #(let [aref (% refs)
                                                       value (% m empty-value)]
                                                   (if aref
                                                     (cond (keyword? aref) (get value aref empty-value)
                                                           (map? aref) (->> value :db/id (d/ident db) aref))
                                                     value)) display))) sorted)]
        (layout/render "admin/entities.html" {:ns ns :attrs attrs :values values})))))


(defn entities-retract [ns entities]
  (let [tx-data (map #(->> % read-string (vector :db.fn/retractEntity)) entities)]
    (d/transact conn tx-data)
    (found (str "/admin/" ns))))


(defn get-schema-by-ns [db ns]
  (->> (d/q '[:find [(pull ?ident [:db/id :db/ident :db/doc :db/isComponent
                                  {:db/valueType [:db/ident]}
                                  {:db/cardinality [:db/ident]}]) ...]
              :in $ ?ns
              :where [?e :db/ident ?ident]
                     [_ :db.install/attribute ?e]
                     [(namespace ?ident) ?namespace]
                     [(= ?namespace ?ns)]]
          db ns)
    (map #(update-vals % [:db/valueType :db/cardinality] :db/ident))))


(defn join-path [& paths]
  (java.net.URLDecoder/decode
    (str (java.nio.file.Paths/get "" (into-array paths)))
    "utf-8"))


(def media-root (join-path (System/getProperty "user.dir") (:media-root env)))


(defn upload-file [media-root upload-to {:keys [tempfile size filename]}]
  (try
    (let [path (join-path media-root upload-to)]
      (.mkdirs (File. path))
      (with-open [in (new FileInputStream tempfile)
                  out (new FileOutputStream (join-path path filename))]
        (let [source (.getChannel in)
              dest (.getChannel out)]
          (.transferFrom dest source 0 (.size source))
          (.flush out)
          (join-path upload-to filename))))))


(defn fix-value [valtype widget value]
  (let [value (if (string? value) (trim value) value)]
    (if-not (= value "")
      (cond
        (some (partial = valtype) [:db.type/ref
                                   :db.type/keyword
                                   :db.type/long
                                   :db.type/bigint
                                   :db.type/float
                                   :db.type/double
                                   :db.type/bigdec])
              (try (edn/read-string value) (catch Exception e value))
        (= valtype :db.type/boolean) (boolean value)
        (= valtype :db.type/bytes) (if-not (= 0 (:size value))
                                     (let [f (:tempfile value)
                                           ba (byte-array (.length f))
                                           is (FileInputStream. f)]
                                       (.read is ba)
                                       (.close is)
                                       ba)
                                     nil)
        (and (= valtype :db.type/string)
             (= (first widget) "file"))
          (if-not (= 0 (:size value))
            (let [upload-to (-> widget second :upload-to)]
              (upload-file media-root upload-to value))
            nil)
        :else value)
      nil)))


(defn fix-post-data [data schema widgets]
  (reduce (fn [m s]
            (let [attr (:db/ident s)
                  valtype (:db/valueType s)
                  widget (widgets (name attr))
                  many? (= :db.cardinality/many (:db/cardinality s))
                  raw (-> attr str data)
                  rawvec (if
                           (and many? (not (vector? raw)))
                           (if (= raw "")
                             []
                             (vector raw))
                           raw)
                  value (if many?
                          (->> rawvec (map #(fix-value valtype widget %)) (filter #(not (nil? %))) vec)
                          (fix-value valtype widget rawvec))]
              (if (= value nil) (dissoc m attr) (assoc m attr value))))
    {} schema))


(defn get-cardinality-many-retract [db id schema data]
  (let [attrs (map :db/ident (filter #(= (:db/cardinality %) :db.cardinality/many) schema))]
    (if-let [retract (not-empty (reduce (fn [r a]
                                          (let [old (set (d/q '[:find [?v ...]
                                                                :in $ ?e ?a
                                                                :where [?e ?a ?v]]
                                                            db id a))
                                                new (set (a data []))
                                                diff (s/difference old new)
                                                retract (map (fn [v] [:db/retract id a v]) diff)]
                                            (apply (partial conj r) retract)))
                                  [] attrs))]
      retract)))


(defn schema-to-fields [db schema data refs widgets]
  (->> schema
    (map #(update % :name (fn [_] (-> % :db/ident name))))
    (map #(update % :value (fn [_] (let [value (-> % :db/ident data)
                                         ref? (= :db.type/ref (:db/valueType %))]
                                     (if ref? (:db/id value value) value)))))
    (map #(update % :choices (fn [_] (let [ref-to (-> % :db/ident refs)]
                                       (cond
                                         (map? ref-to) (->> (d/q '[:find ?attrs ?e
                                                                   :in $ [?attrs ...]
                                                                   :where [?e :db/ident ?attrs]]
                                                              db (keys ref-to))
                                                         (into {})
                                                         (s/rename-keys ref-to)
                                                         vec)
                                         (keyword? ref-to) (->> (d/q '[:find ?e ?v
                                                                       :in $ ?attr
                                                                       :where [?e ?attr ?v]]
                                                                  db ref-to)
                                                              (sort-by second)))))))
    (map #(update % :widget (fn [_] (-> % :name widgets))))
    (sort-by :db/id)))


(defn entity-edit [ns id]
  (let [db (d/db conn)
        refs (get-in admin [:ns (keyword ns) :refs] {})
        widgets (get-in admin [:ns (keyword ns) :widgets] {})]
    (if-let [schema (not-empty (get-schema-by-ns db ns))]
      (if-let [data (if id
                      (d/q '[:find (pull ?e [*]) .
                             :in $ ?e
                             :where [?e]]
                        db id)
                       {})]
        (if-let [fields (not-empty (schema-to-fields db schema data refs widgets))]
          (layout/render "admin/entity.html" {:ns ns :id id :fields fields}))))))


(defn entity-edit-post [ns id data]
  (let [db (d/db conn)
        refs (get-in admin [:ns (keyword ns) :refs] {})
        widgets (get-in admin [:ns (keyword ns) :widgets] {})]
    (if-let [schema (not-empty (get-schema-by-ns db ns))]
      (let [eid (if id id (d/tempid :db.part/user))
            add (assoc (fix-post-data data schema widgets) :db/id eid)
            retract (if id (get-cardinality-many-retract db id schema add) [])
            tx (vec (cons add retract))]
        (try
          @(d/transact conn tx)
          (found (str "/admin/" ns))
          (catch Exception e
            (let [fields (schema-to-fields db schema add refs widgets)]
              (layout/render "admin/entity.html" {:ns ns
                                                  :id id
                                                  :fields fields
                                                  :error (.getMessage e)}))))))))


(defn admin-routes []
  (routes
    (GET "/" [] (home))
    (GET "/:ns" [ns] (entities ns))
    (POST "/:ns" [ns entities] (entities-retract ns entities))
    (GET "/:ns/add" [ns] (entity-edit ns nil))
    (POST "/:ns/add" [ns :as {data :multipart-params}] (entity-edit-post ns nil data))
    (GET "/:ns/:id" [ns id :<< as-int] (entity-edit ns id))
    (POST "/:ns/:id" [ns id :<< as-int :as {data :multipart-params}] (entity-edit-post ns id data))))
