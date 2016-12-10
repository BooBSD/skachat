(ns skachat.core
  (:require [skachat.handler :refer [app init destroy]]
            [luminus.repl-server :as repl]
            [luminus.http-server :as http]
            [skachat.db.utils :refer [dbdrop ensure-fixtures ensure-migrations]]
            [config.core :refer [env]])
  (:gen-class))

(defn parse-port [port]
  (when port
    (cond
      (string? port) (Integer/parseInt port)
      (number? port) port
      :else          (throw (Exception. (str "invalid port value: " port))))))

(defn http-port [port]
  ;;default production port is set in
  ;;env/prod/resources/config.edn
  (parse-port (or port (env :port))))

(defn stop-app []
  (repl/stop)
  (http/stop destroy)
  (shutdown-agents))

(defn start-app
  "e.g. lein run 3000"
  [[port]]
  (let [port (http-port port)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app))
    (when-let [repl-port (env :nrepl-port)]
      (repl/start {:port (parse-port repl-port)}))
    (http/start {:handler app
                 :init    init
                 :port    port
                 :host    "localhost"})))

(defn -main [& args]
  (cond
    (some #{"dbdrop"} args)
    (do (dbdrop) (System/exit 0))
    (some #{"dbload"} args)
    (do (ensure-fixtures) (System/exit 0))
    :else
    (do (ensure-migrations) (start-app args))))
