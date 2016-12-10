(ns skachat.config
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[skachat started successfully]=-"))
   :middleware identity})
