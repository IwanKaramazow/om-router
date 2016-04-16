(ns om-router.history
  (:require [goog.events :refer [listen]]
            [om.next :as om])
  (:import [goog.history Html5History EventType]))

(def token-transformer
  (let [transformer (js/Object.)]
    (set! (.-retrieveToken transformer)
          (fn [path-prefix location]
            (str (.-pathname location) (.-search location))))

    (set! (.-createUrl transformer)
          (fn [token path-prefix location]
            (str path-prefix token)))
    transformer))

(defn get-token []
  (str js/window.location.pathname js/window.location.search))

(defn handleUrlChange [c event]
  (om/transact! c `[(router/transact
                     {:url ~(get-token) :action :push}) :route]))

(defn make-history [c]
  (doto (Html5History. js/window token-transformer)
    (.setPathPrefix (str js/window.location.protocol "//" js/window.location.host))
    (.setUseFragment false)
    (listen EventType.NAVIGATE
            #(handleUrlChange c %))
    (.setEnabled true)))


(defn push! [history token]
  (.setToken history token))

(defn replace! [history token]
  (.replaceToken history token))
