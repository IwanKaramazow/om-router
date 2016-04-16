(ns user
  (:require [figwheel-sidecar.repl-api :as figwheel]))

(defn run []
  (figwheel/start-figwheel!))

(def browser-repl figwheel/cljs-repl)
