(ns skachat.config
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [skachat.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[skachat started successfully using the development profile]=-"))
   :middleware wrap-dev})
